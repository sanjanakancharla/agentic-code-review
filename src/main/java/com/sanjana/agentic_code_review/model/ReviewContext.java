package com.sanjana.agentic_code_review.model;

public record ReviewContext(
        String filePath,
        String diff,
        String retrievedContext   // nullable — RAG context, wired in later
) {}