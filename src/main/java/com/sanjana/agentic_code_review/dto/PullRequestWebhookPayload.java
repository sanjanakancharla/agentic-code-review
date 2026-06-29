package com.sanjana.agentic_code_review.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal slice of GitHub's pull_request webhook payload.
 * Add fields as you need them — don't model the whole payload up front.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestWebhookPayload(
        String action,                 // "opened", "synchronize", "reopened", etc.
        Integer number,                // PR number
        @JsonProperty("pull_request") PullRequestInfo pullRequest,
        RepositoryInfo repository
) {

    /**
     * We only act on these — ignore "closed", "labeled", "assigned", etc.
     */
    public boolean isReviewableAction() {
        return "opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestInfo(
            String title,
            @JsonProperty("diff_url") String diffUrl,
            @JsonProperty("html_url") String htmlUrl,
            BranchRef head,
            BranchRef base,
            UserInfo user
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchRef(String ref, String sha) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryInfo(
            @JsonProperty("full_name") String fullName
    ) {}
}