package com.sanjana.agentic_code_review.model;

import java.util.List;

public interface ReviewAgent {
    ReviewCategory category();
    List<ReviewFinding> review(ReviewContext context);
}