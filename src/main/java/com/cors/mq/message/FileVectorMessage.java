package com.cors.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class FileVectorMessage implements Serializable {
    /**
     * 全局唯一消息 ID (幂等性凭证)
     * 文件存储的 Key
     */
    private String storageKey;
}