package com.cors.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cors.enums.AssistantType;
import com.cors.enums.MessageType;
import com.cors.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("messages")
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    /**
     * 发送者类型：0=用户，1=人工智能，2=系统
     */
    private SenderType senderType;

    private MessageType messageType;

    /**
     * LOCAL 本地 ONLINE 联网
     */
    private AssistantType assistantType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime created;

    private String content;
}
