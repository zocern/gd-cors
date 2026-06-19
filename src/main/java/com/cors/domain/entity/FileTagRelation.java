package com.cors.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件标签关联实体类，对应数据库表 file_tag_relation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_tag_relation")
public class FileTagRelation {

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的 file_metadata.id（文件或文件夹）
     */
    private Long fileId;

    /**
     * 关联的 tags.id
     */
    private Long tagId;

    /**
     * 绑定时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime created;
}
