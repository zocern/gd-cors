package com.cors.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件版本实体类，对应数据库表 file_versions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_versions")
public class FileVersion {

    /**
     * 版本记录ID，主键，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的 file_metadata.id
     */
    private Long fileId;

    /**
     * 版本号（从1开始递增）
     */
    private Integer version;

    /**
     * 该版本在 MinIO 的存储键
     */
    private String storageKey;

    /**
     * 该版本文件大小（字节）
     */
    private Long size;

    /**
     * 该版本文件 MD5
     */
    private String md5;

    /**
     * 版本备注（可选）
     */
    private String remark;

    /**
     * 上传者 ID
     */
    private Long createdBy;

    /**
     * 版本创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime created;
}
