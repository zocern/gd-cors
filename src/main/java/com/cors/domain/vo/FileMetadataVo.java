package com.cors.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 绑定的标签列表（按需填充，非所有接口都返回） */
    private List<TagVo> tags;

    private Long createdBy;

    private Long updatedBy;

    private LocalDateTime created;

    private LocalDateTime updated;
}
