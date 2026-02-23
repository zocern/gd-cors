package com.cors.vector;

import com.cors.config.milvus.parser.ApacheTikaDocumentParser;
import com.cors.util.MinIoUtil;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.cors.constant.CommonConstants.METADATA_BATCH_ID;
import static com.cors.constant.CommonConstants.METADATA_STORAGE_KEY;

@Slf4j
@Component
public class DocumentVector {


    private final MinIoUtil minIoUtil;
    private final ApacheTikaDocumentParser tikaParser;
    private final EmbeddingStore<TextSegment> milvusEmbeddingStore;
    private final EmbeddingStoreIngestor ingestor;

    public DocumentVector(MinIoUtil minIoUtil,
                          ApacheTikaDocumentParser tikaParser,
                          EmbeddingModel embeddingModel,
                          EmbeddingStore<TextSegment> milvusEmbeddingStore) {
        this.minIoUtil = minIoUtil;
        this.tikaParser = tikaParser;
        this.milvusEmbeddingStore = milvusEmbeddingStore;

        // TODO
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(500, 100);

        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(milvusEmbeddingStore)
                .build();
    }

    /**
     * 上传新文件：直接向量化入库 (假设是新文件，不检查旧数据以提高性能)
     * 核心逻辑：从 MinIO 下载 -> Tika 解析 -> 切分 -> 向量化入库
     */
    public void processUploadDoc(String storageKey) {
        String batchId = UUID.randomUUID().toString().replace("-", "");
        try (InputStream is = minIoUtil.getObject(storageKey)) {
            log.debug("开始处理文件向量化: {}", storageKey);

            // 解析文档 (Tika)
            Document document = tikaParser.parse(is);
            // 注入元数据 (Metadata) 用于后续的删除和检索过滤
            document.metadata().put(METADATA_STORAGE_KEY, storageKey);
            document.metadata().put(METADATA_BATCH_ID, batchId);

            // 切分与向量化入库
            ingestor.ingest(document);
            log.debug("向量化入库成功: {}", storageKey);

        } catch (BlankDocumentException | IllegalArgumentException e) {
            // 捕获拦截的“不支持类型”
            log.debug("检测到空文档或文件格式不支持，跳过处理: {}, 原因: {}", storageKey, e.getMessage());
            throw e;
        } catch (Exception e) {
            try {
                processDeleteDoc(storageKey, batchId);
            } catch (Exception ex) {
                log.error("补偿删除向量失败: storageKey: {}, batchId: {}", storageKey, batchId, ex);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除文件：只删除向量数据
     * 核心逻辑：根据 storage_key 删除 Milvus 中的数据
     */
//    public void processDeleteDoc(String storageKey) {
//        log.debug("开始清理旧向量数据: {}", storageKey);
//
//        Filter filter = MetadataFilterBuilder.metadataKey(METADATA_STORAGE_KEY).isEqualTo(storageKey);
//        milvusEmbeddingStore.removeAll(filter);
//
//        log.debug("旧向量数据清理完成: {}", storageKey);
//    }
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