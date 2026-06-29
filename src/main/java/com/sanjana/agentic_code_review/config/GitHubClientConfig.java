package com.sanjana.agentic_code_review.config;


import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GitHubClientConfig {

    @Value("${github.bot.token}")
    private String githubToken;

    @Bean
    public GitHub gitHub() throws IOException {
        return new GitHubBuilder()
                .withOAuthToken(githubToken)
                .build();
    }
}
