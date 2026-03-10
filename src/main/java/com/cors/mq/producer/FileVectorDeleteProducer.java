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
public class FileVectorDeleteProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送删除消息（Vector）
     */
    public void sendFileVectorDeleteMessage(FileDeleteMessage message) {
        try {

            // --- Vector 任务 ---
            CorrelationData correlation = new CorrelationData(message.getStorageKey() + "-VECTOR-DEL");
            log.info("发送向量删除消息: {}", message.getStorageKey());
            rabbitTemplate.convertAndSend(
                    RabbitInitConfig.FILE_MAIN_EXCHANGE,
                    RabbitInitConfig.FILE_VECTOR_DELETE_ROUTING_KEY,
                    message,
                    correlation
            );
        } catch (Exception e) {
            log.error("发送删除消息失败 (可由定时任务兜底)，TraceID={}", message.getStorageKey(), e);
        }
    }
}
