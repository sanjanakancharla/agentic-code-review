package com.sanjana.agentic_code_review.agent;

import com.sanjana.agentic_code_review.model.ReviewContext;
import com.sanjana.agentic_code_review.model.ReviewFinding;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LogicReviewAgent {
    private final ChatClient chatClient;

    public LogicReviewAgent(ChatClient.Builder builder){
        this.chatClient = builder
                .defaultSystem("""
                        You are a security-focused code reviewer. Examine the diff for:
                                        hardcoded secrets, SQL injection risk, unsafe deserialization,
                                        missing input validation, and auth bypass risks.
                                        Only flag genuine issues — do not invent findings.
                                """).build();
    }
    public List<ReviewFinding> review(ReviewContext context){
        return chatClient.prompt()
                .user(u -> u.text("Review this diff:\n{diff}")
                        .param("diff", context.diff()))
                .call()
                .entity(new ParameterizedTypeReference<List<ReviewFinding>>() {});
    }
}
