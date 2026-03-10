package com.cors.mq.producer;

import com.cors.config.rabbitmq.RabbitInitConfig;
import com.cors.mq.message.FileVectorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileVectorProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendFileVectorMessage(FileVectorMessage message) {

        try {
            CorrelationData correlation = new CorrelationData(message.getStorageKey() + "-VECTOR");
            log.debug("发送向量化消息: {}", message.getStorageKey());
            rabbitTemplate.convertAndSend(
                    RabbitInitConfig.FILE_MAIN_EXCHANGE,
                    RabbitInitConfig.FILE_VECTOR_ROUTING_KEY,
                    message,
                    correlation
            );
        } catch (Exception e) {
            log.error("发送向量化消息失败 (可由定时任务兜底)，TraceID={}", message.getStorageKey(), e);
        }
    }
}