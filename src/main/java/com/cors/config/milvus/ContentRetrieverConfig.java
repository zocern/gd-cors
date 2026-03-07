package com.cors.config.milvus;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
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
                .maxResults(50)                       // 指定最多返回 50 个最相关的结果
                .minScore(0.65)                       // 粗排 指定相关性分数的最小阈值，过滤掉不相关的结果
                .build();
    }

    // 精排
    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever milvusRetriever, ScoringModel scoringModel) {
        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.8)   // 精排阈值
                .maxResults(5)   // 最终只给 LLM 5 条数据
                .build();

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(milvusRetriever)
                .contentAggregator(contentAggregator)
                .build();
    }
}