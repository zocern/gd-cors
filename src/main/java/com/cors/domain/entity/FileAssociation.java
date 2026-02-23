package com.cors.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("file_association")
public class FileAssociation {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 相应的文件 ID
     */
    private Long fileMetadataId;

    /**
     * 项目名称
     */
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
