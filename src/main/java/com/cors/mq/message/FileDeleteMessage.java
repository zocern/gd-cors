package com.cors.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class FileDeleteMessage implements Serializable {

    /**
     * 全局唯一消息 ID (幂等性凭证)
     * 业务数据：要删除的文件 Key 列表
     */
    private String storageKey;

    // 重试计数器，默认 0
    private int retryCount;
}