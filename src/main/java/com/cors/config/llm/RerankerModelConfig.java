package com.cors.config.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.huggingface.reranker")
public class RerankerModelConfig {
    private String baseUrl;
}
