package com.cors.service;

import com.cors.domain.dto.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatService {

    void startChatStream(ChatRequest request);

    Flux<ServerSentEvent<String>> subscribe(Long sessionId, String lastChunkId);

    void stopGeneration(Long sessionId);
}
