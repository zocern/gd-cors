package com.cors.config.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.ollama.qwen-chat-model")
public class QwenChatModelConfig {
    private String baseUrl;
    private String modelName;
    private boolean logRequests;
    private boolean logResponses;
    private Duration timeout;
}
