package com.cors.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新标签请求 DTO
 */
@Data
public class TagDto {

    /**
     * 所属项目标识（必填，同项目内标签名唯一）
     */
    @NotBlank(message = "项目标识不能为空")
    @Size(max = 100, message = "项目标识不能超过100个字符")
    private String project;

    /**
     * 标签名称（必填，同项目内唯一）
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称不能超过100个字符")
    private String name;

    /**
     * 标签颜色（如 #FF5733），可选
     */
    @Size(max = 20, message = "标签颜色值不能超过20个字符")
    private String color;

    /**
     * 标签描述，可选
     */
    @Size(max = 500, message = "标签描述不能超过500个字符")
    private String description;
}
