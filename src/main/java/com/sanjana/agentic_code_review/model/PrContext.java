package com.sanjana.agentic_code_review.model;

public record PrContext(
        int prNumber,
        String filePath,
        String diff
) {}