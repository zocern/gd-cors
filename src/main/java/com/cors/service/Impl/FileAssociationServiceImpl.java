package com.cors.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.dto.FileAssociationDto;
import com.cors.domain.entity.FileAssociation;
import com.cors.domain.entity.FileMetadata;
import com.cors.domain.vo.FileAssociationVo;
import com.cors.exception.BadRequestException;
import com.cors.exception.NotFoundException;
import com.cors.lock.HierarchicalLockHelper;
import com.cors.mapper.FileAssociationMapper;
import com.cors.mapper.FileMetadataMapper;
import com.cors.service.FileAssociationService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileAssociationServiceImpl extends ServiceImpl<FileAssociationMapper, FileAssociation> implements FileAssociationService {

    private final HierarchicalLockHelper hierarchicalLockHelper;
    private final FileAssociationMapper fileAssociationMapper;
    private final FileMetadataMapper fileMetadataMapper;

    // 初始化后再注入、防止循环依赖
    @Lazy
    @Autowired
    private FileAssociationServiceImpl self;

    @Override
    public Long createFileAssociation(FileAssociationDto fileAssociationDto) {
        if (fileAssociationDto == null) {
            throw new BadRequestException("文件信息不能为空");
        }
        Long fileMetadataId = fileAssociationDto.getFileMetadataId();
        RReadWriteLock fileLock = hierarchicalLockHelper.getReadWriteLock(fileMetadataId);
        RLock lock = hierarchicalLockHelper.lockWrite(fileLock);
        try {
            return self.createFileAssociationWithTransaction(fileAssociationDto, fileMetadataId);
        } finally {
            hierarchicalLockHelper.unlockAll(lock);
        }
    }

    @Transactional
    public Long createFileAssociationWithTransaction(FileAssociationDto fileAssociationDto, Long fileMetadataId) {
        FileMetadata fileMetadata = fileMetadataMapper.selectById(fileMetadataId);
        if (fileMetadata == null) {
            throw new NotFoundException("文件不存在");
        }
        if (Boolean.TRUE.equals(fileMetadata.getAssociation())) {
            throw new BadRequestException("文件关联信息已存在");
        }
        FileAssociation fileAssociation = new FileAssociation();
        BeanUtil.copyProperties(fileAssociationDto, fileAssociation);
        fileMetadata.setAssociation(true);
        fileMetadataMapper.updateById(fileMetadata);
        fileAssociationMapper.insert(fileAssociation);
        return fileAssociation.getFileMetadataId();
    }


    @Override
    public void updateFileAssociation(FileAssociationDto fileAssociationDto) {
        if (fileAssociationDto == null) {
            throw new BadRequestException("文件信息不能为空");
        }
        FileAssociation fileAssociation = fileAssociationMapper.selectOne(new LambdaQueryWrapper<FileAssociation>()
                .eq(FileAssociation::getFileMetadataId, fileAssociationDto.getFileMetadataId()));
        if (fileAssociation == null) {
            throw new NotFoundException("文件不存在");
        }
        BeanUtil.copyProperties(fileAssociationDto, fileAssociation, CopyOptions.create().ignoreNullValue());
        RReadWriteLock fileLock = hierarchicalLockHelper.getReadWriteLock(fileAssociation.getFileMetadataId());
        RLock lock = hierarchicalLockHelper.lockWrite(fileLock);
        try {
            fileAssociationMapper.updateById(fileAssociation);
        } finally {
            hierarchicalLockHelper.unlockAll(lock);
        }
    }

    @Override
    public FileAssociationVo getFileAssociation(Long id) {
        if (id == null) {
            throw new BadRequestException("文件信息ID不能为空");
        }
        FileAssociation fileAssociation = fileAssociationMapper.selectOne(
                new LambdaQueryWrapper<FileAssociation>()
                        .eq(FileAssociation::getFileMetadataId, id)
        );
        FileAssociationVo fileAssociationVo = new FileAssociationVo();
        BeanUtil.copyProperties(fileAssociation, fileAssociationVo);
        if (fileAssociation == null) {
            throw new NotFoundException("文件信息不存在");
        }
        return fileAssociationVo;
    }
}
