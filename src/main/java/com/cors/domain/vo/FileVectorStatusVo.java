package com.cors.domain.vo;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

import com.cors.enums.FileVectorStatusType;
import lombok.Data;

/**
 * 文件向量化状态表视图表
 */
@Data
public class FileVectorStatusVo {

    // FileMetadata

    /**
     * 文件ID，主键
     */
    private Long id;

    /**
     * 文件对象名称
     */
    private String name;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 文件对象（存储 key），主键
     */
    private String storageKey;

    /**
     * 文件处理状态：PENDING / PROCESSING / SUCCESS / FAILED
     */
    private FileVectorStatusType status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime created;

    /**
     * 最后更新时间，自动更新
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updated;

    /**
     * 异常信息
     */
    private String errorMsg;
}
