package com.cors.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequest {
    @NotNull(message = "模型名称不能为空")
    String model;
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;
    @NotNull(message = "消息不能为空")
    private String message;
}