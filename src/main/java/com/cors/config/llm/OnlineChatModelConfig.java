package com.cors.config.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.online-chat-model")
public class OnlineChatModelConfig {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private boolean logRequests;
    private boolean logResponses;
    private Duration timeout;
}
