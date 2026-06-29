package com.sanjana.agentic_code_review.github;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sanjana.agentic_code_review.model.ReviewFinding;

/**
 * Posts review findings back to the GitHub PR.
 *
 * v1 posts ONE consolidated review whose body lists every finding. This works
 * reliably without diff-position math. True per-line inline comments use
 * GHPullRequestReviewBuilder.comment(body, path, position), where `position` is
 * the line index within the unified diff hunk (NOT the file line number) — that
 * upgrade needs a file-line -> diff-position mapper, added later.
 */
@Service
public class GitHubCommentService {

    private final GitHub gitHub;
    private final String repoFullName; // e.g. "sanjana/some-repo"

    public GitHubCommentService(GitHub gitHub,
                                @Value("${github.repo}") String repoFullName) {
        this.gitHub = gitHub;
        this.repoFullName = repoFullName;
    }

    public void postFindings(int prNumber, List<ReviewFinding> findings) {
        try {
            GHRepository repo = gitHub.getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);

            String body = findings.isEmpty()
                    ? "### Agentic Code Review\n\nNo issues found in the changed files."
                    : buildSummary(findings);

            // COMMENT (not APPROVE/REQUEST_CHANGES) keeps the bot neutral and
            // avoids GitHub rejecting a self-approval on the bot's own token.
            pr.createReview()
                    .body(body)
                    .event(GHPullRequestReviewEvent.COMMENT)
                    .create();

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to post review to PR #" + prNumber, e);
        }
    }

    private String buildSummary(List<ReviewFinding> findings) {
        String items = findings.stream()
                .map(this::formatFinding)
                .collect(Collectors.joining("\n\n"));
        return "### Agentic Code Review\n\nFound " + findings.size()
                + " item(s):\n\n" + items;
    }

    private String formatFinding(ReviewFinding f) {
        return """
            - **[%s · %s]** `%s:%d` — %s
              _Fix:_ %s"""
                .formatted(
                        f.severity(),
                        f.category(),
                        f.filePath(),
                        f.line(),
                        f.description(),
                        f.suggestion());
    }
}