package com.cors.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ExecutorService vectorIngestExecutor() {
        final int corePoolSize = 20;
        final int maxPoolSize = 40;
        final int queueCapacity = 500;
        final long keepAliveSeconds = 60L;

        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r, "vector-ingest-" + count.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };

        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                queue,
                threadFactory,
                handler
        );
    }
}