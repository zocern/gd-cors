package com.cors.config.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitInitConfig {

    public static final String FILE_MAIN_EXCHANGE = "file-main-exchange";
    public static final String FILE_RETRY_EXCHANGE = "file-retry-exchange";

    // 队列名 & routing key
    public static final String FILE_STORAGE_DELETE_QUEUE = "file-storage-delete-queue";
    public static final String FILE_STORAGE_DELETE_ROUTING_KEY = "file.storage.delete";

    public static final String FILE_VECTOR_DELETE_QUEUE = "file-vector-delete-queue";
    public static final String FILE_VECTOR_DELETE_ROUTING_KEY = "file.vector.delete";

    public static final String FILE_VECTOR_QUEUE = "file-vector-queue";
    public static final String FILE_VECTOR_ROUTING_KEY = "file.vector";

    public static final String FILE_STORAGE_DELETE_RETRY_QUEUE = "file-storage-delete-retry-queue";
    public static final String FILE_STORAGE_DELETE_RETRY_ROUTING_KEY = "file.storage.delete.retry";

    public static final String FILE_VECTOR_DELETE_RETRY_QUEUE = "file-vector-delete-retry-queue";
    public static final String FILE_VECTOR_DELETE_RETRY_ROUTING_KEY = "file.vector.delete.retry";

    public static final String FILE_VECTOR_RETRY_QUEUE = "file-vector-retry-queue";
    public static final String FILE_VECTOR_RETRY_ROUTING_KEY = "file.vector.retry";

    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(FILE_MAIN_EXCHANGE);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(FILE_RETRY_EXCHANGE);
    }

    // ==========================================
    // 主队列配置
    // ==========================================
    @Bean
    public Queue fileStorageDeleteQueue() {
        return QueueBuilder.durable(FILE_STORAGE_DELETE_QUEUE)
                .lazy()
                .build();
    }

    @Bean
    public Queue fileVectorDeleteQueue() {
        return QueueBuilder.durable(FILE_VECTOR_DELETE_QUEUE)
                .lazy()
                .build();
    }

    @Bean
    public Queue fileVectorQueue() {
        return QueueBuilder.durable(FILE_VECTOR_QUEUE)
                .lazy()
                .build();
    }

    // ==========================================
    // 重试队列配置
    // ==========================================
    @Bean
    public Queue fileStorageDeleteRetryQueue() {
        return QueueBuilder.durable(FILE_STORAGE_DELETE_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_MAIN_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FILE_STORAGE_DELETE_ROUTING_KEY)
                .withArgument("x-message-ttl", 3000) // 3秒重试
                .build();
    }

    @Bean
    public Queue fileVectorDeleteRetryQueue() {
        return QueueBuilder.durable(FILE_VECTOR_DELETE_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_MAIN_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FILE_VECTOR_DELETE_ROUTING_KEY)
                .withArgument("x-message-ttl", 3000)
                .build();
    }

    @Bean
    public Queue fileVectorRetryQueue() {
        return QueueBuilder.durable(FILE_VECTOR_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_MAIN_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FILE_VECTOR_ROUTING_KEY)
                .withArgument("x-message-ttl", 15000)
                .build();
    }

    // ==========================================
    // 主队列绑定到 mainExchange
    // ==========================================
    @Bean
    public Binding fileStorageDeleteBinding(Queue fileStorageDeleteQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(fileStorageDeleteQueue)
                .to(mainExchange)
                .with(FILE_STORAGE_DELETE_ROUTING_KEY);
    }

    @Bean
    public Binding fileVectorDeleteBinding(Queue fileVectorDeleteQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(fileVectorDeleteQueue)
                .to(mainExchange)
                .with(FILE_VECTOR_DELETE_ROUTING_KEY);
    }

    @Bean
    public Binding fileVectorBinding(Queue fileVectorQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(fileVectorQueue)
                .to(mainExchange)
                .with(FILE_VECTOR_ROUTING_KEY);
    }

    // ==========================================
    // 重试队列绑定到 retryExchange
    // ==========================================
    @Bean
    public Binding fileStorageDeleteRetryBinding(Queue fileStorageDeleteRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(fileStorageDeleteRetryQueue)
                .to(retryExchange)
                .with(FILE_STORAGE_DELETE_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding fileVectorDeleteRetryBinding(Queue fileVectorDeleteRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(fileVectorDeleteRetryQueue)
                .to(retryExchange)
                .with(FILE_VECTOR_DELETE_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding fileVectorRetryBinding(Queue fileVectorRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(fileVectorRetryQueue)
                .to(retryExchange)
                .with(FILE_VECTOR_RETRY_ROUTING_KEY);
    }
}