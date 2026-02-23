package com.cors.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;

// 容量信息封装类
@Data
@AllArgsConstructor
public class StorageUsage {
    private long totalBytes;
    private long quotaBytes;
    private double usedPercentage;
    private Map<String, Long> usedBytes;
    private ZonedDateTime zonedDateTime;
}