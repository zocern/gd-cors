package com.cors.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cors.config.rabbitmq.RabbitInitConfig;
import com.cors.domain.entity.FileVectorStatus;
import com.cors.enums.FileVectorStatusType;
import com.cors.mapper.FileVectorStatusMapper;
import com.cors.mq.message.FileVectorMessage;
import com.cors.vector.DocumentVector;
import com.rabbitmq.client.Channel;
import dev.langchain4j.data.document.BlankDocumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.cors.constant.CommonConstants.MAX_RETRIES;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitInitConfig.FILE_VECTOR_QUEUE)
public class FileVectorConsumer {

    private final DocumentVector documentVector;
    private final RabbitTemplate rabbitTemplate;
    private final FileVectorStatusMapper fileVectorStatusMapper;

    @RabbitHandler
    public void processFileVector(FileVectorMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        String storageKey = message.getStorageKey();

        try {
            // 幂等性检查
            // 尝试将状态从 PENDING -> PROCESSING
            LambdaUpdateWrapper<FileVectorStatus> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FileVectorStatus::getStorageKey, storageKey)
                    .eq(FileVectorStatus::getStatus, FileVectorStatusType.PENDING)
                    .set(FileVectorStatus::getStatus, FileVectorStatusType.PROCESSING);

            int updated = fileVectorStatusMapper.update(null, updateWrapper);

            if (updated == 0) {
                // 说明已经在 PROCESSING 或 SUCCESS，直接幂等跳过
                log.debug("重复的向量化任务被拦截，storageKey={}", storageKey);
                channel.basicAck(tag, false);
                return;
            }

            log.debug("开始处理向量化任务，storageKey={}", storageKey);

            // 执行业务逻辑 (上传/向量化)
            // documentVector.processUploadDoc(storageKey);
            documentVector.processUploadDoc(storageKey);

            // 标记成功状态
            updateStatus(storageKey, FileVectorStatusType.SUCCESS, null);

            // 成功 ACK
            channel.basicAck(tag, false);

        } catch (BlankDocumentException | IllegalArgumentException e) {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            log.error("文件向量化拦截: {}", storageKey, e);
            updateStatus(storageKey, FileVectorStatusType.UNSUPPORTED, rootCause.getMessage());
            try {
                channel.basicAck(tag, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (Exception e) {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
            updateStatus(storageKey, FileVectorStatusType.FAILED, rootCause.getMessage());
            log.debug("Vector 向量化消费异常, storageKey={}", storageKey, e);

            // 发生未知异常（如网络中断、Redis 宕机），不要 ACK。
            // A. NACK 并 requeue=true (无限重试，风险大)
            // B. NACK 并 requeue=false (进入死信队列，推荐) + 告警
            // C. 捕获后手动发重试队列 + ACK (类似你原本的逻辑)

            // 发生异常时，发送到重试队列，并 ACK 避免阻塞队列
            try {
                sendToRetryQueue(message);
                channel.basicAck(tag, false);
            } catch (Exception sendEx) {
                log.debug("发送重试消息失败或ACK失败，保留原消息", sendEx);
            }
        }
    }

    /**
     * 发送重试消息 (逻辑与 FileDeleteConsumer 保持一致)
     */
    private void sendToRetryQueue(FileVectorMessage message) {

        String storageKey = message.getStorageKey();

        LambdaUpdateWrapper<FileVectorStatus> updateWrapper = new LambdaUpdateWrapper<FileVectorStatus>()
                .eq(FileVectorStatus::getStorageKey, storageKey)
                .eq(FileVectorStatus::getStatus, FileVectorStatusType.FAILED)
                .lt(FileVectorStatus::getRetryCount, MAX_RETRIES) // retry_count < MAX_RETRIES
                .set(FileVectorStatus::getStatus, FileVectorStatusType.PENDING)
                .set(FileVectorStatus::getErrorMsg, null)
                .setSql("retry_count = retry_count + 1");
        int updated = fileVectorStatusMapper.update(null, updateWrapper);

        if (updated == 0) {
            log.error("Vector 向量化任务重试次数超限 ({})，放弃任务: {}", MAX_RETRIES, storageKey);
            return;
        }

        rabbitTemplate.convertAndSend(
                RabbitInitConfig.FILE_RETRY_EXCHANGE,
                RabbitInitConfig.FILE_VECTOR_RETRY_ROUTING_KEY,
                message
        );
        FileVectorStatus fileVectorStatus = fileVectorStatusMapper.selectById(storageKey);
        log.debug("Vector 向量化任务发送至重试队列 {}/{} , storageKey={}",
                fileVectorStatus.getRetryCount(), MAX_RETRIES, message.getStorageKey());
    }

    private void updateStatus(String storageKey, FileVectorStatusType status, String errorMsg) {

        LambdaUpdateWrapper<FileVectorStatus> updateWrapper = new LambdaUpdateWrapper<FileVectorStatus>()
                .eq(FileVectorStatus::getStorageKey, storageKey)
                .eq(FileVectorStatus::getStatus, FileVectorStatusType.PROCESSING)
                .set(FileVectorStatus::getStatus, status)
                .set(FileVectorStatus::getErrorMsg, errorMsg);
        fileVectorStatusMapper.update(null, updateWrapper);
    }
}