package com.cors.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.entity.FileMetadata;
import com.cors.domain.entity.FileVersion;
import com.cors.domain.entity.FileVectorStatus;
import com.cors.domain.vo.FileVersionVo;
import com.cors.enums.FileVectorStatusType;
import com.cors.exception.BadRequestException;
import com.cors.exception.NotFoundException;
import com.cors.mapper.FileMetadataMapper;
import com.cors.mapper.FileVersionMapper;
import com.cors.mapper.FileVectorStatusMapper;
import com.cors.mq.message.FileDeleteMessage;
import com.cors.mq.producer.FileVectorDeleteProducer;
import com.cors.mq.producer.FileVectorProducer;
import com.cors.mq.message.FileVectorMessage;
import com.cors.service.FileVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileVersionServiceImpl extends ServiceImpl<FileVersionMapper, FileVersion>
        implements FileVersionService {

    private final FileVersionMapper fileVersionMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final FileVectorStatusMapper fileVectorStatusMapper;
    private final FileVectorProducer fileVectorProducer;
    private final FileVectorDeleteProducer fileVectorDeleteProducer;

    @Override
    public List<FileVersionVo> listVersions(Long fileId) {
        FileMetadata meta = fileMetadataMapper.selectById(fileId);
        if (meta == null) {
            throw new NotFoundException("文件不存在，id: " + fileId);
        }
        if (Boolean.TRUE.equals(meta.getFolder())) {
            throw new BadRequestException("文件夹不支持版本管理");
        }

        List<FileVersion> versions = this.lambdaQuery()
                .eq(FileVersion::getFileId, fileId)
                .orderByAsc(FileVersion::getVersion)
                .list();

        // 批量查询所有版本的向量化状态，减少 N+1 查询
        List<String> storageKeys = versions.stream()
                .map(FileVersion::getStorageKey)
                .toList();

        Map<String, String> vectorStatusMap = storageKeys.isEmpty()
                ? Map.of()
                : fileVectorStatusMapper.selectList(
                        new LambdaQueryWrapper<FileVectorStatus>()
                                .in(FileVectorStatus::getStorageKey, storageKeys)
                  ).stream()
                  .collect(Collectors.toMap(
                          FileVectorStatus::getStorageKey,
                          s -> s.getStatus().getValue()
                  ));

        Integer currentVersion = meta.getCurrentVersion();

        return versions.stream().map(v -> {
            FileVersionVo vo = BeanUtil.copyProperties(v, FileVersionVo.class);
            vo.setCurrent(v.getVersion().equals(currentVersion));
            vo.setVectorStatus(vectorStatusMap.get(v.getStorageKey()));
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public void switchVersion(Long fileId, Integer targetVersion) {
        FileMetadata meta = fileMetadataMapper.selectById(fileId);
        if (meta == null) {
            throw new NotFoundException("文件不存在，id: " + fileId);
        }
        if (Boolean.TRUE.equals(meta.getFolder())) {
            throw new BadRequestException("文件夹不支持版本切换");
        }
        if (targetVersion.equals(meta.getCurrentVersion())) {
            throw new BadRequestException("已是当前版本（v" + targetVersion + "），无需切换");
        }

        // 查目标版本记录
        FileVersion targetVer = this.lambdaQuery()
                .eq(FileVersion::getFileId, fileId)
                .eq(FileVersion::getVersion, targetVersion)
                .one();
        if (targetVer == null) {
            throw new NotFoundException("目标版本不存在: v" + targetVersion);
        }

        String oldStorageKey = meta.getStorageKey();   // 当前激活版本的 storageKey
        String newStorageKey = targetVer.getStorageKey();
        Integer oldVersion = meta.getCurrentVersion();

        // 更新 file_metadata：切换 storageKey、currentVersion、size
        meta.setStorageKey(newStorageKey);
        meta.setCurrentVersion(targetVersion);
        meta.setSize(targetVer.getSize());
        fileMetadataMapper.updateById(meta);

        log.info("文件版本切换: fileId={}, v{} -> v{}", fileId, oldVersion, targetVersion);

        // 事务提交后，异步触发向量库切换（保证 DB 已落库再操作 MQ）
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        triggerVectorSwitch(oldStorageKey, newStorageKey);
                    }
                }
        );
    }

    /**
     * 触发向量库切换：
     * 1. 删除旧版本在 Milvus 中的向量数据（保留 file_vector_status 记录）
     * 2. 重置目标版本状态为 PENDING，重新触发向量化
     *
     * 注意：Milvus 只维护当前激活版本的向量，历史版本向量在文件更新时已被删除，
     *       因此切换版本时目标版本必须重新向量化，不存在复用场景。
     */
    private void triggerVectorSwitch(String oldStorageKey, String newStorageKey) {
        log.info("向量库切换开始: old={}, new={}", oldStorageKey, newStorageKey);

        // 1. 删除旧版本向量数据（deleteStatus=false：只删 Milvus 数据，保留状态表记录）
        fileVectorDeleteProducer.sendFileVectorDeleteMessage(
                new FileDeleteMessage(oldStorageKey, 0, false)
        );

        // 2. 重置目标版本状态为 PENDING，触发重新向量化
        FileVectorStatus newStatus = fileVectorStatusMapper.selectById(newStorageKey);
        if (newStatus == null) {
            // 状态记录不存在（兜底处理）
            FileVectorStatus status = new FileVectorStatus();
            status.setStorageKey(newStorageKey);
            status.setStatus(FileVectorStatusType.PENDING);
            try {
                fileVectorStatusMapper.insert(status);
            } catch (DuplicateKeyException e) {
                log.debug("向量化状态已存在（并发写入），忽略: {}", newStorageKey);
            }
        } else {
            fileVectorStatusMapper.update(null,
                    new LambdaUpdateWrapper<FileVectorStatus>()
                            .eq(FileVectorStatus::getStorageKey, newStorageKey)
                            .set(FileVectorStatus::getStatus, FileVectorStatusType.PENDING)
                            .set(FileVectorStatus::getErrorMsg, null)
                            .setSql("retry_count = 0")
            );
        }
        log.info("向量库切换：目标版本 {} 开始重新向量化", newStorageKey);
        fileVectorProducer.sendFileVectorMessage(new FileVectorMessage(newStorageKey));
    }
}
