package com.cors.vector;

import com.cors.util.MinIoUtil;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.cors.constant.CommonConstants.METADATA_BATCH_ID;
import static com.cors.constant.CommonConstants.METADATA_STORAGE_KEY;

@Slf4j
@Component
public class DocumentVector {


    private final MinIoUtil minIoUtil;
    private final ApacheTikaDocumentParser tikaParser;
    private final EmbeddingStore<TextSegment> milvusEmbeddingStore;


    public DocumentVector(MinIoUtil minIoUtil,
                          ApacheTikaDocumentParser tikaParser,
                          EmbeddingStore<TextSegment> milvusEmbeddingStore) {
        this.minIoUtil = minIoUtil;
        this.tikaParser = tikaParser;
        this.milvusEmbeddingStore = milvusEmbeddingStore;
    }

    /**
     * 上传新文件：直接向量化入库 (假设是新文件，不检查旧数据以提高性能)
     * 核心逻辑：从 MinIO 下载 -> Tika 解析 -> 切分 -> 向量化入库
     */
    public void processUploadDoc(String storageKey) {
        String batchId = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> globalMetadata = Map.of(
                METADATA_STORAGE_KEY, storageKey,
                METADATA_BATCH_ID, batchId
        );

        List<CompletableFuture<?>> futures = Collections.synchronizedList(new ArrayList<>());

        try (InputStream is = minIoUtil.getObject(storageKey)) {
            log.debug("开始处理文件向量化: {}", storageKey);
            BufferedInputStream bis = new BufferedInputStream(is, 1024 * 1024); // 1MB
            // 解析文档
            Document document = tikaParser.parse(bis, globalMetadata, futures);
            log.debug(document.toString());

            if (!futures.isEmpty()) {
                // 只要其中任何一个任务抛出异常，join() 就会抛出 CompletionException
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            log.debug("向量化入库成功: {}", storageKey);
        } catch (BlankDocumentException | IllegalArgumentException re) {
            throw re;
        } catch (Exception e) {
            // 取消所有尚未完成的任务
            futures.forEach(f -> {
                if (f != null && !f.isDone()) f.cancel(true);
            });
            // 出错回滚
            processDeleteDoc(storageKey, batchId);
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除文件：只删除向量数据
     * 核心逻辑：根据 storage_key 删除 Milvus 中的数据
     */
    public void processDeleteDoc(String storageKey, @Nullable String batchId) {
        try {
            Filter filter = MetadataFilterBuilder.metadataKey(METADATA_STORAGE_KEY).isEqualTo(storageKey);
            if (batchId != null && !batchId.isEmpty()) {
                filter = filter.and(MetadataFilterBuilder.metadataKey(METADATA_BATCH_ID).isEqualTo(batchId));
            }
            log.debug("开始删除向量数据: {} - batchId={}", storageKey, batchId);
            milvusEmbeddingStore.removeAll(filter);
            log.debug("向量数据删除完成: {} - batchId={}", storageKey, batchId);
        } catch (Exception e) {
            log.error("向量数据物理删除失败 StorageKey: {}, BatchId: {}", storageKey, batchId, e);
        }
    }

    /**
     * 批量处理删除文件
     */
    public void processDeleteDocs(List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }

        try {
            log.debug("开始批量清理向量数据, 数量: {}", objectNames.size());

            // 这种方式比循环调用 removeAll 性能高很多，只需一次网络请求
            Filter filter = MetadataFilterBuilder.metadataKey(METADATA_STORAGE_KEY).isIn(objectNames);

            milvusEmbeddingStore.removeAll(filter);

            log.debug("批量向量数据清理完成: {}", objectNames);

        } catch (Exception e) {
            throw new RuntimeException("批量删除向量失败", e);
        }
    }
}