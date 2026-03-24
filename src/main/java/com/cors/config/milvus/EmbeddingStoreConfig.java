package com.cors.config.milvus;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
@RequiredArgsConstructor
public class EmbeddingStoreConfig {

    @Value("${milvus.dimension}")
    private Integer dimension;

    /**
     * 创建并配置 MilvusEmbeddingStore Bean。
     * 这个 Bean 将作为向量数据的持久化存储。
     * * @return 配置好的 MilvusEmbeddingStore 实例。
     */
    // 构建向量数据库操作对象
    @Bean
    @DependsOn("milvusClient")
    public EmbeddingStore<TextSegment> milvusEmbeddingStore(
            @Value("${milvus.host}") String host,
            @Value("${milvus.port}") Integer port,
            @Value("${milvus.collection}") String collectionName,
            @Value("${milvus.database}") String databaseName
    ) {
        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .metricType(MetricType.IP) // 已归一化，COSINE=IP
                // 维度必须与的 EmbeddingModel 生成的向量维度一致
                .dimension(dimension)
                // 在执行向量相似度搜索（search）时，同时把向量本身也从 Milvus 返回
                .retrieveEmbeddingsOnSearch(false)
                // 每次 insert 自动 flush，如果为 false，则手动调用 client.flush()
                .autoFlushOnInsert(true)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();
    }
}
