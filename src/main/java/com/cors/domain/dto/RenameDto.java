package com.cors.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameDto {

    @NotBlank(message = "新文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过255个字符")
    private String newName;
}

