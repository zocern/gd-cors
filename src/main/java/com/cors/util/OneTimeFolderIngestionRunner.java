package com.cors.runner;

import com.cors.util.LocalFolderIngestionService;
import com.cors.util.UserContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Spring Boot 启动完成后自动执行的 Runner (支持多路径)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneTimeFolderIngestionRunner implements CommandLineRunner {

    private final LocalFolderIngestionService localFolderIngestionService;

    @Value("${ingestion.run-on-startup}")
    private boolean runOnStartup;

    @Value("${ingestion.local-paths}")
    private List<String> localPaths;

    // 挂载的父节点ID
    @Value("${ingestion.parent-id}")
    private Long parentId;

    @Override
    public void run(String... args) throws Exception {
        if (!runOnStartup) {
            log.info("本地文件夹入库任务未开启，跳过执行。(ingestion.run-on-startup=false)");
            return;
        }

        if (localPaths == null || localPaths.isEmpty()) {
            log.error("未配置要入库的本地路径！请检查 ingestion.local-paths 参数。");
            return;
        }

        Long finalParentId = (parentId != null && parentId > 0) ? parentId : null;

        log.info("======================================================");
        log.info("🚀 触发一次性本地文件夹入库任务！");
        log.info("📁 共有 {} 个目标路径等待处理", localPaths.size());
        log.info("======================================================");

        // 异步执行，不阻塞 Spring Boot 主线程
        CompletableFuture.runAsync(() -> {
            UserContextUtil.setUserId(1L);
            for (int i = 0; i < localPaths.size(); i++) {
                String path = localPaths.get(i);
                log.info("▶️ 开始处理第 {}/{} 个路径: {}", (i + 1), localPaths.size(), path);

                try {
                    localFolderIngestionService.ingestLocalDirectory(path, finalParentId);
                } catch (Exception e) {
                    log.error("❌ 路径处理失败，跳过并继续下一个。路径: {}, 原因: {}", path, e.getMessage());
                }
            }

            log.info("======================================================");
            log.info("✅ 所有后台入库任务执行完毕！");
            log.info("======================================================");
        });
    }
}