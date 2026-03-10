package com.cors.controller;

import com.cors.domain.ResponseResult;
import com.cors.domain.dto.ChatRequest;
import com.cors.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sessions")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseResult<String>> submitChat(@RequestBody ChatRequest request) {
        chatService.startChatStream(request);
        return Mono.just(ResponseResult.success());
    }

    // 订阅 SSE 流

    /**
     * subscribeChatStream({
     * sessionId,
     * onChunk,
     * onFinish,
     * onError,
     * onId,
     * lastChunkId,
     * }) {
     * const sseUrl = new URL(`/api/v1/assistant/sse/${sessionId}`, BASE);
     * const controller = new AbortController();
     * const finished = fetchEventSource(sseUrl, {
     * method: "GET",
     * headers: {
     * Authorization: userStore.token,
     * "Last-Chunk-ID": lastChunkId,
     * },
     * openWhenHidden: true,
     * signal: controller.signal,
     * // 处理接收到的消息
     * onmessage(event) {
     * if (!event.data) return;
     * // 返回 chunk 及其 id
     * onId(event.id);
     * onChunk && onChunk(event.data);
     * },
     * // 连接关闭时的回调
     * onclose() {
     * onFinish && onFinish();
     * },
     * // 错误处理
     * onerror(err) {
     * onError && onError(err);
     * },
     * });
     * return {
     * cancel: () => controller.abort(),
     * finished,
     * };
     * }
     */
    @GetMapping(value = "/{sessionId}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> streamCompletion(
            @RequestHeader(value = "Last-Chunk-ID", required = false) String lastChunkId,
            @PathVariable Long sessionId) {
        log.info("客户端连接: sessionId={},  lastChunkId={}", sessionId, lastChunkId);
        return chatService.subscribe(sessionId, lastChunkId);
    }

    @PostMapping("/{sessionId}/stop")
    public ResponseResult<Void> stopGeneration(
            @PathVariable Long sessionId) {
        chatService.stopGeneration(sessionId);
        return ResponseResult.success();
    }
}