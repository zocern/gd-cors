package com.cors.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标签实体类，对应数据库表 tags
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tags")
public class Tag {

    /**
     * 标签ID，主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属项目标识（同项目内标签名唯一）
     */
    private String project;

    /**
     * 标签名称（同项目内唯一）
     */
    private String name;

    /**
     * 标签颜色（如 #FF5733）
     */
    private String color;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 更新者ID
     */
    private Long updatedBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime created;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updated;
}
