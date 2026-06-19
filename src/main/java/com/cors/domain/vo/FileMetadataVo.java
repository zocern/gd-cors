package com.cors.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileMetadataVo {

    private Long id;

    private String name;

    private Long parentId;

    private String parentName;

    private Boolean folder;

    private Long size;

    private Boolean association;

    /** 当前激活的版本号，文件夹为 null */
    private Integer currentVersion;

    /** 总版本数，文件夹为 null */
    private Integer versionCount;

    private Long createdBy;

    private Long updatedBy;

    private LocalDateTime created;

    private LocalDateTime updated;
}
