package com.sanjana.agentic_code_review.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Default executor is fine for week 1. Once you have 3-4 specialist
    // agents running concurrently per PR, define a dedicated
    // ThreadPoolTaskExecutor bean here so agent work doesn't compete
    // with web request threads.
}