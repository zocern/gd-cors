package com.cors.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 文件绑定/解绑标签请求 DTO
 */
@Data
public class FileTagDto {

    /**
     * 标签ID列表（至少一个）
     */
    @NotEmpty(message = "标签ID列表不能为空")
    private List<Long> tagIds;
}
