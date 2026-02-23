package com.cors.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileChunkDto {
    @NotBlank(message = "MD5不能为空")
    private String md5;

    private String fileName;

    private Long parentId;

    @NotNull(message = "分片索引不能为空")
    private Integer chunkIndex;

    @NotNull(message = "总分片数不能为空")
    private Integer totalChunks;

    private Long totalSize;

    private MultipartFile file; // 上传接口必填，合并接口可为空
}