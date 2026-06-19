package com.cors.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签响应 VO
 */
@Data
public class TagVo {

    /**
     * 标签ID
     */
    private Long id;

    /**
     * 所属项目标识
     */
    private String project;

    /**
     * 标签名称
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
    private LocalDateTime created;

    /**
     * 更新时间
     */
    private LocalDateTime updated;
}
