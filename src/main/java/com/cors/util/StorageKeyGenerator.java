package com.cors.util;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class StorageKeyGenerator {

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private StorageKeyGenerator() {
        // prevent instantiation
    }

    /**
     * 生成存储Key，按 yyyy/MM/dd 目录组织
     *
     * @param filename 原始文件名
     * @return 存储路径，例如：2026/02/01/uuid.pdf
     */
    public static String generate(String filename) {
        String dateFolder = LocalDate.now().format(MONTH_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String extension = StringUtils.getFilenameExtension(filename);
        String objectName = StringUtils.hasText(extension)
                ? uuid + "." + extension
                : uuid;
        return dateFolder + "/" + objectName;
    }
}