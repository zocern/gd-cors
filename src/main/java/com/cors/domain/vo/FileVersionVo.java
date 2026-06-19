package com.cors.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件版本 VO，返回给前端
 */
@Data
public class FileVersionVo {

    /**
     * 版本记录ID
     */
    private Long id;

    /**
     * 关联的文件ID
     */
    private Long fileId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 该版本文件大小（字节）
     */
    private Long size;

    /**
     * 该版本文件 MD5
     */
    private String md5;

    /**
     * 版本备注
     */
    private String remark;

    /**
     * 上传者 ID
     */
    private Long createdBy;

    /**
     * 版本创建时间
     */
    private LocalDateTime created;

    /**
     * 是否为当前激活版本（由 Service 层填充）
     */
    private Boolean current;

    /**
     * 该版本的向量化状态（由 Service 层填充，可为 null 表示未知）
     */
    private String vectorStatus;
}
