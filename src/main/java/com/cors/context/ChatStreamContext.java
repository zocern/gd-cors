package com.cors.context;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cors.constant.CommonConstants.NONE;

@Data
@Builder
public class ChatStreamContext {

    // 流通道
    private Sinks.Many<ServerSentEvent<String>> sink;

    // 控制句柄
    private Disposable disposable;

    // 状态标记
    @Builder.Default
    private AtomicBoolean manualStop = new AtomicBoolean(false);

    // 防止缓存数据与数据库返回重复
    @Builder.Default
    private AtomicInteger status = new AtomicInteger(NONE);
}