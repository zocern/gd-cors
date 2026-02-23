package com.cors.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.dto.FileAssociationDto;
import com.cors.domain.entity.FileAssociation;
import com.cors.domain.vo.FileAssociationVo;

public interface FileAssociationService extends IService<FileAssociation> {
    // 文件结点
    Long createFileAssociation(FileAssociationDto fileAssociationDto);

    // 文件结点
    void updateFileAssociation(FileAssociationDto fileAssociationDto);

    FileAssociationVo getFileAssociation(Long id);
}
