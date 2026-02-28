package com.cors.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        final int corePoolSize = 32;       // 核心线程数，保证低延迟
        final int maxPoolSize = 64;        // 最大线程数，高峰期可扩展
        final int queueCapacity = 100;     // 队列容量，请求排队
        final int keepAliveSeconds = 60;   // 超过 core 的线程空闲回收时间
        final long defaultTimeout = 30000; // 异步请求默认超时时间，单位毫秒

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

        // 核心线程数、最大线程数
        taskExecutor.setCorePoolSize(corePoolSize);
        taskExecutor.setMaxPoolSize(maxPoolSize);

        // 队列容量
        taskExecutor.setQueueCapacity(queueCapacity);

        // 超过 core 的线程空闲回收时间
        taskExecutor.setKeepAliveSeconds(keepAliveSeconds);

        // 线程名字前缀，方便日志追踪
        taskExecutor.setThreadNamePrefix("mvc-async-");

        // 拒绝策略：队列满时，让提交线程自己执行，避免丢请求
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化线程池
        taskExecutor.initialize();

        // 指定 Spring MVC 异步请求执行器
        configurer.setTaskExecutor(taskExecutor);

        // 设置异步请求超时时间
        configurer.setDefaultTimeout(defaultTimeout);
    }
}