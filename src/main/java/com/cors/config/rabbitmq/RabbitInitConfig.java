package com.cors.config.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitInitConfig {

    // =========================================================================
    // 物理存储删除队列 (原 file-delete) -> 专注 MinIO
    // =========================================================================
    public static final String FILE_STORAGE_DELETE_QUEUE = "file-storage-delete-queue";
    public static final String FILE_STORAGE_DELETE_EXCHANGE = "file-storage-delete-exchange";
    public static final String FILE_STORAGE_DELETE_ROUTING_KEY = "file.storage.delete";

    // 重试
    public static final String FILE_STORAGE_DELETE_RETRY_QUEUE = "file-storage-delete-retry-queue";
    public static final String FILE_STORAGE_DELETE_RETRY_EXCHANGE = "file-storage-delete-retry-exchange";
    public static final String FILE_STORAGE_DELETE_RETRY_ROUTING_KEY = "file.storage.delete.retry";

    // =========================================================================
    // 向量索引删除队列 -> 专注 Milvus
    // =========================================================================
    public static final String FILE_VECTOR_DELETE_QUEUE = "file-vector-delete-queue";
    public static final String FILE_VECTOR_DELETE_EXCHANGE = "file-vector-delete-exchange";
    public static final String FILE_VECTOR_DELETE_ROUTING_KEY = "file.vector.delete";

    // 重试
    public static final String FILE_VECTOR_DELETE_RETRY_QUEUE = "file-vector-delete-retry-queue";
    public static final String FILE_VECTOR_DELETE_RETRY_EXCHANGE = "file-vector-delete-retry-exchange";
    public static final String FILE_VECTOR_DELETE_RETRY_ROUTING_KEY = "file.vector.delete.retry";

    // =========================
    // 文件向量化队列 (Vectorization)
    // =========================
    public static final String FILE_VECTOR_QUEUE = "file-vector-queue";
    public static final String FILE_VECTOR_EXCHANGE = "file-vector-exchange";
    public static final String FILE_VECTOR_ROUTING_KEY = "file.vector";

    // =========================
    // 向量化重试队列 (Retry/Dead Letter)
    // =========================
    public static final String FILE_VECTOR_RETRY_QUEUE = "file-vector-retry-queue";
    public static final String FILE_VECTOR_RETRY_EXCHANGE = "file-vector-retry-exchange";
    public static final String FILE_VECTOR_RETRY_ROUTING_KEY = "file.vector.retry";


    @Bean("fileStorageDeleteQueue")
    public Queue fileStorageDeleteQueue() {
        return QueueBuilder.durable(FILE_STORAGE_DELETE_QUEUE)
                .lazy()  // 如果文件量大，延迟队列可以减少内存压力
                .build();
    }

    @Bean("fileStorageDeleteExchange")
    public DirectExchange fileStorageDeleteExchange() {
        return new DirectExchange(FILE_STORAGE_DELETE_EXCHANGE);
    }

    @Bean("fileStorageDeleteBinding")
    public Binding fileStorageDeleteBinding(
            @Qualifier("fileStorageDeleteQueue") Queue queue,
            @Qualifier("fileStorageDeleteExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FILE_STORAGE_DELETE_ROUTING_KEY);
    }

    @Bean("fileStorageDeleteRetryQueue")
    public Queue fileStorageDeleteRetryQueue() {
        return QueueBuilder.durable(FILE_STORAGE_DELETE_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_STORAGE_DELETE_EXCHANGE) // TTL 到期后发送到主队列
                .withArgument("x-dead-letter-routing-key", FILE_STORAGE_DELETE_ROUTING_KEY)
                .withArgument("x-message-ttl", 3000) // 重试间隔 3 秒
                .build();
    }

    @Bean("fileStorageDeleteRetryExchange")
    public DirectExchange fileStorageDeleteRetryExchange() {
        return new DirectExchange(FILE_STORAGE_DELETE_RETRY_EXCHANGE);
    }

    @Bean("fileStorageDeleteRetryBinding")
    public Binding fileStorageDeleteRetryBinding(
            @Qualifier("fileStorageDeleteRetryQueue") Queue queue,
            @Qualifier("fileStorageDeleteRetryExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FILE_STORAGE_DELETE_RETRY_ROUTING_KEY);
    }

    @Bean("fileVectorDeleteQueue")
    public Queue fileVectorDeleteQueue() {
        return QueueBuilder.durable(FILE_VECTOR_DELETE_QUEUE).lazy().build();
    }

    @Bean("fileVectorDeleteExchange")
    public DirectExchange fileVectorDeleteExchange() {
        return new DirectExchange(FILE_VECTOR_DELETE_EXCHANGE);
    }

    @Bean("fileVectorDeleteBinding")
    public Binding fileVectorDeleteBinding(
            @Qualifier("fileVectorDeleteQueue") Queue queue,
            @Qualifier("fileVectorDeleteExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FILE_VECTOR_DELETE_ROUTING_KEY);
    }


    @Bean("fileVectorDeleteRetryQueue")
    public Queue fileVectorDeleteRetryQueue() {
        return QueueBuilder.durable(FILE_VECTOR_DELETE_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_VECTOR_DELETE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FILE_VECTOR_DELETE_ROUTING_KEY)
                .withArgument("x-message-ttl", 3000)
                .build();
    }
    @Bean("fileVectorDeleteRetryExchange")
    public DirectExchange fileVectorDeleteRetryExchange() {
        return new DirectExchange(FILE_VECTOR_DELETE_RETRY_EXCHANGE);
    }

    @Bean("fileVectorDeleteRetryBinding")
    public Binding fileVectorDeleteRetryBinding(
            @Qualifier("fileVectorDeleteRetryQueue") Queue queue,
            @Qualifier("fileVectorDeleteRetryExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FILE_VECTOR_DELETE_RETRY_ROUTING_KEY);
    }


    @Bean("fileVectorQueue")
    public Queue fileVectorQueue() {
        return QueueBuilder.durable(FILE_VECTOR_QUEUE)
                .lazy() // 向量化比较慢，堆积时减少内存压力
                .build();
    }

    @Bean("fileVectorExchange")
    public DirectExchange fileVectorExchange() {
        return new DirectExchange(FILE_VECTOR_EXCHANGE);
    }

    @Bean("fileVectorBinding")
    public Binding fileVectorBinding(
            @Qualifier("fileVectorQueue") Queue queue,
            @Qualifier("fileVectorExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FILE_VECTOR_ROUTING_KEY);
    }

    @Bean("fileVectorRetryQueue")
    public Queue fileVectorRetryQueue() {
        return QueueBuilder.durable(FILE_VECTOR_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_VECTOR_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FILE_VECTOR_ROUTING_KEY)
                .withArgument("x-message-ttl", 15000) // 重试间隔 15 秒
                .build();
    }

    @Bean("fileVectorRetryExchange")
    public DirectExchange fileVectorRetryExchange() {
        return new DirectExchange(FILE_VECTOR_RETRY_EXCHANGE);
    }

    @Bean("fileVectorRetryBinding")
    public Binding fileVectorRetryBinding(
            @Qualifier("fileVectorRetryQueue") Queue queue,
            @Qualifier("fileVectorRetryExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FILE_VECTOR_RETRY_ROUTING_KEY);
    }
}
