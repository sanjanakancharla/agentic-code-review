package com.sanjana.agentic_code_review.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the X-Hub-Signature-256 header GitHub sends with every webhook
 * delivery, BEFORE the request reaches the controller.
 *
 * Why a filter and not logic inside the controller: by the time a
 * @RequestBody record is bound, the raw bytes are gone. HMAC verification
 * needs the exact raw payload — re-serializing a parsed object will not
 * reliably reproduce byte-for-byte the original payload GitHub signed.
 */
@Component
public class GitHubWebhookSignatureFilter extends OncePerRequestFilter {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only guard the webhook endpoint — don't slow down everything else
        return !request.getRequestURI().equals("/webhooks/github");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Wrap so we can read the body now AND let the controller read it again later
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        // Force the body to be read/cached before we inspect it
        wrappedRequest.getInputStream().readAllBytes();
        byte[] rawBody = wrappedRequest.getContentAsByteArray();

        String signatureHeader = request.getHeader(SIGNATURE_HEADER);

        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or malformed signature");
            return;
        }

        String providedSignature = signatureHeader.substring(SIGNATURE_PREFIX.length());
        String expectedSignature = computeHmacSha256(rawBody, webhookSecret);

        if (!constantTimeEquals(providedSignature, expectedSignature)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Signature mismatch");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private String computeHmacSha256(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    /**
     * Timing-safe comparison — prevents an attacker from inferring the
     * correct signature byte-by-byte via response timing differences.
     */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}