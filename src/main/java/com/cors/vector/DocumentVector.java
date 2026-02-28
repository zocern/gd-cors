package com.cors.vector;

import com.cors.util.MinIoUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

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

        List<Future<?>> futures = new ArrayList<>();

        try (InputStream is = minIoUtil.getObject(storageKey)) {
            log.debug("开始处理文件向量化: {}", storageKey);

            // 解析文档
            Document document = tikaParser.parse(is, globalMetadata, futures);
            log.debug(document.toString());

            // 等待异步 batch 完成
            for (Future<?> f : futures) {
                f.get();
            }

            log.debug("向量化入库成功: {}", storageKey);
        } catch (Exception e) {
            processDeleteDoc(storageKey, batchId); // 出错回滚
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除文件：只删除向量数据
     * 核心逻辑：根据 storage_key 删除 Milvus 中的数据
     */
    public void processDeleteDoc(String storageKey, @Nullable String batchId) {
        log.debug("开始删除向量数据: {} - batchId={}", storageKey, batchId);

        Filter filter = MetadataFilterBuilder.metadataKey(METADATA_STORAGE_KEY).isEqualTo(storageKey);

        if (batchId != null && !batchId.isEmpty()) {
            filter = filter.and(MetadataFilterBuilder.metadataKey(METADATA_BATCH_ID).isEqualTo(batchId));
        }

        milvusEmbeddingStore.removeAll(filter);

        log.debug("向量数据删除完成: {} - batchId={}", storageKey, batchId);
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