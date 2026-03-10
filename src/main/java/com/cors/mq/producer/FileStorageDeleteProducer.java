package com.cors.mq.producer;

import com.cors.config.rabbitmq.RabbitInitConfig;
import com.cors.mq.message.FileDeleteMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorageDeleteProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送删除消息（Storage）
     */
    public void sendFileStorageDeleteMessage(FileDeleteMessage message) {
        try {
            // --- Storage 任务 ---
            CorrelationData correlation = new CorrelationData(message.getStorageKey() + "-STORAGE-DEL");
            log.info("发送存储删除消息: {}", message.getStorageKey());
            rabbitTemplate.convertAndSend(
                    RabbitInitConfig.FILE_MAIN_EXCHANGE,
                    RabbitInitConfig.FILE_STORAGE_DELETE_ROUTING_KEY,
                    message,
                    correlation
            );
        } catch (Exception e) {
            log.error("发送删除消息失败 (可由定时任务兜底)，TraceID={}", message.getStorageKey(), e);
        }
    }
}
