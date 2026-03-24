package com.cors.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cors.domain.entity.FileMetadata;
import com.cors.mapper.FileMetadataMapper;
import com.cors.service.FileMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * 本地文件夹入库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFolderIngestionService {

    private final FileMetadataMapper fileMetadataMapper;

    private final FileMetadataService fileMetadataService;

    /**
     * 导入本地文件夹
     *
     * @param localPath 物理磁盘上的路径，例如 "D:/my_data" 或 "/usr/local/data"
     * @param parentId  挂载在系统的哪个父级目录下（NULL表示根目录）
     */
    public void ingestLocalDirectory(String localPath, Long parentId) {
        File rootFile = new File(localPath);
        if (!rootFile.exists()) {
            log.error("本地路径不存在: {}", localPath);
            throw new RuntimeException("本地路径不存在: " + localPath);
        }
        
        log.info("开始处理本地目录入库: {}", localPath);
        processNode(rootFile, parentId);
        log.info("本地目录入库流程结束！");
    }

    /**
     * 递归处理节点
     */
    private void processNode(File node, Long parentId) {
        if (node.isDirectory()) {
            // 1. 处理文件夹：直接入库
            Long currentFolderId = saveFolderToDatabase(node, parentId);
            
            // 2. 获取子节点并继续递归
            File[] children = node.listFiles();
            if (children != null) {
                // 将文件夹排在前面，然后再按名称排序，保证入库顺序的美观性
                Arrays.sort(children, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareTo(f2.getName());
                });

                for (File child : children) {
                    processNode(child, currentFolderId); 
                }
            }
        } else if (node.isFile()) {
            // 3. 处理文件：适配为 MultipartFile 并复用现有逻辑
            try {
                MultipartFile multipartFile = new LocalFileAdapter(node);
                // 调用你现有的上传接口 (含MinIO、入库、发MQ向量化等所有逻辑)
                Thread.sleep(10000);
                fileMetadataService.uploadFile(multipartFile, parentId);
                log.info("文件处理成功: {}", node.getAbsolutePath());
            } catch (Exception e) {
                // 捕获异常，防止某一个文件（如重名、格式错误）导致整个目录遍历中断
                log.error("文件处理失败，路径: {}, 原因: {}", node.getAbsolutePath(), e.getMessage(), e);
            }
        }
    }

    /**
     * 将文件夹信息单独保存到数据库
     */
    private Long saveFolderToDatabase(File folder, Long parentId) {
        long folderId = IdWorker.getId(); 
        
        // 注意：如果是定时任务或系统启动时触发，UserContextUtil.getUserId() 可能为空。
        // 这里建议写死一个系统管理员ID，或者从上下文中安全获取。
        Long userId = 1L; 

        FileMetadata folderMetadata = FileMetadata.builder()
                .id(folderId)
                .name(folder.getName())
                .parentId(parentId)
                .folder(true)      // 标记为文件夹
                .size(0L)          // 文件夹大小设为0
                .storageKey(null)  // 文件夹没有存储键
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        fileMetadataMapper.insert(folderMetadata);
        log.info("文件夹入库成功: {} (ID: {})", folder.getName(), folderId);
        
        return folderId;
    }

    private static class LocalFileAdapter implements MultipartFile {

        private final File file;

        public LocalFileAdapter(File file) {
            this.file = file;
        }

        @NonNull
        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public String getOriginalFilename() {
            return file.getName();
        }

        @Override
        public String getContentType() {
            try {
                String type = Files.probeContentType(file.toPath());
                return type != null ? type : "application/octet-stream";
            } catch (IOException e) {
                return "application/octet-stream";
            }
        }

        @Override
        public boolean isEmpty() {
            return file.length() == 0;
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @NotNull
        @Override
        public byte[] getBytes() throws IOException {
            try (InputStream is = new FileInputStream(file);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                }
                return bos.toByteArray();
            }
        }

        @NonNull
        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}