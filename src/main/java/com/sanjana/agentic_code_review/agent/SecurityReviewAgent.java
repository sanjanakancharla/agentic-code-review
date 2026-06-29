package com.sanjana.agentic_code_review.agent;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.sanjana.agentic_code_review.model.ReviewAgent;
import com.sanjana.agentic_code_review.model.ReviewCategory;
import com.sanjana.agentic_code_review.model.ReviewContext;
import com.sanjana.agentic_code_review.model.ReviewFinding;
import com.sanjana.agentic_code_review.model.Severity;

/**
 * Specialist agent that reviews a single changed file for security issues only.
 * Runs synchronously; the CoordinatorAgent is responsible for running the
 * specialist agents concurrently (e.g. CompletableFuture.supplyAsync).
 */
@Component
public class SecurityReviewAgent implements ReviewAgent {

    private static final String SYSTEM_PROMPT = """
        You are a senior application security engineer reviewing a single file
        changed in a GitHub pull request.

        Report ONLY genuine security vulnerabilities or weaknesses. Do NOT comment
        on style, formatting, naming, performance, or general correctness — other
        agents handle those.

        Look specifically for:
        - Injection (SQL, command, LDAP, template) and queries built by string concatenation
        - Hardcoded secrets, credentials, API tokens, or keys
        - Broken authentication or authorization / missing access checks
        - Insecure deserialization and unsafe reflection
        - Path traversal, SSRF, and unvalidated redirects
        - Weak or misused cryptography (weak algorithms, static IVs, ECB, predictable randomness)
        - XXE and unsafe XML/YAML parsing
        - Sensitive data exposure (logging secrets/PII, leaking stack traces to clients)
        - Missing or improper validation of untrusted input

        Rules:
        - Only report issues you can justify directly from the code shown. Do not
          speculate or invent findings to fill space.
        - If the file has no security issues, return an empty findings list.
        - Use the line number where the issue occurs, as numbered in the code provided.
        - Assign severity using this rubric:
            CRITICAL - remote exploit or data breach with little precondition
            HIGH     - clear vulnerability with a realistic exploit path
            MEDIUM   - weakness exploitable under specific conditions
            LOW      - hardening / defense-in-depth improvement
            INFO     - informational note
        - Each description must be concrete and specific to this code.
        - Each suggestion must be an actionable fix, not generic advice.
        """;

    private static final String USER_TEMPLATE = """
        File: {path}

        Code under review (changed lines / unified diff):
        {diff}
        {context}
        """;

    private final ChatClient chatClient;

    public SecurityReviewAgent(ChatClient.Builder builder) {
        // Options pinned here so this agent stays deterministic regardless of
        // global defaults. Move to application.yml if you prefer central config.
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .temperature(0.2)
                        .build())
                .build();
    }

    @Override
    public ReviewCategory category() {
        return ReviewCategory.SECURITY;
    }

    @Override
    public List<ReviewFinding> review(ReviewContext context) {
        String ragBlock = (context.retrievedContext() == null || context.retrievedContext().isBlank())
                ? ""
                : "\nRelated project context (for reference only, do not review):\n" + context.retrievedContext();

        SecurityFindings result = chatClient.prompt()
                .user(u -> u.text(USER_TEMPLATE)
                        .param("path", context.filePath())
                        .param("diff", context.diff())
                        .param("context", ragBlock))
                .call()
                .entity(SecurityFindings.class);

        if (result == null || result.findings() == null) {
            return List.of();
        }

        // Stamp category and file path on our side so they're authoritative,
        // rather than trusting the model to echo them back correctly.
        return result.findings().stream()
                .map(raw -> new ReviewFinding(
                        context.filePath(),
                        raw.line(),
                        parseSeverity(raw.severity()),
                        ReviewCategory.SECURITY,
                        raw.title(),
                        raw.description(),
                        raw.suggestion()))
                .toList();
    }

    private static Severity parseSeverity(String value) {
        if (value == null) {
            return Severity.MEDIUM;
        }
        try {
            return Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Severity.MEDIUM; // model returned something off-rubric
        }
    }

    /** Container the model fills; wrapping the list is more robust than a top-level array. */
    private record SecurityFindings(List<RawFinding> findings) {}

    /** Raw shape from the LLM — mapped to ReviewFinding above. */
    private record RawFinding(
            int line,
            String severity,
            String title,
            String description,
            String suggestion) {}
}