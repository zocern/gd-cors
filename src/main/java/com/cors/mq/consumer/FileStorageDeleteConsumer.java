package com.cors.mq.consumer;

import com.cors.config.rabbitmq.RabbitInitConfig;
import com.cors.mq.message.FileDeleteMessage;
import com.cors.util.MinIoUtil;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.cors.constant.CommonConstants.MAX_RETRIES;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitInitConfig.FILE_STORAGE_DELETE_QUEUE)
public class FileStorageDeleteConsumer {

    private final MinIoUtil minIOUtil;
    private final RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void handleFileDelete(FileDeleteMessage message,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        String storageKey = message.getStorageKey();

        try {
            log.info("开始处理 Storage 删除任务，storageKey={}", storageKey);

            // 业务逻辑 (MinIO 删除)
            minIOUtil.removeFile(storageKey);

            // 成功 ACK
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.debug("Storage 消费异常, storageKey={}", storageKey, e);
            try {
                sendToRetryQueue(message);
                channel.basicAck(tag, false);
            } catch (Exception sendEx) {
                log.debug("发送重试消息失败或ACK失败，保留原消息", sendEx);
            }
        }
    }

    /**
     * 发送 Storage 删除重试消息
     */
    private void sendToRetryQueue(FileDeleteMessage message) {

        String storageKey = message.getStorageKey();
        int retryCount = message.getRetryCount();

        if (retryCount >= MAX_RETRIES) {
            log.error("Storage 删除任务重试次数超限 ({}), 放弃文件: {}", MAX_RETRIES, storageKey);
            return;
        }

        FileDeleteMessage retryMessage = new FileDeleteMessage(storageKey, retryCount + 1);
        rabbitTemplate.convertAndSend(
                RabbitInitConfig.FILE_RETRY_EXCHANGE,
                RabbitInitConfig.FILE_STORAGE_DELETE_RETRY_ROUTING_KEY,
                retryMessage
        );
        log.debug("Storage 删除任务发送至重试队列 (第 {} 次), storageKey={}", retryMessage.getRetryCount(), retryMessage.getStorageKey());
    }
}
