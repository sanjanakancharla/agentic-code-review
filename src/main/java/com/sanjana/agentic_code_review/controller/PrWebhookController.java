package com.sanjana.agentic_code_review.controller;


import com.sanjana.agentic_code_review.dto.PullRequestWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PrWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PrWebhookController.class);

    // Simple in-memory idempotency guard for week 1.
    // Swap for a Redis SETNX or a unique DB constraint once this needs to
    // survive a restart / run across multiple instances.
    private final Set<String> processedDeliveryIds = ConcurrentHashMap.newKeySet();

    // private final CoordinatorAgent coordinatorAgent; // wire this in next

    @PostMapping("/webhooks/github")
    public ResponseEntity<String> handlePullRequestEvent(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody PullRequestWebhookPayload payload) {

        // GitHub sends a "ping" event when the webhook is first created — just ack it
        if (!"pull_request".equals(eventType)) {
            log.info("Ignoring non-PR event type: {}", eventType);
            return ResponseEntity.ok("ignored: " + eventType);
        }

        // Idempotency: GitHub retries deliveries on timeout / 5xx.
        // Without this guard you'd review the same PR diff multiple times.
        if (!processedDeliveryIds.add(deliveryId)) {
            log.info("Duplicate delivery {} — already processed, returning 200", deliveryId);
            return ResponseEntity.ok("duplicate, already processed");
        }

        if (!payload.isReviewableAction()) {
            log.info("Ignoring action '{}' on PR #{}", payload.action(), payload.number());
            return ResponseEntity.ok("ignored action: " + payload.action());
        }

        log.info("PR #{} '{}' on {} — action={}, triggering review",
                payload.number(),
                payload.pullRequest().title(),
                payload.repository().fullName(),
                payload.action());

        // CRITICAL: respond within GitHub's 10s window, do the real work async.
        // For week 1, @Async on the coordinator call is enough. Once you need
        // this to survive restarts/scale across instances, swap to a real
        // queue (RabbitMQ/SQS) per the production pattern.
        // coordinatorAgent.reviewPrAsync(payload);

        return ResponseEntity.ok("review triggered for PR #" + payload.number());
    }
}