package com.sanjana.agentic_code_review.agent;

import com.sanjana.agentic_code_review.github.GitHubCommentService;
import com.sanjana.agentic_code_review.model.PrContext;
import com.sanjana.agentic_code_review.model.ReviewContext;
import com.sanjana.agentic_code_review.model.ReviewFinding;
import com.sanjana.agentic_code_review.model.ReviewResult;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class CoordinatorAgent {

    private final SecurityReviewAgent securityAgent;
    private final LogicReviewAgent logicAgent;
    private final GitHubCommentService githubService;

    public CoordinatorAgent(SecurityReviewAgent securityAgent,
                            LogicReviewAgent logicAgent,
                            GitHubCommentService githubService) {
        this.securityAgent = securityAgent;
        this.logicAgent = logicAgent;
        this.githubService = githubService;
    }

    public ReviewResult reviewPr(PrContext context) {
        // Bridge: build the per-file context the specialist agents consume.
        ReviewContext reviewContext =
                new ReviewContext(context.filePath(), context.diff(), null);

        // Run specialists concurrently.
        CompletableFuture<List<ReviewFinding>> securityFuture =
                CompletableFuture.supplyAsync(() -> securityAgent.review(reviewContext));
        CompletableFuture<List<ReviewFinding>> logicFuture =
                CompletableFuture.supplyAsync(() -> logicAgent.review(reviewContext));

        // Wait for both, merge, worst severity first.
        List<ReviewFinding> allFindings = Stream.concat(
                        securityFuture.join().stream(),
                        logicFuture.join().stream())
                .sorted(Comparator.comparing(ReviewFinding::severity, Comparator.reverseOrder()))
                .toList();

        githubService.postFindings(context.prNumber(), allFindings);
        return new ReviewResult(allFindings);
    }
}