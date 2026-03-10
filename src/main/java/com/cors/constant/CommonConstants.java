package com.cors.constant;

import java.util.Set;

public final class CommonConstants {

    private CommonConstants() {
    }

    public static final String METADATA_STORAGE_KEY = "storage_key";
    public static final String METADATA_BATCH_ID = "batchId";

    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String MEMORY_KEY_PREFIX = "gd-cors:memory:";
    public static final String MYBATIS_CACHE_KEY_PREFIX = "gd-cors:mybatis:cache:";
    public static final int MAX_RETRIES = 3;

    /**
     * 业务信号量
     */
    public static final String CHUNK_TYPE_ID = "ARCHIVE_FINISHED";

    public static final Set<String> BLACKLISTED_TYPES = Set.of(
            // --- 1. 执行文件与系统文件 ---
            "application/x-msdownload",      // .exe, .dll
            "application/x-sh",              // .sh (脚本通常作为文本处理才有意义)
            "application/x-object",          // .o, .obj
            "application/octet-stream",       // 识别失败的二进制流

            // --- 2. 压缩与容器格式 ---
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/x-gzip",

            // --- 3. 纯多媒体格式 ---
            "audio/mpeg", "audio/x-wav", "audio/ogg", "audio/mp4",
            "video/mp4", "video/x-msvideo", "video/quicktime", "video/x-flv",
            "image/gif",                     // 动图在某些 VLM 中只能看到第一帧
            "image/vnd.adobe.photoshop",     // .psd (Tika 能测出，但 VLM 打不开)
            "image/tiff",                    // .tiff (某些 API 不支持)
            "image/png", "image/jpeg", "image/bmp",
            // --- 4. 字体与加密文件 ---
            "application/x-font-ttf",
            "application/x-font-woff",
            "application/pgp-encrypted",
            "application/pkcs7-signature",

            // --- 5. 数据库与特殊格式 ---
            "application/x-sqlite3",
            "application/x-berkeley-db"
    );
}
