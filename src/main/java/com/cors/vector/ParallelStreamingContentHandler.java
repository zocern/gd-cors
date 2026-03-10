package com.cors.vector;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@Slf4j
public class ParallelStreamingContentHandler extends DefaultHandler {

    private final StringBuilder currentText = new StringBuilder();
    private final List<Document> batchIngestBuffer = new ArrayList<>();
    private final int flushThreshold;
    private final int batchIngestSize;
    private final ExecutorService executor;
    private final List<CompletableFuture<?>> futures;
    private final Semaphore semaphore;
    private final EmbeddingStoreIngestor ingestor;
    private final DocumentSplitter documentSplitter;
    private final Map<String, String> globalMetadata;
    private final dev.langchain4j.data.document.Metadata documentMetadata;

    public ParallelStreamingContentHandler(ExecutorService executor,
                                           List<CompletableFuture<?>> futures,
                                           EmbeddingModel embeddingModel,
                                           EmbeddingStore<TextSegment> milvusEmbeddingStore,
                                           Map<String, String> globalMetadata,
                                           dev.langchain4j.data.document.Metadata documentMetadata,
                                           int maxConcurrentBatches,
                                           int flushThreshold,
                                           int batchIngestSize,
                                           int maxSegmentSizeInChars,
                                           int maxOverlapSizeInChars) {

        if (maxSegmentSizeInChars > flushThreshold) {
            throw new IllegalArgumentException("maxSegmentSizeInChars must be less than or equal to flushThreshold");
        }

        this.executor = executor;
        this.futures = futures;
        this.semaphore = new Semaphore(maxConcurrentBatches);
        this.flushThreshold = flushThreshold;
        this.batchIngestSize = batchIngestSize;
        this.ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(milvusEmbeddingStore)
                .build();
        this.documentSplitter = DocumentSplitters.recursive(maxSegmentSizeInChars, maxOverlapSizeInChars);
        this.globalMetadata = globalMetadata != null ? globalMetadata : Collections.emptyMap();
        this.documentMetadata = documentMetadata != null ? documentMetadata : new dev.langchain4j.data.document.Metadata();
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        currentText.append(ch, start, length);
        if (currentText.length() >= flushThreshold) {
            splitAndSubmit(false);
        }
    }

    @Override
    public void endDocument() {
        if (!currentText.isEmpty()) {
            // 最后一次必须全部提交
            splitAndSubmit(true);
        }
        // 强制提交最后未满批次
        if (!batchIngestBuffer.isEmpty()) {
            submitBatch();
        }
    }

    private void splitAndSubmit(boolean forceSubmitAll) {

        String text = currentText.toString().trim();
        if (text.isEmpty()) {
            currentText.setLength(0);
            return;
        }

        List<TextSegment> segments = documentSplitter.split(Document.from(text));

        if (segments.isEmpty()) {
            return;
        }

        // 核心逻辑
        int limit = forceSubmitAll ? segments.size() : segments.size() - 1;

        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        // 全局 metadata
        globalMetadata.forEach(metadata::put);
        // 文件级 metadata
        metadata.putAll(documentMetadata.toMap());

        for (int i = 0; i < limit; i++) {
            TextSegment segment = segments.get(i);
            log.debug("metadata: {} ", metadata);
            batchIngestBuffer.add(Document.from(segment.text(), metadata));
            if (batchIngestBuffer.size() >= batchIngestSize) {
                submitBatch();
            }
        }

        // 保留最后一个（可能不完整）
        if (!forceSubmitAll) {
            TextSegment last = segments.get(segments.size() - 1);
            currentText.setLength(0);
            currentText.append(last.text());
        } else {
            currentText.setLength(0);
        }
    }

    private void submitBatch() {
        List<Document> batchToSubmit = new ArrayList<>(batchIngestBuffer);
        batchIngestBuffer.clear();

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // 使用 runAsync 并指定自定义线程池
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                ingestor.ingest(batchToSubmit);
            } catch (Exception e) {
                log.debug("Embedding ingest failed, thread={}", Thread.currentThread().getName(), e);
                throw e;
            } finally {
                semaphore.release();
            }
        }, executor);

        futures.add(future);
    }
}