package com.cors.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.entity.FileVectorStatus;
import com.cors.domain.vo.FileVectorStatusVo;
import com.cors.enums.FileVectorStatusType;

import java.time.LocalDateTime;

public interface FileVectorStatusService extends IService<FileVectorStatus> {

    /**
     * 获取文件向量状态列表
     */
    PageDTO<FileVectorStatusVo> getFileVectorStatusByPage(FileVectorStatusType status,
                                                          String keyword,
                                                          LocalDateTime start,
                                                          LocalDateTime end,
                                                          Integer pageNum,
                                                          Integer pageSize);
}
