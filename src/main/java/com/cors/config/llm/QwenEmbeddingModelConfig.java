package com.cors.config.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.ollama.qwen-embedding-model")
public class QwenEmbeddingModelConfig {
    private String baseUrl;
    private String modelName;
    private boolean logRequests;
    private boolean logResponses;
//    private int maxSegmentsPerBatch;
//    private int dimensions;
    private Duration timeout;
    private int maxRetries;
}