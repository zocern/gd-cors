package com.cors.service.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface OnlineAssistant extends Assistant {
    @SystemMessage("""
        你是广东省国土资源测绘院卫星应用中心的在线智能体助手。
        """)
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);
}
