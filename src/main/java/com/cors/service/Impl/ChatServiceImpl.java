package com.cors.service.Impl;

import com.cors.context.ChatStreamContext;
import com.cors.domain.dto.ChatRequest;
import com.cors.domain.entity.ChatMessage;
import com.cors.domain.entity.Session;
import com.cors.enums.AssistantType;
import com.cors.enums.MessageType;
import com.cors.enums.SenderType;
import com.cors.exception.BadRequestException;
import com.cors.service.ChatService;
import com.cors.service.MessageService;
import com.cors.service.SessionService;
import com.cors.service.ai.Assistant;
import com.cors.util.UserContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static com.cors.constant.CommonConstants.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    // Map 的作用： 是给别人（比如取消接口、管理员接口）用来找到你的
    // 局部变量的作用： 是给你自己（当前线程的生命周期）使用的
    // 成员变量 Map 作为多线程间通信的桥梁
    private final Map<Long, ChatStreamContext> contextMap = new ConcurrentHashMap<>();
    private final Map<String, Assistant> assistantMap;
    private final SessionService sessionService;
    private final MessageService messageService;

    // TODO 单会话互斥
    private final Map<Long, Semaphore> sessionLockMap = new ConcurrentHashMap<>();

    /**
     * 异步对话生成
     */
    public void startChatStream(ChatRequest request) {

        // 权限校验
        Long userId = UserContextUtil.getUserId();
        String model = request.getModel();
        Long sessionId = request.getSessionId();
        String message = request.getMessage();
        // String message = request.getMessage() + " /no_think";

        // 此处注意判空
        Session session = sessionService.getById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BadRequestException("会话权限不足");
        }
        // 校验与获取 Bean
        AssistantType assistantType = AssistantType.fromName(model);
        String beanName = assistantType.getValue();
        Assistant assistant = assistantMap.get(beanName);
        if (assistant == null) {
            throw new IllegalArgumentException("服务不可用");
        }

        // ReentrantLock 是可重入锁，它有一个严格的规定：“加锁的线程必须与解锁的线程是同一个”
        // 解锁 (saveOnce.run() -> lock.unlock)： 这个动作是在 subscribe 的回调（onComplete / onError）中触发的。此时，执行线程已经是 Reactor 线程

        // TODO
        Semaphore lock = sessionLockMap.computeIfAbsent(sessionId, k -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            // 说明有正在进行的任务
            throw new BadRequestException("当前会话正在生成中，请稍后重试");
        }
        log.info("session [{}] 获取到锁，允许开始新的流式任务", sessionId);
        try {
            ChatMessage chatMessageUser = ChatMessage.builder()
                    .sessionId(sessionId)
                    .senderType(SenderType.USER)
                    .messageType(MessageType.TEXT)
                    .created(LocalDateTime.now())
                    .content(message)
                    .assistantType(assistantType)
                    .build();

            messageService.save(chatMessageUser); // 入库
            log.debug("用户消息已保存，准备请求 AI...");

            // 创建一个具有“重放”功能的 Sink
            // replay().all() 表示：无论何时有新客户端连进来，都把之前的历史记录全发给它
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().all();
            StringBuffer contentBuilder = new StringBuffer();
            ChatStreamContext context = ChatStreamContext.builder()
                    .sink(sink)
                    .build();

            // 保存会话的任务
            Runnable saveOnce = () -> {
                // (CAS) 保证入库只执行一次
                if (context.getStatus().compareAndSet(false, true)) {
                    try {
                        String finalAnswer = contentBuilder.toString();
                        if (!finalAnswer.isBlank()) {

                            log.debug("AI 会话消息入库, 长度: [{}], 内容: [{}]", finalAnswer.length(), finalAnswer);
                            ChatMessage chatMessageAi = ChatMessage.builder()
                                    .sessionId(sessionId)
                                    .senderType(SenderType.AI)
                                    .messageType(MessageType.TEXT)
                                    .created(LocalDateTime.now())
                                    .content(finalAnswer)
                                    .assistantType(assistantType)
                                    .build();
                            messageService.save(chatMessageAi); // 入库
                        } else {
                            log.debug("会话 [{}] 生成内容为空，跳过入库", sessionId);
                        }
                        // 通知前端结束 (让 UI 停止 Loading)
                        sink.tryEmitComplete();
                    } catch (Exception e) {
                        log.error("会话 [{}] 结算处理异常，内容长度: [{}], 内容详情: [{}]",
                                sessionId, contentBuilder.length(), contentBuilder, e);
                        sink.tryEmitError(new RuntimeException("保存会话失败"));
                    } finally {
                        contextMap.remove(sessionId);
                        log.info("会话 [{}] 内存上下文已清理", sessionId);
                        lock.release();
                        log.info("会话 [{}] 锁已释放，流程结束", sessionId);
                    }
                }
            };

            // 启动流并获取 Disposable 句柄
            String contextKey = sessionId + ":" + beanName;
            // Flux 本质上是基于惰性求值的声明式数据流蓝图，它仅定义了数据的生产与处理逻辑，而只有在被订阅（subscribe）时才会真正触发执行并产生数据
            Disposable disposable = assistant.chat(contextKey, message)
                    .subscribeOn(Schedulers.boundedElastic())
                    // 处理业务异常，防止整个流中断
                    .onErrorResume(e -> {
                        log.error("处理会话 [{}] 时发生错误: {}", sessionId, e.getMessage(), e);
                        return Flux.just("当前服务繁忙或出现异常，请稍后再试 🙏");
                    })
                    .doOnNext(raw -> log.debug("会话 [{}] token: {}", sessionId, raw))
                    // 获取每个 token 的序号，方便前端断点续连
                    .index()
                    // 收集 token
                    .doOnNext(tuple -> contentBuilder.append(tuple.getT2()))
                    // 监听取消事件（前端断开连接）
                    .doOnCancel(() -> {
                        if (context.getManualStop().get()) {
                            log.warn("会话 [{}] 检测到手动停止，执行截断保存", sessionId);
                            saveOnce.run();
                        } else {
                            // Reactor 内部异常取消，或者系统关闭，无需操作
                            log.debug("会话 [{}] 被动取消 (忽略，后台继续运行或已异常处理)", sessionId);
                        }
                    })
                    // 监听错误事件（如果有未被 onErrorResume 捕获的）
                    .doOnError(err -> {
                        log.error("流处理发生错误", err);
                        saveOnce.run();
                    })
                    .map(tuple ->
                            ServerSentEvent.<String>builder()
                                    .id(String.valueOf(tuple.getT1()))
                                    .data(tuple.getT2())
                                    .build())
                    .subscribe(
                            // onNext 推入 Sink 缓存
                            sink::tryEmitNext,
                            // onError 订阅层面的错误
                            error -> {
                                log.error("Subscribe 异常", error);
                                saveOnce.run();
                            },
                            // onComplete 正常结束
                            () -> {
                                log.info("会话 [{}] 后台生成正常结束", sessionId);
                                saveOnce.run();
                            }
                    );
            context.setDisposable(disposable);
            contextMap.put(sessionId, context);
        } catch (Exception e) {
            log.error("session [{}] 发生异常: {}", sessionId, e.getMessage(), e);
            // 出现异常时需要确保释放锁
            lock.release();
            log.info("session [{}] 释放锁", sessionId);
        }
    }


//    场景复现：
//    后端：AI 生成完毕 -> 已入库（DB有数据） -> 缓存还没删（Sink还有数据）。
//    前端：用户点进来 -> 查DB（拿到了完整数据） -> 连SSE（带着旧的或空的 Last-Chunk-ID）。
//
//    冲突：
//    前端已经在界面上渲染了 DB 里的完整回复。
//    后端 subscribe 发现缓存还在，且前端传的 Chunk-ID 很小（因为前端没用 DB 里的最新 ID，或者是以前的 ID）。
//    后端 skipWhile 失效，把缓存里的数据又推了一遍。
//    结果：界面上出现了两段一模一样的回复（一段来自DB，一段来自SSE拼接）。

    /**
     * 获取流（支持断点续传）
     */
    public Flux<ServerSentEvent<String>> subscribe(Long sessionId, String lastChunkId) {
        ChatStreamContext context = contextMap.get(sessionId);
        if (context == null || context.getSink() == null || context.getStatus().get()) {
            log.info("会话 [{}] 缓存已失效，通知前端读取历史归档", sessionId);
            // 发送一个一次性的信号事件，告诉前端去查库
            // 这里用 Flux.just 发送单条数据后流就会立即结束 (complete)
            return Flux.just(ServerSentEvent.<String>builder()
                    .id(CHUNK_TYPE_ID)
                    .data("")
                    .build());
        }

        return context.getSink().asFlux()
                .skipWhile(chunk -> {
                    // 如果前端传了 Last-Chunk-ID，跳过已经接收过的
                    if (lastChunkId == null || chunk.id() == null)
                        return false;  // 如果没有 Last-Chunk-ID 或者 chunk.id() 为 null，直接开始传递
                    try {
                        // 只有 chunk.id() 小于等于 lastChunkId 才跳过
                        return Long.parseLong(chunk.id()) <= Long.parseLong(lastChunkId);
                    } catch (NumberFormatException e) {
                        return false;  // 如果 id 无法解析为数字，则跳过
                    }
                });
    }


//    取消信号（Cancel Signal）是向上传递的，且由发起取消的线程同步执行
//    Reactor 规定：所有控制指令（要多少数据、不想要数据了），必须由消费者发起，一层层通知到生产者
//    1：前端发送 HTTP 请求 /stop
//    2：Tomcat/Netty 的 Web 处理线程（比如 http-nio-8080-exec-1）接收请求，进入 stopGeneration 方法
//    3：该线程执行 context.getDisposable().dispose()
//    4：dispose() 发出取消信号，信号沿着 Flux 链条向上游（Upstream）传递
//    5：信号遇到了 .doOnCancel(...) 钩子。
//    6：就在当前这个 Web 处理线程上，立即执行 lambda 表达式里的 saveOnce.run()

    /**
     * 手动停止接口
     * 用户点击前端的“停止生成”按钮
     */
    public void stopGeneration(Long sessionId) {
        ChatStreamContext context = contextMap.get(sessionId);
        if (context != null && !context.getDisposable().isDisposed()) {
            log.info("接收到停止指令，正在终止会话: {}", sessionId);
            context.getManualStop().set(true);
            context.getDisposable().dispose();
        }
    }
}