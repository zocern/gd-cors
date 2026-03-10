package com.cors.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.cors.domain.dto.FolderDto;
import com.cors.domain.entity.FileMetadata;
import com.cors.domain.entity.FileAssociation;
import com.cors.domain.entity.FileVectorStatus;
import com.cors.domain.vo.FileMetadataVo;
import com.cors.enums.FileVectorStatusType;
import com.cors.exception.BadRequestException;
import com.cors.exception.FileStorageException;
import com.cors.lock.HierarchicalLockHelper;
import com.cors.mapper.FileMetadataMapper;
import com.cors.mapper.FileVectorStatusMapper;
import com.cors.mq.message.FileDeleteMessage;
import com.cors.mq.message.FileVectorMessage;
import com.cors.mq.producer.FileStorageDeleteProducer;
import com.cors.mq.producer.FileVectorDeleteProducer;
import com.cors.mq.producer.FileVectorProducer;
import com.cors.service.FileAssociationService;
import com.cors.service.FileMetadataService;
import com.cors.util.MinIoUtil;
import com.cors.util.StorageKeyGenerator;
import com.cors.util.UserContextUtil;
import io.minio.ObjectWriteResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.cors.exception.NotFoundException;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataServiceImpl extends ServiceImpl<FileMetadataMapper, FileMetadata> implements FileMetadataService {
    // 文件名非法字符正则表达式
    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
    // 文件名最大长度（对应数据库varchar(255)限制）
    private static final Integer MAX_FILENAME_LENGTH = 255;

    private final FileAssociationService fileAssociationService;
    private final HierarchicalLockHelper hierarchicalLockHelper;
    private final FileMetadataMapper fileMetadataMapper;
    private final FileVectorStatusMapper fileVectorStatusMapper;
    private final FileStorageDeleteProducer fileStorageDeleteProducer;
    private final FileVectorDeleteProducer fileVectorDeleteProducer;
    private final FileVectorProducer fileVectorProducer;
    private final MinIoUtil minIoUtil;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;


    // 初始化后再注入、防止循环依赖
    @Lazy
    @Autowired
    private FileMetadataServiceImpl self;

    @Override
    public FileMetadataVo getFileById(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("文件ID无效");
        }
        FileMetadata fileMetadata = this.getById(id);
        if (fileMetadata == null) {
            throw new NotFoundException("文件或文件夹不存在, id: " + id);
        }
        FileMetadataVo fileMetadataVo = new FileMetadataVo();
        BeanUtil.copyProperties(fileMetadata, fileMetadataVo);
        return fileMetadataVo;
    }

    @Override
    public List<FileMetadataVo> getFileListById(Long id) {
        if (id != null) {
            if (id <= 0) {
                throw new BadRequestException("文件夹ID无效");
            }
            FileMetadata fileMetadata = this.getById(id);
            if (fileMetadata == null) {
                throw new NotFoundException(String.format("父文件夹不存在, id: %d", id));
            }
            if (!Boolean.TRUE.equals(fileMetadata.getFolder())) {
                throw new BadRequestException(String.format("指定的ID不是一个文件夹, id: %d", id));
            }
        }
        LambdaQueryWrapper<FileMetadata> wrapper = new LambdaQueryWrapper<>();
        if (id == null) {
            wrapper.isNull(FileMetadata::getParentId);
        } else {
            wrapper.eq(FileMetadata::getParentId, id);
        }
        List<FileMetadata> fileMetadata = this.list(wrapper);
        return BeanUtil.copyToList(fileMetadata, FileMetadataVo.class);
    }

    @Override // 当前节点
    public void DownloadFile(HttpServletResponse response, Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("文件ID无效");
        }
        if (response == null) {
            throw new BadRequestException("响应对象不能为空");
        }

        FileMetadata fileMetadata = this.getById(id);
        if (fileMetadata == null) {
            throw new NotFoundException(String.format("文件记录不存在, id: %d", id));
        }
        if (Boolean.TRUE.equals(fileMetadata.getFolder())) {
            throw new BadRequestException(String.format("不能下载文件夹, id: %d", id));
        }
        String storageKey = fileMetadata.getStorageKey();
        if (!StringUtils.hasText(fileMetadata.getStorageKey())) {
            throw new FileStorageException(String.format("文件存储ID无效, id: %d", id));
        }
        RReadWriteLock fileLock = hierarchicalLockHelper.getReadWriteLock(id);
        RLock lock = hierarchicalLockHelper.lockRead(fileLock);
        try {
            response.reset();
            response.setCharacterEncoding("UTF-8");
            try {
                response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileMetadata.getName(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("文件名编码失败: {}", fileMetadata.getName(), e);
                response.addHeader("Content-Disposition", "attachment;filename=file.dat");
            }
            // 设置为通用的二进制流类型，强制下载
            response.setContentType("application/octet-stream");
            response.setContentLengthLong(fileMetadata.getSize());
            try (InputStream is = minIoUtil.getObject(storageKey)) {
                // 使用 Java 9+ 的 transferTo 方法，自动处理缓冲区和读写
                is.transferTo(response.getOutputStream());
                // 确保响应流被刷新
                response.getOutputStream().flush();
            } catch (Exception e) {
                log.error("文件下载失败, storageKey: {}", storageKey, e);
                throw new FileStorageException(String.format("文件下载失败: %s", e.getMessage()), e);
            }
        } finally {
            hierarchicalLockHelper.unlockAll(lock);
        }
    }

    // 1. 先执行 I/O（上传） -> 2. 再获取锁 -> 3. 最后执行事务（写数据库）（失败回滚产生的孤儿数据节点可以定期清理）
//    @Override // 目录结点
//    public void uploadFile(MultipartFile file, Long parentId) throws NotFoundException {
//
//        if (file == null) {
//            throw new BadRequestException("文件对象不能为空");
//        }
//        long fileSize = file.getSize();
//        if (fileSize <= 0) {
//            throw new BadRequestException("文件大小无效");
//        }
//        if (fileSize > maxFileSize.toBytes()) {
//            throw new BadRequestException(String.format(
//                    "文件大小超过限制，最大允许 %.2fMB", (double) maxFileSize.toBytes() / (1024 * 1024)
//            ));
//        }
//        String originalFilename = file.getOriginalFilename();
//        if (!StringUtils.hasText(originalFilename)) {
//            throw new BadRequestException("文件名不能为空");
//        }
//        validateFileName(originalFilename);
//        long id = IdWorker.getId();
//        String uuid = UUID.randomUUID().toString();
//        String extension = StringUtils.getFilenameExtension(originalFilename);
//        String objectName;
//        if (StringUtils.hasText(extension)) {
//            objectName = uuid + "." + extension;
//        } else {
//            objectName = uuid;
//        }
//        // 当前月份目录
//        String monthFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
//        // 拼接成最终对象路径
//        String storageKey = monthFolder + "/" + objectName;
//        try {
//            ObjectWriteResponse response = minIoUtil.uploadFile(file, storageKey, file.getContentType());
//            log.info("文件成功上传到 MinIO. Object: {}, ETag: {}", response.object(), response.etag());
//        } catch (Exception e) {
//            log.error("文件上传到 MinIO 失败. ObjectName: {}", storageKey, e);
//            throw new FileStorageException(String.format("文件存储服务异常: %s", e.getMessage()), e);
//        }
//        RReadWriteLock parentLock = hierarchicalLockHelper.getReadWriteLock(parentId);
//        RLock lock = hierarchicalLockHelper.lockWrite(parentLock);
//        try {
//            self.saveFileRecordWithTransaction(parentId, id, originalFilename, fileSize, storageKey);
//            try {
//                fileVectorProducer.sendFileVectorDeleteMessage(storageKey);
//            } catch (Exception e) {
//                log.error("消息队列发送失败，文件未进入向量化流程: {}", storageKey, e);
//                // 视业务需求决定是否抛出异常，通常建议吞掉异常，后续通过定时任务补偿
//            }
//        } catch (Exception e) {
//            log.warn("数据库记录保存失败，尝试删除 MinIO 中的孤儿文件: {}", storageKey, e);
//            try {
//                fileDeleteProducer.sendFileVectorDeleteMessage(List.of(storageKey));
//                log.info("已发送孤儿文件清理消息至 MQ: {}", storageKey);
//            } catch (Exception mqEx) {
//                log.error("孤儿文件回滚失败 (MQ发送异常)，文件残留 MinIO: {}", storageKey, mqEx);
//            }
//
//            if (e instanceof FileStorageException
//                    || e instanceof BadRequestException
//                    || e instanceof NotFoundException) {
//                throw e;
//            }
//            throw new FileStorageException(String.format("保存文件记录时出错: %s", e.getMessage()), e);
//        } finally {
//            hierarchicalLockHelper.unlockAll(lock);
//        }
//    }

    @Override
    public void uploadFile(MultipartFile file, Long parentId) {
        // 前置校验 (Fail Fast)
        validateFile(file);

        // 准备元数据
        String originalFilename = file.getOriginalFilename();
        long fileSize = file.getSize();
        long id = IdWorker.getId();
        String storageKey = StorageKeyGenerator.generate(originalFilename);

        // 上传文件到 MinIO
        uploadToStorage(file, storageKey);

        // 变更数据库 (加锁，核心临界区)
        boolean saveSuccess = false;
        RReadWriteLock parentLock = hierarchicalLockHelper.getReadWriteLock(parentId);
        RLock lock = hierarchicalLockHelper.lockWrite(parentLock);
        try {
            // 使用 self 代理调用以确保事务生效
            self.saveFileRecordWithTransaction(parentId, id, originalFilename, fileSize, storageKey);
            saveSuccess = true;
        } finally {
            if (saveSuccess) {
                // 发送向量化消息
                fileVectorProducer.sendFileVectorMessage(new FileVectorMessage(storageKey));
            } else {
                log.error("数据库记录保存失败，准备回滚 MinIO 文件: {}", storageKey);
                fileStorageDeleteProducer.sendFileStorageDeleteMessage(new FileDeleteMessage(storageKey, 0));
            }
            hierarchicalLockHelper.unlockAll(lock);
        }
    }

    @Transactional
    public void saveFileRecordWithTransaction(Long parentId,
                                              long id,
                                              String originalFilename,
                                              long fileSize,
                                              String storageKey) {
        Long userId = UserContextUtil.getUserId();
        String parentName = null;
        if (parentId != null) {
            if (parentId <= 0) {
                throw new BadRequestException("父文件夹ID无效");
            }
            FileMetadata parentFileMetadata = this.getById(parentId);
            if (parentFileMetadata == null) {
                throw new NotFoundException(String.format("父文件夹不存在 (可能在上传时被删除), ID: %d", parentId));
            }
            if (!Boolean.TRUE.equals(parentFileMetadata.getFolder())) {
                throw new BadRequestException(String.format("指定的父ID不是一个文件夹, ID: %d", parentId));
            }
            parentName = parentFileMetadata.getName();
        }
        checkDuplicateName(parentId, originalFilename, null);
        FileMetadata fileMetadata = FileMetadata.builder()
                .id(id)
                .name(originalFilename)
                .parentId(parentId)
                .parentName(parentName)
                .folder(false)
                .createdBy(userId)
                .updatedBy(userId)
                .size(fileSize)
                .storageKey(storageKey) // 存储 MinIO 的对象名称
                .build();
        if (!this.save(fileMetadata)) {
            throw new FileStorageException("保存文件记录到数据库失败");
        }
        try {
            FileVectorStatus status = new FileVectorStatus();
            status.setStorageKey(storageKey);
            status.setStatus(FileVectorStatusType.PENDING);
            fileVectorStatusMapper.insert(status);
        } catch (DuplicateKeyException e) {
            log.debug("向量化状态已存在，storageKey={}", storageKey);
        } catch (Exception e) {
            throw new FileStorageException("保存向量化状态到数据库失败", e);
        }
    }


    @Override
    public void updateFile(MultipartFile file, Long id) {
        // 前置校验
        validateFile(file);

        // 生成新的 StorageKey (即使文件名没变，也建议用新的 UUID 防止浏览器缓存或覆盖问题)
        String newStorageKey = StorageKeyGenerator.generate(file.getOriginalFilename());
        long newFileSize = file.getSize();

        // 上传新文件到 MinIO (不加锁，纯 IO 操作)
        // 如果这里失败，抛出异常，流程结束，对现有数据无影响
        uploadToStorage(file, newStorageKey);


        String oldStorageKey = null;
        boolean updateSuccess = false;

        // 数据库原子更新 (加锁)
        // 获取文件写锁，防止并发修改同一文件
        RReadWriteLock fileLock = hierarchicalLockHelper.getReadWriteLock(id);
        RLock lock = hierarchicalLockHelper.lockWrite(fileLock);
        try {
            // 查旧数据 (用于后续清理)
            FileMetadata fileMetadata = this.getById(id);
            if (fileMetadata == null) {
                throw new NotFoundException("未找到待更新的文件记录");
            }
            oldStorageKey = fileMetadata.getStorageKey();
            // 更新字段：大小、StorageKey、更新时间等
            self.updateFileRecordWithTransaction(id, newFileSize, newStorageKey);
            updateSuccess = true;
        } finally {
            if (updateSuccess) {
                // 清理旧文件、旧向量 + 新向量化
                handlePostUpdateSuccess(oldStorageKey, newStorageKey);
            } else {
                // 清理刚才上传的新文件
                log.error("文件更新数据库失败，准备回滚新上传的文件: {}", newStorageKey);
                fileStorageDeleteProducer.sendFileStorageDeleteMessage(new FileDeleteMessage(newStorageKey, 0));
            }
            hierarchicalLockHelper.unlockAll(lock);
        }
    }

    @Transactional
    public void updateFileRecordWithTransaction(Long id,
                                                long newSize,
                                                String newStorageKey) {
        Long userId = UserContextUtil.getUserId();

        // 获取旧数据
        FileMetadata oldFileMetadata = this.getById(id);
        if (oldFileMetadata == null) {
            throw new NotFoundException(String.format("未找到待更新的文件记录, ID: %d", id));
        }

        String oldKey = oldFileMetadata.getStorageKey();
        oldFileMetadata.setSize(newSize);
        oldFileMetadata.setStorageKey(newStorageKey);
        oldFileMetadata.setUpdatedBy(userId);

        if (!this.updateById(oldFileMetadata)) {
            throw new FileStorageException("更新文件记录数据库失败");
        }
        try {
            FileVectorStatus status = new FileVectorStatus();
            status.setStorageKey(newStorageKey);
            status.setStatus(FileVectorStatusType.PENDING);
            fileVectorStatusMapper.insert(status);
        } catch (DuplicateKeyException e) {
            log.debug("向量化状态已存在，newStorageKey={}", newStorageKey);
        } catch (Exception e) {
            throw new FileStorageException("保存向量化状态到数据库失败", e);
        }
        log.debug("文件记录更新成功. ID: {}, OldKey: {}, NewKey: {}", id, oldKey, newStorageKey);
    }


//    @Override

    /// / 注意：去掉 @Transactional，因为我们要手动控制锁范围，事务在 saveFileRecordWithTransaction 内部
//    public FileCheckResult checkFile(String md5, Long parentId, String fileName) {
//        // 1. 【秒传检查】查询全局是否存在相同 MD5 的文件
//        // 只要查到一个即可 (LIMIT 1)
//        FileMetadata existFile = fileMetadataMapper.selectOne(new LambdaQueryWrapper<FileMetadata>()
//                .eq(FileMetadata::getMd5, md5)
//                .last("LIMIT 1"));
//
//        // 如果文件库中已经有这个文件了 -> 执行秒传逻辑
//        if (existFile != null) {
//            // 【统一 Redisson 锁】
//            // 既然是在 parentId 下生成新记录，必须加父文件夹写锁，防止重名并发问题
//            RReadWriteLock parentLock = hierarchicalLockHelper.getReadWriteLock(parentId);
//            RLock writeLock = hierarchicalLockHelper.lockWrite(parentLock);
//
//            try {
//                long id = IdWorker.getId();
//
//                // 调用复用的事务方法进行保存
//                // 注意：这里直接复用 existFile 的 storageKey 和 size
//                self.saveFileRecordWithTransaction(parentId, id, fileName, existFile.getSize(), existFile.getStorageKey(), md5);
//
//                // 秒传成功，返回 true
//                return new FileCheckResult(true, null);
//
//            } catch (Exception e) {
//                log.error("秒传保存失败: MD5={}, ParentId={}", md5, parentId, e);
//                throw new FileStorageException("文件秒传失败", e);
//            } finally {
//                // 解锁
//                hierarchicalLockHelper.unlockAll(writeLock);
//            }
//        }
//
//        // 2. 【断点续传检查】如果没命中秒传，查询 Redis
//        String key = CHUNK_KEY_PREFIX + md5;
//
//        // 获取该 Key 下所有已上传的 chunkIndex
//        Set<String> uploadedChunksStr = redisTemplate.opsForSet().members(key);
//
//        List<Integer> uploadedChunks = uploadedChunksStr == null ? new ArrayList<>() :
//                uploadedChunksStr.stream()
//                        .map(Integer::parseInt)
//                        .sorted()
//                        .collect(Collectors.toList());
//
//        return new FileCheckResult(false, uploadedChunks);
//    }
//
//    @Override
//    public void uploadChunk(FileChunkDto chunkDto) {
//        // 【性能提示】分片上传不需要加分布式锁
//        // 原因：多个线程传同一个分片是幂等的（覆盖即可），加锁会严重拖慢上传速度
//
//        if (chunkDto.getFile() == null || chunkDto.getFile().isEmpty()) {
//            throw new BadRequestException("分片数据为空");
//        }
//
//        // 构造临时分片路径：temp/{md5}/{chunkIndex}
//        String tempChunkPath = "temp/" + chunkDto.getMd5() + "/" + chunkDto.getChunkIndex();
//
//        try {
//            // 1. 上传分片到 MinIO
//            minIoUtil.uploadFile(chunkDto.getFile(), tempChunkPath, "application/octet-stream");
//
//            // 2. Redis 记录索引
//            String key = CHUNK_KEY_PREFIX + chunkDto.getMd5();
//            redisTemplate.opsForSet().add(key, chunkDto.getChunkIndex().toString());
//
//            // 3. 续期 (每次上传都刷新过期时间，例如 24小时)
//            redisTemplate.expire(key, 24, TimeUnit.HOURS);
//
//        } catch (Exception e) {
//            log.error("分片上传失败: MD5={}, Index={}", chunkDto.getMd5(), chunkDto.getChunkIndex(), e);
//            // 这里不需要回滚 MinIO，下次重传覆盖即可
//            throw new FileStorageException("分片上传失败", e);
//        }
//    }
//
//    @Override
//    public void mergeFile(String md5, String fileName, Long parentId, Long totalSize, Integer totalChunks) {
//
//        String mergeLockKey = "lock:merge:" + md5;
//        RLock mergeLock = redissonClient.getLock(mergeLockKey);
//
//        // 尝试获取锁，等待0秒，锁定30秒（根据文件大小预估合并时间，或者自动续期）
//        if (!mergeLock.tryLock()) {
//            throw new BadRequestException("文件正在合并中，请勿重复操作");
//        }
//
//        try {
//            // 2. 【双重检查】检查是否已经合并完成 (防止并发请求排队后进入)
//            // 如果数据库已经有这个 MD5 且 parentId 也一样，其实可以视为秒传成功（可选逻辑）
//            // 这里主要检查 Redis 分片数据是否还存在，如果 key 没了说明被别人合并了
//            String chunkKey = CHUNK_KEY_PREFIX + md5;
//            if (!redisTemplate.hasKey(chunkKey)) {
//                throw new BadRequestException("分片数据不存在或已被合并");
//            }
//
//            // 3. 【完整性校验】
//            Long uploadedCount = redisTemplate.opsForSet().size(chunkKey);
//            if (uploadedCount == null || uploadedCount.intValue() != totalChunks) {
//                throw new BadRequestException(String.format("分片缺失，无法合并。已上传:%d, 总需:%d", uploadedCount, totalChunks));
//            }
//
//            // 4. 准备 MinIO 路径
//            String uuid = UUID.randomUUID().toString();
//            String extension = StringUtils.getFilenameExtension(fileName);
//            String objectName = StringUtils.hasText(extension) ? uuid + "." + extension : uuid;
//            String monthFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
//            String storageKey = monthFolder + "/" + objectName;
//
//            List<String> sourceObjects;
//            try {
//                // 5. 获取并排序分片
//                // 假设你的 minIoUtil.listObjectNames 内部已经根据数字索引排好序了
//                // 如果 Util 没排序，这里必须手动 sort，否则合并后的文件是坏的
//                sourceObjects = minIoUtil.listObjectNames("temp/" + md5 + "/");
//
//                // double check: 确保分片数量和 Redis 记录一致
//                if (sourceObjects.size() != totalChunks) {
//                    throw new BadRequestException("MinIO分片文件缺失");
//                }
//
//                // 6. 执行 MinIO 合并
//                log.info("开始合并文件 MD5={}, 目标={}", md5, storageKey);
//                minIoUtil.composeFile(sourceObjects, storageKey);
//                log.info("MinIO合并成功");
//
//            } catch (Exception e) {
//                log.error("文件合并阶段失败: {}", storageKey, e);
//                throw new FileStorageException("文件合并失败", e);
//            }
//
//            // 7. 数据库落库 (保持原来的文件夹锁逻辑)
//            RReadWriteLock parentLock = hierarchicalLockHelper.getReadWriteLock(parentId);
//            RLock writeLock = hierarchicalLockHelper.lockWrite(parentLock);
//            try {
//                long id = IdWorker.getId();
//
//                // 【关键修正】传入 MD5，确保下次能秒传
//                self.saveFileRecordWithTransaction(parentId, id, fileName, totalSize, storageKey, md5);
//
//            } catch (Exception e) {
//                log.warn("数据库落库失败，回滚删除MinIO文件: {}", storageKey);
//                // 回滚：删除刚刚合并好的大文件
//                try {
//                    minIoUtil.removeFiles(Collections.singletonList(storageKey));
//                } catch (Exception ex) {
//                    log.error("回滚删除失败", ex);
//                }
//                throw new FileStorageException("保存文件记录失败", e);
//            } finally {
//                hierarchicalLockHelper.unlockAll(writeLock);
//            }
//
//            // 8. 【清理工作】合并成功且落库成功后，清理临时分片
//            // 建议异步执行，不阻塞前端响应
//            List<String> finalSourceObjects = sourceObjects; // for lambda
//            CompletableFuture.runAsync(() -> {
//                try {
//                    // 删除 Redis 记录
//                    redisTemplate.delete(chunkKey);
//                    // 删除 MinIO 临时分片
//                    minIoUtil.removeFiles(finalSourceObjects);
//                    log.info("清理临时分片成功 MD5={}", md5);
//                } catch (Exception e) {
//                    log.error("清理临时资源失败 MD5={}", md5, e);
//                }
//            });
//
//        } finally {
//            // 释放 MD5 并发锁
//            if (mergeLock.isHeldByCurrentThread()) {
//                mergeLock.unlock();
//            }
//        }
//    }
//
//
//    @Transactional
//    public void saveFileRecordWithTransaction(Long parentId,
//                                              long id,
//                                              String originalFilename,
//                                              long fileSize,
//                                              String storageKey,
//                                              String md5) {
//        Long userId = UserContextUtil.getUserId();
//        if (parentId != null) {
//            if (parentId <= 0) {
//                throw new BadRequestException("父文件夹ID无效");
//            }
//            FileMetadata parent = this.getById(parentId);
//            if (parent == null) {
//                throw new NotFoundException(String.format("父文件夹不存在 (可能在上传时被删除), ID: %d", parentId));
//            }
//            if (!Boolean.TRUE.equals(parent.getFolder())) {
//                throw new BadRequestException(String.format("指定的父ID不是一个文件夹, ID: %d", parentId));
//            }
//        }
//        checkDuplicateName(parentId, originalFilename, null);
//        FileMetadata fileMetadata = FileMetadata.builder()
//                .id(id)
//                .name(originalFilename)
//                .parentId(parentId)
//                .folder(false)
//                .createdBy(userId)
//                .updatedBy(userId)
//                .size(fileSize)
//                .storageKey(storageKey) // 存储 MinIO 的对象名称
//                .md5(md5)
//                .build();
//        if (!this.save(fileMetadata)) {
//            throw new FileStorageException("保存文件记录到数据库失败");
//        }
//    }

    @Override // 目录结点
    public void createFolder(FolderDto folderDto) {
        if (folderDto == null) {
            throw new BadRequestException("文件夹信息不能为空");
        }
        // 验证文件夹名称合法性（包括长度检查，数据库限制为varchar(255)）
        if (folderDto.getName() == null || !StringUtils.hasText(folderDto.getName())) {
            throw new BadRequestException("文件夹名称不能为空");
        }
        validateFileName(folderDto.getName());
        Long parentId = folderDto.getParentId();
        // 获取父目录及祖先锁, 从顶级目录文件夹到父文件夹
        RReadWriteLock parentLock = hierarchicalLockHelper.getReadWriteLock(parentId);
        RLock lock = hierarchicalLockHelper.lockWrite(parentLock);
        try {
            Long userId = UserContextUtil.getUserId();
            String parentName = null;
            if (parentId != null) {
                FileMetadata parentFileMetadata = this.getById(parentId);
                if (parentFileMetadata == null) {
                    throw new NotFoundException(String.format("父文件夹不存在, ID: %d", parentId));
                }
                if (!Boolean.TRUE.equals(parentFileMetadata.getFolder())) {
                    throw new BadRequestException(String.format("指定的父ID不是一个文件夹, ID: %d", parentId));
                }
                parentName = parentFileMetadata.getName();
            }
            checkDuplicateName(parentId, folderDto.getName(), null);
            FileMetadata fileMetadata = FileMetadata.builder()
                    .name(folderDto.getName())
                    .parentId(parentId)
                    .parentName(parentName)
                    .folder(true)
                    .createdBy(userId)
                    .updatedBy(userId)
                    .size(0L)
                    .build();
            if (!this.saveOrUpdate(fileMetadata)) {
                throw new FileStorageException(String.format("文件夹创建失败: %s", folderDto.getName()));
            }
        } finally {
            hierarchicalLockHelper.unlockAll(lock);
        }
    }


    @Override // 当前节点 + 目录结点
    public void rename(Long id, String newName) {
        if (id == null) {
            throw new BadRequestException("文件ID无效");
        }
        if (!StringUtils.hasText(newName)) {
            throw new BadRequestException("新文件名不能为空");
        }
        validateFileName(newName);
        FileMetadata fileMetadata = this.getById(id);
        if (fileMetadata == null) {
            throw new NotFoundException(String.format("文件或文件夹不存在, ID: %s", id));
        }
        if (newName.equals(fileMetadata.getName())) {
            throw new BadRequestException("新文件名与当前文件名相同，无需重命名");
        }
        List<RReadWriteLock> locks = new ArrayList<>();
        locks.add(hierarchicalLockHelper.getReadWriteLock(fileMetadata.getParentId()));
        locks.add(hierarchicalLockHelper.getReadWriteLock(id));
        RLock multiLock = hierarchicalLockHelper.lockAllWrite(locks);
        try {
            self.renameWithTransaction(id, newName, fileMetadata);
        } finally {
            hierarchicalLockHelper.unlockAll(multiLock);
        }
    }

    @Transactional
    public void renameWithTransaction(Long id, String newName, FileMetadata fileMetadata) {
        Long userId = UserContextUtil.getUserId();
        String oldName = fileMetadata.getName();
        newName = newName.trim();
        checkDuplicateName(fileMetadata.getParentId(), newName, id);
        fileMetadata.setName(newName);
        fileMetadata.setUpdatedBy(userId);
        if (!this.updateById(fileMetadata)) {
            throw new FileStorageException(String.format("重命名失败, ID: %s", id));
        }
        LambdaUpdateWrapper<FileMetadata> updateWrapper =
                new LambdaUpdateWrapper<FileMetadata>()
                        .eq(FileMetadata::getParentId, id)
                        .eq(FileMetadata::getParentName, oldName)
                        .set(FileMetadata::getParentName, newName);
        this.update(null, updateWrapper);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new BadRequestException("文件ID无效");
        }
        FileMetadata fileMetadata = this.getById(id);
        if (fileMetadata == null) {
            throw new NotFoundException(String.format("文件或文件夹不存在, ID: %s", id));
        }
        List<RReadWriteLock> descendantLocks = hierarchicalLockHelper.getDescendantReadWriteLocks(id);
        RLock multiLock = hierarchicalLockHelper.lockAllWrite(descendantLocks);
        try {
            self.deleteWithTransaction(fileMetadata);
        } catch (Exception e) {
            if (e instanceof FileStorageException || e instanceof NotFoundException) {
                throw e;
            }
            throw new FileStorageException(String.format("删除时发生未知错误: %s", e.getMessage()), e);
        } finally {
            hierarchicalLockHelper.unlockAll(multiLock);
        }
    }

    @Transactional
    public void deleteWithTransaction(FileMetadata fileMetadata) {

        Set<Long> fileIdsToDelete = new HashSet<>();
        Set<Long> associationIdsToDelete = new HashSet<>();
        List<String> storageKeysToDelete = new ArrayList<>();

        List<FileMetadata> targets;

        if (Boolean.TRUE.equals(fileMetadata.getFolder())) {
            targets = fileMetadataMapper.selectAllDescendants(fileMetadata.getId());
        } else {
            targets = List.of(fileMetadata);
        }

        for (FileMetadata f : targets) {
            fileIdsToDelete.add(f.getId());

            if (!Boolean.TRUE.equals(f.getFolder())
                    && StringUtils.hasText(f.getStorageKey())) {
                storageKeysToDelete.add(f.getStorageKey());
            }
        }

        if (!fileIdsToDelete.isEmpty()) {
            List<FileAssociation> associations =
                    fileAssociationService.list(
                            new LambdaQueryWrapper<FileAssociation>()
                                    .in(FileAssociation::getFileMetadataId, fileIdsToDelete)
                    );

            associationIdsToDelete.addAll(
                    associations.stream()
                            .map(FileAssociation::getId)
                            .toList()
            );
        }

        if (!this.removeByIds(fileIdsToDelete)) {
            throw new FileStorageException("删除文件记录失败");
        }
        if (!associationIdsToDelete.isEmpty() && !fileAssociationService.removeBatchByIds(associationIdsToDelete)) {
            throw new FileStorageException("删除文件关联失败");
        }

        if (!storageKeysToDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            for (String storageKey : storageKeysToDelete) {
                                fileStorageDeleteProducer.sendFileStorageDeleteMessage(new FileDeleteMessage(storageKey, 0));
                                fileVectorDeleteProducer.sendFileVectorDeleteMessage(new FileDeleteMessage(storageKey, 0));
                            }
                        }
                    }
            );
        }
    }


    @Override // 当前节点 + 目标父目录
    public void move(Long id, Long newParentId) {
        if (id == null || id <= 0) {
            throw new BadRequestException("文件ID无效");
        }
        FileMetadata fileMetadata = this.getById(id);
        if (fileMetadata == null) {
            throw new NotFoundException(String.format("文件或文件夹不存在, ID: %s", id));
        }
        Long currentParentId = fileMetadata.getParentId();
        if ((newParentId == null && currentParentId == null) ||
                (newParentId != null && newParentId.equals(currentParentId))) {
            throw new BadRequestException("文件或文件夹已在目标位置，无需移动");
        }
        if (newParentId != null && newParentId.equals(id)) {
            throw new BadRequestException("不能将文件或文件夹移动到自身");
        }

        // 先加目标父文件夹锁
        List<RReadWriteLock> locks = new ArrayList<>();
        locks.add(hierarchicalLockHelper.getReadWriteLock(newParentId));
        // 再加当前文件锁
        locks.add(hierarchicalLockHelper.getReadWriteLock(id));
        // 顺序加锁
        RLock multiLock = hierarchicalLockHelper.lockAllWrite(locks);
        try {
            self.moveWithTransactional(id, newParentId, fileMetadata);
        } finally {
            hierarchicalLockHelper.unlockAll(multiLock);
        }
    }

    @Transactional
    public void moveWithTransactional(Long id, Long newParentId, FileMetadata fileMetadata) {
        Long userId = UserContextUtil.getUserId();
        String newParentName = null;
        if (newParentId != null) {
            FileMetadata newParentFileMetadata = this.getById(newParentId);
            if (newParentFileMetadata == null) {
                throw new NotFoundException(String.format("目标父文件夹不存在, ID: %s", newParentId));
            }
            if (!Boolean.TRUE.equals(newParentFileMetadata.getFolder())) {
                throw new BadRequestException(String.format("目标不是一个文件夹, ID: %s", newParentId));
            }
            if (isAncestor(id, newParentId)) {
                throw new BadRequestException("不能将文件夹移动到其子文件夹中");
            }
            checkDuplicateName(newParentId, fileMetadata.getName(), id);
            newParentName = newParentFileMetadata.getName();
        } else {
            checkDuplicateName(null, fileMetadata.getName(), id);
        }

        fileMetadata.setParentId(newParentId);
        fileMetadata.setParentName(newParentName);
        fileMetadata.setUpdatedBy(userId);
        if (!this.updateById(fileMetadata)) {
            throw new FileStorageException(String.format("移动文件失败, ID: %s", id));
        }
    }

    @Override
    public PageDTO<FileMetadataVo> getFileByPage(String keyword, Integer pageNum, Integer pageSize) {


        /*SELECT
                *
                FROM
        file_metadata
                WHERE
        name LIKE CONCAT('%', ?, '%')
        ORDER BY
        updated DESC,
        id DESC
        LIMIT ?, ?*/

        Page<FileMetadata> page = this.lambdaQuery()
                .like(StringUtils.hasText(keyword),
                        FileMetadata::getName,
                        keyword)
                .orderByDesc(FileMetadata::getUpdated)
                .orderByDesc(FileMetadata::getId)
                .page(new Page<>(pageNum, pageSize));

        // 获取分页记录
        List<FileMetadata> fileMetadata = page.getRecords();

        List<FileMetadataVo> fileMetadataVos = BeanUtil.copyToList(fileMetadata, FileMetadataVo.class);

        // 如果没有记录，返回空的分页结果
        if (fileMetadata.isEmpty()) {
            return new PageDTO<>(pageNum, pageSize, page.getTotal());
        }

        // 用 PageDTO 包装
        PageDTO<FileMetadataVo> pageDTO = new PageDTO<>(pageNum, pageSize, page.getTotal());
        pageDTO.setRecords(fileMetadataVos);
        return pageDTO;
    }

    @Override
    public void fileVectorRetry(String storageKey) {

        LambdaUpdateWrapper<FileVectorStatus> updateWrapper = new LambdaUpdateWrapper<FileVectorStatus>()
                .eq(FileVectorStatus::getStorageKey, storageKey)
                .eq(FileVectorStatus::getStatus, FileVectorStatusType.FAILED)
                .set(FileVectorStatus::getStatus, FileVectorStatusType.PENDING)
                .set(FileVectorStatus::getErrorMsg, null)
                .setSql("retry_count = retry_count + 1");
        int updated = fileVectorStatusMapper.update(null, updateWrapper);
        if (updated == 0) {
            throw new FileStorageException("文件向量状态不存在或正在重试: " + storageKey);
        }
        fileVectorProducer.sendFileVectorMessage(new FileVectorMessage(storageKey));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("文件不能为空或大小无效");
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            double maxSizeMb = (double) maxFileSize.toBytes() / (1024 * 1024);
            throw new BadRequestException(String.format("文件大小超过限制，最大允许 %.2fMB", maxSizeMb));
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException("文件名不能为空");
        }
        validateFileName(file.getOriginalFilename());
    }

    private void uploadToStorage(MultipartFile file, String storageKey) {
        try {
            ObjectWriteResponse response = minIoUtil.uploadFile(file, storageKey, file.getContentType());
            log.info("文件成功上传 MinIO. Object: {}, ETag: {}", response.object(), response.etag());
        } catch (Exception e) {
            log.error("MinIO 上传失败: {}", storageKey, e);
            throw new FileStorageException("文件存储服务异常", e);
        }
    }

    /**
     * 事务成功后的后置处理：清理旧资源 + 触发新流程
     */
    private void handlePostUpdateSuccess(String oldStorageKey, String newStorageKey) {
        // 异步清理旧资源 (MinIO文件 + 向量库数据)
        // 建议：将两个删除动作合并到一个 MQ 消息或异步任务中，确保主线程不阻塞

        // 这里 sendFileVectorDeleteMessage 同时负责：1.删MinIO 2.删向量库对应的doc
        fileStorageDeleteProducer.sendFileStorageDeleteMessage(new FileDeleteMessage(oldStorageKey, 0));
        fileVectorDeleteProducer.sendFileVectorDeleteMessage(new FileDeleteMessage(oldStorageKey, 0));
        // 触发新文件的向量化
        fileVectorProducer.sendFileVectorMessage(new FileVectorMessage(newStorageKey));
    }

    /**
     * 检查folderId是否是targetId的祖先
     */
    private boolean isAncestor(Long folderId, Long targetId) {
        if (folderId == null || targetId == null) {
            return false;
        }
        if (folderId.equals(targetId)) {
            return false;
        }
        List<FileMetadata> descendants = fileMetadataMapper.listAllDescendants(folderId);
        // 判断 targetId 是否在子孙列表里
        return descendants.stream()
                .anyMatch(f -> f.getId().equals(targetId));
    }

    private void validateFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BadRequestException("文件名不能为空");
        }

        String trimmedName = fileName.trim();
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("文件名不能只包含空格");
        }

        // 检查文件名长度（数据库字段限制为varchar(255)）
        if (trimmedName.length() > MAX_FILENAME_LENGTH) {
            throw new BadRequestException(String.format("文件名长度不能超过 %s 个字符（数据库限制）", MAX_FILENAME_LENGTH));
        }

        // 检查非法字符
        if (INVALID_FILENAME_CHARS.matcher(trimmedName).find()) {
            throw new BadRequestException("文件名包含非法字符，不允许使用: \\ / : * ? \" < > |");
        }

        // 检查文件名不能以点开头或结尾（特殊情况除外）
        if (trimmedName.startsWith(".") && trimmedName.length() == 1) {
            throw new BadRequestException("文件名不能只是一个点");
        }
        if (trimmedName.endsWith(".") && !trimmedName.equals("..")) {
            throw new BadRequestException("文件名不能以点结尾");
        }
    }

    private void checkDuplicateName(Long parentId, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            return;
        }

        LambdaQueryWrapper<FileMetadata> wrapper = new LambdaQueryWrapper<>();

        if (parentId == null) {
            wrapper.isNull(FileMetadata::getParentId);
        } else {
            wrapper.eq(FileMetadata::getParentId, parentId);
        }

        wrapper.eq(FileMetadata::getName, name.trim());

        // 排除当前文件（用于重命名和移动时的检查）
        if (excludeId != null) {
            wrapper.ne(FileMetadata::getId, excludeId);
        }

        long count = this.count(wrapper);
        if (count > 0) {
            throw new BadRequestException(String.format("同一目录下已存在同名文件或文件夹: %s", name));
        }
    }
}
