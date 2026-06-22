```text
src/main/java/com/yourname/aireview/
├── AiReviewApplication.java
├── controller/
│   └── PrWebhookController.java        // receives GitHub webhook payload
├── agent/
│   ├── CoordinatorAgent.java           // routes + merges
│   ├── SecurityReviewAgent.java
│   ├── LogicReviewAgent.java
│   └── StyleReviewAgent.java           // week 2
├── model/
│   ├── ReviewFinding.java              // record: file, line, severity, message, agentSource
│   └── PrContext.java                  // diff, file list, PR metadata
├── github/
│   └── GitHubCommentService.java       // posts comments via GitHub REST/MCP
└── config/
    └── SpringAiConfig.java             // ChatClient beans per agent
