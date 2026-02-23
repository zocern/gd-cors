package com.cors.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FileAssociationDto {

    /**
     * 相应的文件 ID
     */
    @NotNull(message = "文件ID不能为空")
    private Long fileMetadataId;

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    /**
     * 项目创建日期
     */
    private LocalDate projectStartDate;

    /**
     * 项目工期（天）
     */
    private Integer projectDuration;

    /**
     * 项目负责人
     */
    private String projectManager;

    /**
     * 项目第二负责人
     */
    private String projectManagerSecond;

    /**
     * 项目实施位置
     */
    private String projectLocation;

    /**
     * 项目乙方单位
     */
    private String projectPartner;
}