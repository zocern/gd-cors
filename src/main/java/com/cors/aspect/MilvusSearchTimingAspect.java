package com.cors.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class MilvusSearchTimingAspect {
    /**
     * 拦截 MilvusEmbeddingStore 的 search 方法，用于统计 Milvus 搜索的耗时
     */
    @Around("execution(* dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore.search(..))")
    public Object measureSearchTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis(); // 记录开始时间

        // 执行 search 方法
        Object result = pjp.proceed();

        long end = System.currentTimeMillis(); // 记录结束时间
        long duration = end - start; // 计算搜索耗时
        log.info("Milvus 索引搜索耗时: {} ms", duration); // 打印 Milvus 索引搜索耗时

        return result;
    }
}
