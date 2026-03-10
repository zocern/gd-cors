package com.cors.mq.consumer;

import com.cors.config.rabbitmq.RabbitInitConfig;
import com.cors.mapper.FileVectorStatusMapper;
import com.cors.mq.message.FileDeleteMessage;
import com.cors.vector.DocumentVector;
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
@RabbitListener(queues = RabbitInitConfig.FILE_VECTOR_DELETE_QUEUE)
public class FileVectorDeleteConsumer {

    // 依赖 DocumentVector，不需要 MinIoUtil
    private final DocumentVector documentVector;
    private final RabbitTemplate rabbitTemplate;
    private final FileVectorStatusMapper fileVectorStatusMapper;

    @RabbitHandler
    public void handleVectorDelete(FileDeleteMessage message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        String storageKey = message.getStorageKey();

        try {
            log.info("开始处理 Vector 删除任务，storageKey={}", storageKey);

            // 遍历 List 进行删除
            documentVector.processDeleteDoc(storageKey, null);

            // 删除状态表记录
            fileVectorStatusMapper.deleteById(storageKey);

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
     * 发送重试消息 (逻辑一致，但 Exchange/RoutingKey 不同)
     */
    private void sendToRetryQueue(FileDeleteMessage message) {

        String storageKey = message.getStorageKey();
        int retryCount = message.getRetryCount();

        if (retryCount >= MAX_RETRIES) {
            log.error("Vector 删除任务重试次数超限 ({})，放弃文件: {}", MAX_RETRIES, storageKey);
            return;
        }

        // 构造新消息
        FileDeleteMessage retryMessage = new FileDeleteMessage(storageKey, retryCount + 1);

        // 发送到向量删除重试交换机
        rabbitTemplate.convertAndSend(
                RabbitInitConfig.FILE_RETRY_EXCHANGE,
                RabbitInitConfig.FILE_VECTOR_DELETE_RETRY_ROUTING_KEY,
                retryMessage
        );
        log.debug("Vector 删除任务发送至重试队列 (第 {} 次), storageKeys={}", retryMessage.getRetryCount(), retryMessage.getStorageKey());
    }
}