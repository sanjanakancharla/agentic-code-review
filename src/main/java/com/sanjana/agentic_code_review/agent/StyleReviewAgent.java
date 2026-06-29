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
 * Specialist agent that reviews a single changed file for style, readability,
 * and convention issues only. Runs synchronously; the CoordinatorAgent fans out
 * the specialist agents concurrently.
 */
@Component
public class StyleReviewAgent implements ReviewAgent {

    private static final String SYSTEM_PROMPT = """
        You are a senior engineer reviewing a single file changed in a GitHub pull
        request, focused ONLY on code style, readability, and convention.

        Report issues such as:
        - Naming that is unclear, inconsistent, or misleading
        - Dead code, unused variables/imports, commented-out blocks
        - Overly long methods or deeply nested logic that hurts readability
        - Inconsistent formatting or violations of common Java conventions
        - Magic numbers / strings that should be named constants
        - Missing or misleading documentation on public APIs
        - Duplicated logic or responsibilities placed in the wrong unit

        Do NOT report security vulnerabilities or functional/logic bugs — other
        agents own those.

        Rules:
        - Only report issues justified directly by the code shown. Do not invent
          nits to fill space.
        - Style issues are usually LOW or INFO. Reserve MEDIUM for readability
          problems serious enough to cause real maintenance pain.
        - If the file is clean, return an empty findings list.
        - Use the line number where the issue occurs, as numbered in the code provided.
        - Each suggestion must be a concrete, actionable improvement.
        """;

    private static final String USER_TEMPLATE = """
        File: {path}

        Code under review (changed lines / unified diff):
        {diff}
        {context}
        """;

    private final ChatClient chatClient;

    public StyleReviewAgent(ChatClient.Builder builder) {
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
        return ReviewCategory.STYLE;
    }

    @Override
    public List<ReviewFinding> review(ReviewContext context) {
        String ragBlock = (context.retrievedContext() == null || context.retrievedContext().isBlank())
                ? ""
                : "\nRelated project context (for reference only, do not review):\n" + context.retrievedContext();

        StyleFindings result = chatClient.prompt()
                .user(u -> u.text(USER_TEMPLATE)
                        .param("path", context.filePath())
                        .param("diff", context.diff())
                        .param("context", ragBlock))
                .call()
                .entity(StyleFindings.class);

        if (result == null || result.findings() == null) {
            return List.of();
        }

        return result.findings().stream()
                .map(raw -> new ReviewFinding(
                        context.filePath(),
                        raw.line(),
                        parseSeverity(raw.severity()),
                        ReviewCategory.STYLE,
                        raw.title(),
                        raw.description(),
                        raw.suggestion()))
                .toList();
    }

    private static Severity parseSeverity(String value) {
        if (value == null) {
            return Severity.LOW;
        }
        try {
            return Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Severity.LOW; // style nits default low
        }
    }

    private record StyleFindings(List<RawFinding> findings) {}

    private record RawFinding(
            int line,
            String severity,
            String title,
            String description,
            String suggestion) {}
}