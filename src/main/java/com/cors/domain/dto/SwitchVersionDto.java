package com.cors.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 切换文件版本请求 DTO
 */
@Data
public class SwitchVersionDto {

    /**
     * 目标版本号（从1开始）
     */
    @NotNull(message = "目标版本号不能为空")
    @Min(value = 1, message = "版本号最小为1")
    private Integer version;
}
