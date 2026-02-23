package com.cors.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.dto.FileAssociationDto;
import com.cors.domain.dto.FolderDto;
import com.cors.domain.entity.FileMetadata;
import com.cors.domain.vo.FileAssociationVo;
import com.cors.domain.vo.FileMetadataVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface FileMetadataService extends IService<FileMetadata> {

    FileMetadataVo getFileById(Long id);

    List<FileMetadataVo> getFileListById(Long id);

    void DownloadFile(HttpServletResponse response, Long id);

    void uploadFile(MultipartFile file, Long parentId);

    void updateFile(MultipartFile file, Long id);

//    FileCheckResult checkFile(String md5, Long parentId, String fileName);
//
//    void uploadChunk(FileChunkDto chunkDTO);
//
//    void mergeFile(String md5, String fileName, Long parentId, Long totalSize, Integer totalChunks);

    void createFolder(FolderDto folderDto);

//    Long createFileAssociation(FileAssociationDto fileAssociationDto);
//
//    void updateFileAssociation(FileAssociationDto fileAssociationDto);
//
//    FileAssociationVo getFileAssociation(Long id);

    void rename(Long id, String newName);

    void delete(Long id);

    void move(Long id, Long newParentId);

    PageDTO<FileMetadataVo> getFileByPage(String keyword, Integer pageNum, Integer pageSize);

    void fileVectorRetry(String storageKey);
}
