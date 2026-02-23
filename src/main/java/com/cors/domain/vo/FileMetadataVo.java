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

    private Long createdBy;

    private Long updatedBy;

    private LocalDateTime created;

    private LocalDateTime updated;
}
