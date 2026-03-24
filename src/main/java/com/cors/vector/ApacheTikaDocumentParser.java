package com.cors.vector;


import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static com.cors.constant.CommonConstants.BLACKLISTED_TYPES;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Apache Tika based document parser with VLM enhancement.
 *
 * @author zocern
 */
@Slf4j
public class ApacheTikaDocumentParser implements DocumentParser {

    private static final int NO_WRITE_LIMIT = -1;
    private static final int MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    // 静态单例，类加载时初始化一次
    private static final Tika TIKA_INSTANCE = new Tika();

    // Tika 默认组件 Supplier
    public static final Supplier<Parser> DEFAULT_PARSER_SUPPLIER = AutoDetectParser::new;
    public static final Supplier<Metadata> DEFAULT_METADATA_SUPPLIER = Metadata::new;
    public static final Supplier<ParseContext> DEFAULT_PARSE_CONTEXT_SUPPLIER = ParseContext::new;
    // public static final Supplier<ContentHandler> DEFAULT_CONTENT_HANDLER_SUPPLIER = () -> new BodyContentHandler(NO_WRITE_LIMIT);

    private final Supplier<Parser> parserSupplier;
    private final Supplier<Metadata> metadataSupplier;
    private final Supplier<ParseContext> parseContextSupplier;

    private final ExecutorService executor;
    private final OllamaChatModel ollamaChatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> milvusEmbeddingStore;

    public ApacheTikaDocumentParser(ExecutorService executor, OllamaChatModel ollamaChatModel, EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> milvusEmbeddingStore) {
        this(executor, ollamaChatModel, embeddingModel, milvusEmbeddingStore, null, null, null);
    }

    public ApacheTikaDocumentParser(
            ExecutorService executor,
            OllamaChatModel ollamaChatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> milvusEmbeddingStore,
            Supplier<Parser> parserSupplier,
            Supplier<Metadata> metadataSupplier,
            Supplier<ParseContext> parseContextSupplier
    ) {
        this.executor = executor;
        this.ollamaChatModel = ollamaChatModel;
        this.embeddingModel = embeddingModel;
        this.milvusEmbeddingStore = milvusEmbeddingStore;
        this.parserSupplier = getOrDefault(parserSupplier, () -> DEFAULT_PARSER_SUPPLIER);
        this.metadataSupplier = getOrDefault(metadataSupplier, () -> DEFAULT_METADATA_SUPPLIER);
        this.parseContextSupplier = getOrDefault(parseContextSupplier, () -> DEFAULT_PARSE_CONTEXT_SUPPLIER);
    }

    @Override
    public Document parse(InputStream inputStream) {
        return null;
    }

    public Document parse(InputStream inputStream, Map<String, String> globalMetadata, List<CompletableFuture<?>> futures) {
        try (TikaInputStream tis = TikaInputStream.get(inputStream)) {
            Metadata metadata = metadataSupplier.get();

            String mediaType = TIKA_INSTANCE.detect(tis);
            if (mediaType != null) metadata.set(Metadata.CONTENT_TYPE, mediaType);

            if (mediaType == null || BLACKLISTED_TYPES.contains(mediaType)) {
                throw new IllegalArgumentException("Unsupported file type: " + mediaType);
            }

            parseDocumentStreamingWithEmbedding(
                    tis,
                    futures,
                    embeddingModel,
                    milvusEmbeddingStore,
                    metadata,
                    globalMetadata
            );

        } catch (IllegalArgumentException ie) {
            throw ie;
        } catch (Exception e) {
            throw new RuntimeException("Document parsing failed", e);
        }

        return Document.from("Document parsed and ingested into vector store");
    }

    /**
     * 使用 Tika Parser 解析文本，并挂载 EmbeddedDocumentExtractor 拦截内部图片
     */
    private void parseDocumentStreamingWithEmbedding(InputStream stream,
                                                     List<CompletableFuture<?>> futures,
                                                     EmbeddingModel embeddingModel,
                                                     EmbeddingStore<TextSegment> milvusEmbeddingStore,
                                                     Metadata metadata,
                                                     Map<String, String> globalMetadata)
            throws IOException, TikaException, SAXException {

        Parser parser = parserSupplier.get();
        ParseContext parseContext = parseContextSupplier.get();

        // PDF 配置
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setEnableAutoSpace(true);
        pdfConfig.setExtractInlineImages(false); // 不识别图片
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        pdfConfig.setSortByPosition(true);
        parseContext.set(PDFParserConfig.class, pdfConfig);

        // 嵌入式文档提取器 (拦截 PDF/Word 内部图片)
//        parseContext.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
//            @Override
//            public boolean shouldParseEmbedded(Metadata m) {
//                return true;
//            }
//
//            @Override
//            public void parseEmbedded(InputStream embeddedStream, ContentHandler handler, Metadata m, boolean outputHtml) // m 嵌入对象的元信息
//                    throws SAXException, IOException {
//
//                String contentType = m.get(Metadata.CONTENT_TYPE);
//                String resourceName = getOrDefault(TikaCoreProperties.RESOURCE_NAME_KEY, "embedded-resource");
//
//                // 拦截图片资源
//                if (contentType != null && contentType.startsWith("image/")) {
//                    log.debug("Detected embedded image: Type={}, Name={}", contentType, resourceName);
//
//                    byte[] imageBytes = readBytesWithLimit(embeddedStream);
//                    // 复用公共逻辑
//                    String formattedOutput = processImageAndFormat(imageBytes, contentType, resourceName);
//
//                    if (!isNullOrBlank(formattedOutput)) {
//                        handler.characters(formattedOutput.toCharArray(), 0, formattedOutput.length());
//                    }
//                }
//            }
//        });

        // 流式字符串处理
        // 每次消费一个文件就创建新的 handler 信号量独立
        ParallelStreamingContentHandler streamingHandler = new ParallelStreamingContentHandler(
                executor,
                futures,
                embeddingModel,
                milvusEmbeddingStore,
                globalMetadata,
                convert(metadata),
                4,
                10000,
                100,
                500,
                100
        );

        parser.parse(stream, streamingHandler, metadata, parseContext);
    }

    /**
     * 核心逻辑：读取 Bytes -> VLM 调用 -> Markdown 格式化
     * 提取出来供独立图片和嵌入式图片共用
     */
    private String processImageAndFormat(byte[] imageBytes, String mimeType, String resourceName) {
        if (imageBytes == null) {
            log.debug("Image skipped (too small or null).");
            return null;
        }

        try {
            String description = callVlmModel(imageBytes, mimeType);
            if (!isNullOrBlank(description)) {
                // 格式化输出：图片名 + 引用块描述
                return String.format("\n\n![Image: %s]\n> %s\n\n", resourceName, description);
            }
        } catch (Exception e) {
            log.debug("Vlm error processing image {}: {}", resourceName, e.getMessage());
        }
        return null;
    }

    /**
     * 使用 LangChain4j 的 VlmModel 接口调用多模态模型
     */
    private String callVlmModel(byte[] imageBytes, String mimeType) {
        if (ollamaChatModel == null) {
            return "";
        }
        // 语义标记
        if (mimeType == null || mimeType.isBlank() || "application/octet-stream".equals(mimeType)) {
            mimeType = "image/jpeg";
        }
        try {
            // LangChain4j 的 Image 对象封装
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            // { "content": [ {"type":"text", "text":""}, {"type":"image_url","image_url":{"url":""}}]}
            UserMessage userMessage = UserMessage.from(
                    TextContent.from(
                            """
                                    You are an advanced OCR and image analysis assistant.
                                    
                                    Task Strategy:
                                    1. **Text Extraction (Priority)**: If the image contains text (documents, code, slides, diagrams):
                                       - Transcribe the text **verbatim** in its **ORIGINAL language**.
                                       - Use **Markdown** to preserve structure (headers, lists, tables, code blocks).
                                       - Do NOT translate text unless the text itself is a translation request.
                                    
                                    2. **Visual Description (Fallback)**: If the image is purely visual (photos, art) with NO readable text:
                                       - Provide a concise description in **Chinese**.
                                    
                                    3. **Hybrid Content**: If it is a chart or diagram (like a flowchart):
                                       - Transcribe the text AND briefly describe the relationships in Chinese (e.g., '流程图显示...').
                                    
                                    Output Rules:
                                    - Output ONLY the result. No conversational filler (e.g., 'Here is the output')."""
                    ),
                    ImageContent.from(base64Data, mimeType)
            );
            ChatRequest request = ChatRequest.builder()
                    .messages(userMessage)
                    .parameters(ollamaChatModel.defaultRequestParameters())
                    .build();
            // TODO
            ChatResponse chatResponse = ollamaChatModel.doChat(request);
            return chatResponse.aiMessage().text();
        } catch (Exception e) {
            log.debug("Vlm generation failed: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 安全读取：带最大字节限制的流读取
     *
     * @param stream 输入流
     * @return 读取到的字节数组；如果超过限制，则返回 null (表示丢弃)
     */
    private byte[] readBytesWithLimit(InputStream stream) throws IOException {
        // 限制最大读取大小为 LIMIT + 1，用于判断是否超限
        int limit = ApacheTikaDocumentParser.MAX_IMAGE_SIZE_BYTES;

        // readNBytes 会尝试读取指定数量的字节，如果流还没结束但字节数够了，就停止
        byte[] data = stream.readNBytes(limit + 1);

        if (data.length > limit) {
            log.debug("Image skipped: size exceeds limit {}", limit);
            return null;
        }
        return data;
    }

    private dev.langchain4j.data.document.Metadata convert(Metadata tikaMetadata) {
        final Map<String, String> tikaMetaData = new HashMap<>();
        for (String name : tikaMetadata.names()) {
            tikaMetaData.put(name, String.join(";", tikaMetadata.getValues(name)));
        }
        return new dev.langchain4j.data.document.Metadata(tikaMetaData);
    }
}