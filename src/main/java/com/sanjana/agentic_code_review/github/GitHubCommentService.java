package com.sanjana.agentic_code_review.agentSource.github;


import com.sanjana.aireview.model.ReviewFinding;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewBuilder;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Posts agent-generated review findings back to a GitHub PR.
 *
 * Strategy: try to post each finding as an inline review comment on its
 * file/line via a single GH "review" (one network call, comments grouped
 * together — this is what a human reviewer's "Finish review" button does).
 * If that fails for any reason (e.g. a line isn't part of the diff so
 * GitHub rejects the position), fall back to one summary comment so the
 * PR author still sees the findings instead of silently losing them.
 */
@Service
public class GitHubCommentService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommentService.class);

    private final GitHub gitHub;

    @Value("${github.bot.review-event:COMMENT}")
    private String reviewEventName; // COMMENT (default), REQUEST_CHANGES, or APPROVE

    public GitHubCommentService(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    /**
     * Posts all findings to the given PR. Groups findings into a single
     * GitHub "review" submission rather than N separate comment API calls —
     * fewer requests, and they show up together in GitHub's UI like a real
     * reviewer's pass.
     */
    public void postFindings(String repoFullName, int prNumber, List<ReviewFinding> findings) {
        if (findings.isEmpty()) {
            log.info("No findings for PR #{} — nothing to post", prNumber);
            return;
        }

        try {
            GHRepository repo = gitHub.getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);

            postAsInlineReview(pr, findings);

        } catch (IOException e) {
            log.error("Failed to post review for PR #{} on {} — falling back to summary comment",
                    prNumber, repoFullName, e);
            postSummaryFallback(repoFullName, prNumber, findings);
        }
    }

    private void postAsInlineReview(GHPullRequest pr, List<ReviewFinding> findings) throws IOException {
        GHPullRequestReviewBuilder reviewBuilder = pr.createReview()
                .body(buildReviewSummary(findings))
                .event(GHPullRequestReviewEvent.valueOf(reviewEventName));

        for (ReviewFinding finding : findings) {
            reviewBuilder.comment(formatFindingBody(finding), finding.file(), finding.line());
        }

        reviewBuilder.create();
        log.info("Posted {} inline finding(s) to PR #{}", findings.size(), pr.getNumber());
    }

    /**
     * Fallback when inline positioning fails — e.g. a finding's line number
     * doesn't map cleanly onto the diff (common when the LLM reports a line
     * from the full file rather than the diff hunk). A single readable
     * comment is far better than a swallowed exception and a silent PR.
     */
    private void postSummaryFallback(String repoFullName, int prNumber, List<ReviewFinding> findings) {
        try {
            GHRepository repo = gitHub.getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);
            pr.comment(buildReviewSummary(findings) + "\n\n" + buildFlatFindingsList(findings));
            log.info("Posted fallback summary comment to PR #{}", prNumber);
        } catch (IOException e) {
            log.error("Fallback summary comment also failed for PR #{} on {}", prNumber, repoFullName, e);
        }
    }

    private String buildReviewSummary(List<ReviewFinding> findings) {
        Map<ReviewFinding.Severity, Long> counts = findings.stream()
                .collect(Collectors.groupingBy(ReviewFinding::severity, Collectors.counting()));

        return """
                ## 🤖 AI Code Review

                Found **%d** issue(s): %d critical, %d warning(s), %d suggestion(s).
                """.formatted(
                findings.size(),
                counts.getOrDefault(ReviewFinding.Severity.CRITICAL, 0L),
                counts.getOrDefault(ReviewFinding.Severity.WARNING, 0L),
                counts.getOrDefault(ReviewFinding.Severity.SUGGESTION, 0L)
        );
    }

    private String formatFindingBody(ReviewFinding finding) {
        String icon = switch (finding.severity()) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟡";
            case SUGGESTION -> "🔵";
        };
        return "%s **[%s]** %s".formatted(icon, finding.agentSource(), finding.message());
    }

    private String buildFlatFindingsList(List<ReviewFinding> findings) {
        return findings.stream()
                .sorted(Comparator.comparing(ReviewFinding::severity))
                .map(f -> "- `%s:%d` — %s".formatted(f.file(), f.line(), formatFindingBody(f)))
                .collect(Collectors.joining("\n"));
    }
}