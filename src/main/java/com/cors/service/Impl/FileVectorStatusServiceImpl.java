package com.cors.service.Impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.entity.FileVectorStatus;
import com.cors.domain.vo.FileVectorStatusVo;
import com.cors.enums.FileVectorStatusType;
import com.cors.mapper.FileVectorStatusMapper;
import com.cors.service.FileVectorStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileVectorStatusServiceImpl extends ServiceImpl<FileVectorStatusMapper, FileVectorStatus> implements FileVectorStatusService {

    private final FileVectorStatusMapper fileVectorStatusMapper;

    @Override
    public PageDTO<FileVectorStatusVo> getFileVectorStatusByPage(FileVectorStatusType status,
                                                                 String keyword,
                                                                 LocalDateTime start,
                                                                 LocalDateTime end,
                                                                 Integer pageNum,
                                                                 Integer pageSize) {

        IPage<FileVectorStatusVo> page = fileVectorStatusMapper.selectStatusWithFile(
                new Page<>(pageNum, pageSize),
                status,
                keyword,
                start,
                end
        );

        // 获取分页记录
        List<FileVectorStatusVo> vectorFileStatuses = page.getRecords();

        // 如果没有记录，返回空的分页结果
        if (vectorFileStatuses.isEmpty()) {
            return new PageDTO<>(pageNum, pageSize, page.getTotal());
        }

        // 用 PageDTO 包装
        PageDTO<FileVectorStatusVo> pageDTO = new PageDTO<>(pageNum, pageSize, page.getTotal());
        pageDTO.setRecords(vectorFileStatuses);
        return pageDTO;
    }
}
