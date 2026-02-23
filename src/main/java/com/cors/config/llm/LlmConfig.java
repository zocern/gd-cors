package com.cors.config.llm;

import com.cors.config.milvus.parser.ApacheTikaDocumentParser;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class LlmConfig {

    private final OnlineChatModelConfig onlineChatModelConfig;
    private final QwenChatModelConfig qwenChatModelConfig;
    private final QwenVlModelConfig qwenVlModelConfig;
    private final QwenEmbeddingModelConfig qwenEmbeddingModelConfig;


    @Bean("onlineChatModel")
    public OpenAiStreamingChatModel onlineChatModel() {
        Map<String, String> map = new HashMap<>();
        map.put("Content-Type", "application/json;charset=utf-8");
        return OpenAiStreamingChatModel.builder()
                .baseUrl(onlineChatModelConfig.getBaseUrl())
                .apiKey(onlineChatModelConfig.getApiKey())
                .modelName(onlineChatModelConfig.getModelName())
                .logRequests(onlineChatModelConfig.isLogRequests())
                .logResponses(onlineChatModelConfig.isLogResponses())
                .timeout(onlineChatModelConfig.getTimeout())
                .httpClientBuilder(SpringRestClient.builder())
                .customHeaders(map)
                .build();
    }

    @Bean("qwenChatModel")
    public OllamaStreamingChatModel qwenChatModel() {
        Map<String, String> map = new HashMap<>();
        map.put("Content-Type", "application/json;charset=utf-8");
        return OllamaStreamingChatModel.builder()
                .baseUrl(qwenChatModelConfig.getBaseUrl())
                .modelName(qwenChatModelConfig.getModelName())
                .logRequests(qwenChatModelConfig.isLogRequests())
                .logResponses(qwenChatModelConfig.isLogResponses())
                .timeout(qwenChatModelConfig.getTimeout())
                .httpClientBuilder(SpringRestClient.builder())
                .customHeaders(map)
                .build();
    }

    @Bean("qwenVlModel")
    public OllamaChatModel qwenVlModel() {
        Map<String, String> map = new HashMap<>();
        map.put("Content-Type", "application/json;charset=utf-8");
        return OllamaChatModel.builder()
                .baseUrl(qwenVlModelConfig.getBaseUrl())
                .modelName(qwenVlModelConfig.getModelName())
                .temperature(qwenVlModelConfig.getTemperature())
                .logRequests(qwenVlModelConfig.isLogRequests())
                .logResponses(qwenVlModelConfig.isLogResponses())
                .timeout(qwenVlModelConfig.getTimeout())
                .httpClientBuilder(SpringRestClient.builder()
                        .readTimeout(qwenVlModelConfig.getTimeout()))
                .customHeaders(map)
                .defaultRequestParameters(
                        OllamaChatRequestParameters.builder()
                                .modelName(qwenVlModelConfig.getModelName())
                                .temperature(qwenVlModelConfig.getTemperature())
                                .build()
                )
                .build();

    }

    @Bean("qwenEmbeddingModel")
    public OllamaEmbeddingModel qwenEmbeddingModel() {
        Map<String, String> map = new HashMap<>();
        map.put("Content-Type", "application/json;charset=utf-8");
        return OllamaEmbeddingModel.builder()
                .baseUrl(qwenEmbeddingModelConfig.getBaseUrl())
                .modelName(qwenEmbeddingModelConfig.getModelName())
                .logRequests(qwenEmbeddingModelConfig.isLogRequests())
                .logResponses(qwenEmbeddingModelConfig.isLogResponses())
                .timeout(qwenEmbeddingModelConfig.getTimeout())
                .httpClientBuilder(SpringRestClient.builder())
                .customHeaders(map)
                .build();
     }

    @Bean
    public ApacheTikaDocumentParser apacheTikaDocumentParser(@Qualifier("qwenVlModel") OllamaChatModel ollamaChatModel) {
        return new ApacheTikaDocumentParser(ollamaChatModel, true);
    }
}