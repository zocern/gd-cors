package com.cors.config.milvus;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentRetrieverConfig {

    // 构建向量数据库检索对象
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> milvusEmbeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(milvusEmbeddingStore) // 使用注入的 Milvus 向量存储
                .embeddingModel(embeddingModel)       // 使用注入的向量模型
                .maxResults(20)                       // 指定最多返回 20 个最相关的结果
                .minScore(0.8)                        // 指定相关性分数的最小阈值，过滤掉不相关的结果
                .build();
    }
}