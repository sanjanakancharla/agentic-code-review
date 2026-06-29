package com.sanjana.agentic_code_review.model;

public record ReviewFinding(
        String filePath,
        int line,
        Severity severity,
        ReviewCategory category,
        String title,
        String description,
        String suggestion
) {}