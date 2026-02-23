package com.cors.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

import com.cors.enums.FileVectorStatusType;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 向量化文件状态表实体类
 */
@Data
@TableName("file_vector_status")
public class FileVectorStatus {

    /**
     * 文件对象名称（存储 key），主键
     */
    @TableId(type = IdType.INPUT) // 这里使用输入类型，保证你自己生成 UUID
    private String storageKey;

    /**
     * 文件处理状态：PROCESSING / SUCCESS / FAILED
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
