package com.cors.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.cors.domain.ResponseResult;
import com.cors.domain.StorageUsage;
import com.cors.domain.dto.FileAssociationDto;
import com.cors.domain.dto.FolderDto;
import com.cors.domain.dto.MoveFileDto;
import com.cors.domain.dto.RenameDto;
import com.cors.domain.vo.FileAssociationVo;
import com.cors.domain.vo.FileMetadataVo;
import com.cors.domain.vo.FileVectorStatusVo;
import com.cors.enums.FileVectorStatusType;
import com.cors.service.FileAssociationService;
import com.cors.service.FileMetadataService;
import com.cors.service.FileVectorStatusService;
import com.cors.util.MinIoUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final MinIoUtil minIoUtil;
    private final FileMetadataService fileMetadataService;
    private final FileAssociationService fileAssociationService;
    private final FileVectorStatusService fileVectorStatusService;

    /**
     * 查询文件信息
     */
    @GetMapping("/{id}/info")
    public ResponseResult<FileAssociationVo> getFileAssociation(@PathVariable Long id) {
        return ResponseResult.success(fileAssociationService.getFileAssociation(id));
    }

    @GetMapping("/{id}")
    public ResponseResult<FileMetadataVo> getFile(@PathVariable @NotNull(message = "文件ID不能为空") Long id) {
        return ResponseResult.success(fileMetadataService.getFileById(id));
    }

    @GetMapping
    public ResponseResult<List<FileMetadataVo>> listFiles(@RequestParam(required = false) Long id) {
        return ResponseResult.success(fileMetadataService.getFileListById(id));
    }

    @GetMapping("/{id}/content")
    public void downloadFile(HttpServletResponse response,
                             @PathVariable("id") @NotNull(message = "文件ID不能为空") Long id) {
        fileMetadataService.DownloadFile(response, id);
    }

    /**
     * 文件上传处理
     *
     * @param file     上传的文件 (来自表单的 'file' 字段) Content-Type: multipart/form-data
     * @param parentId 文件的父目录ID (可选, null表示根目录)
     * @return 包含新文件元数据的 ResponseEntity
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Void> uploadFile(@RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
                                           @RequestParam(value = "parent-id", required = false) Long parentId) {
        fileMetadataService.uploadFile(file, parentId);
        return ResponseResult.success();
    }

    @PostMapping("/update")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Void> updateFile(@RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
                                           @RequestParam(value = "id", required = false) Long id) {
        fileMetadataService.updateFile(file, id);
        return ResponseResult.success();
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/folder")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Void> createFolder(@Valid @RequestBody FolderDto folderDto) {
        fileMetadataService.createFolder(folderDto);
        return ResponseResult.success();
    }

    /**
     * 文件搜索
     */
    @GetMapping("/search")
    public ResponseResult<PageDTO<FileMetadataVo>> searchFiles(
            @RequestParam @NotBlank(message = "搜索关键字不能为空") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        // 分页查询文件列表
        PageDTO<FileMetadataVo> page = fileMetadataService.getFileByPage(keyword, pageNum, pageSize);
        return ResponseResult.success(page);
    }

    /**
     * 文件信息登记
     */
    @PostMapping("/info")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Long> createFileAssociation(@Valid @RequestBody FileAssociationDto fileAssociationDto) {
        return ResponseResult.success(fileAssociationService.createFileAssociation(fileAssociationDto));
    }

    /**
     * 文件信息更改
     */
    @PutMapping("/info")
    public ResponseResult<Void> updateFileAssociation(@Valid @RequestBody FileAssociationDto fileAssociationDto) {
        fileAssociationService.updateFileAssociation(fileAssociationDto);
        return ResponseResult.success();
    }


    @PutMapping("/{id}/rename")
    public ResponseResult<Void> renameFile(@PathVariable @NotNull Long id,
                                           @Valid @RequestBody RenameDto renameDto) {
        fileMetadataService.rename(id, renameDto.getNewName());
        return ResponseResult.success();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseResult<Void> deleteFile(@PathVariable @NotNull(message = "文件ID不能为空") Long id) {
        fileMetadataService.delete(id);
        return ResponseResult.success();
    }

    @PutMapping("/{id}/move")
    public ResponseResult<Void> moveFile(@PathVariable @NotNull Long id,
                                         @Valid @RequestBody MoveFileDto moveDto) {
        fileMetadataService.move(id, moveDto.getNewParentId());
        return ResponseResult.success();
    }

    @GetMapping("/storage/usage")
    public ResponseResult<StorageUsage> getStorageUsage() {
        return ResponseResult.success(minIoUtil.getStorageUsage());
    }

    @GetMapping("/vector/status")
    public ResponseResult<PageDTO<FileVectorStatusVo>> getFileVectorStatus(@RequestParam(value = "status", required = false) FileVectorStatusType status,
                                                                           @RequestParam(value = "keyword", required = false) String keyword,
                                                                           @RequestParam(value = "start", required = false)
                                                                           @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime start,
                                                                           @RequestParam(value = "end", required = false)
                                                                           @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime end,
                                                                           @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ResponseResult.success(fileVectorStatusService.getFileVectorStatusByPage(status, keyword, start, end, pageNum, pageSize));
    }

    @PostMapping("/vector/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseResult<String> fileVectorRetry(@RequestParam @NotBlank(message = "storageKey 不能为空") String storageKey) {
        fileMetadataService.fileVectorRetry(storageKey);
        return ResponseResult.success();
    }
}
