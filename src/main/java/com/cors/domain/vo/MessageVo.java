package com.cors.domain.vo;

import com.cors.enums.AssistantType;
import com.cors.enums.MessageType;
import com.cors.enums.SenderType;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MessageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    private LocalDateTime created;

    private String content;
}
