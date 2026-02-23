package com.cors.config.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter();
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        
        // 开启 Mandatory，否则路由失败消息会直接丢失
        rabbitTemplate.setMandatory(true);

        // 设置 ConfirmCallback (消息是否到达 Broker/Exchange)
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            // 获取消息 ID
            String msgId = (correlationData != null) ? correlationData.getId() : "UNKNOWN";

            if (ack) {
                log.info("[MQ-ACK] 消息发送成功, ID: {}", msgId);
            } else {
                log.error("[MQ-NACK] 消息发送失败, ID: {}, 原因: {}", msgId, cause);
                // TODO
                // 记录失败消息
            }
        });

        // 设置 ReturnsCallback (消息到达 Exchange 但找不到 Queue)
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("[MQ-RETURN] 路由失败: Exchange={}, Key={}, ReplyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyText());
            String msgBody = new String(returned.getMessage().getBody());
            log.error("丢失的消息内容: {}", msgBody);
            // TODO
            // 记录丢失消息
        });

        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}