package com.cors.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class FileDeleteMessage implements Serializable {

    /**
     * 全局唯一消息 ID (幂等性凭证)
     * 业务数据：要删除的文件 Key
     */
    private String storageKey;

    /**
     * 重试计数器，默认 0
     */
    private int retryCount;

    /**
     * 是否同时删除 file_vector_status 状态表记录。
     * <ul>
     *   <li>true  — 彻底删除（文件被永久删除时使用）</li>
     *   <li>false — 仅删除 Milvus 向量数据，保留状态记录（版本切换时使用，切回时可复用）</li>
     * </ul>
     */
    private boolean deleteStatus;

    /**
     * 兼容旧调用方：不传 deleteStatus 时默认为 true（彻底删除）
     */
    public FileDeleteMessage(String storageKey, int retryCount) {
        this.storageKey = storageKey;
        this.retryCount = retryCount;
        this.deleteStatus = true;
    }
}
