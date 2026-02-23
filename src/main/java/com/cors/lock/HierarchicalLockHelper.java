package com.cors.lock;

import com.cors.domain.entity.FileMetadata;
import com.cors.mapper.FileMetadataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


@Slf4j
@Component
@RequiredArgsConstructor
public class HierarchicalLockHelper {

    private final RedissonClient redissonClient;
    private final FileMetadataMapper localFileMetadataMapper;

    /**
     * 获取从根到目标文件夹的所有祖先锁
     */
    public List<RReadWriteLock> getAncestorReadWriteLocks(Long parentId) {
        return Stream.concat(
                // root 层面的全局锁
                Stream.of(redissonClient.getReadWriteLock("rwlock:file:root")),
                // 如果 parentId 不为空，则获取祖先锁
                parentId == null
                        ? Stream.empty()
                        : localFileMetadataMapper.listAllAncestors(parentId).stream()
                        .map(file -> redissonClient.getReadWriteLock("rwlock:file:" + file.getId()))).toList();
    }

    /**
     * 获取目标文件夹的读写锁
     * @param id
     * @return
     */
    public RReadWriteLock getReadWriteLock(Long id) {
        String lockKey = (id == null) ? "rwlock:file:root" : "rwlock:file:" + id;
        return redissonClient.getReadWriteLock(lockKey);
    }

    /**
     * 获取从目标文件夹到所有子孙文件/文件夹锁
     */
    public List<RReadWriteLock> getDescendantReadWriteLocks(Long id) {
        if (id == null) return Collections.emptyList();
        ;
        // 一次性获取当前节点及所有子孙节点
        List<FileMetadata> allNodes = localFileMetadataMapper.listAllDescendants(id);
        if (allNodes == null || allNodes.isEmpty()) {
            return Collections.emptyList();
        }
        return allNodes.stream()
                .map(file -> redissonClient.getReadWriteLock("rwlock:file:" + file.getId()))
                .toList();
    }

    /**
     * 锁定所有读锁 (用于下载等共享操作)
     *
     * @param rwLocks 要锁定的读写锁列表
     * @return 组合锁 RLock (RedissonMultiLock)
     */
    // 锁定所有读锁 使用 RedissonMultiLock 锁定所有传入的锁
    public RLock lockAllRead(List<RReadWriteLock> rwLocks) {
        if (rwLocks == null || rwLocks.isEmpty()) {
            return null;
        }
        // 从每个 RReadWriteLock 中获取 readLock()
        RLock[] readLocks = rwLocks.stream()
                .map(RReadWriteLock::readLock)
                .toArray(RLock[]::new);

        RLock multiLock = redissonClient.getMultiLock(readLocks);
        // multiLock.tryLock(3, TimeUnit.SECONDS);
        // return multiLock;
        try {
            boolean isLocked = multiLock.tryLock(3, TimeUnit.SECONDS); // 仅指定等待锁的超时时间，不指定锁的持有时间 自动续期
            if (isLocked) {
                return multiLock;
            } else {
                throw new RuntimeException("系统繁忙，获取组合读锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        }
    }

    /**
     * 锁定所有写锁 (用于移动、重命名、删除等排他操作)
     *
     * @param rwLocks 要锁定的读写锁列表
     * @return 组合锁 RLock (RedissonMultiLock)
     */
    public RLock lockAllWrite(List<RReadWriteLock> rwLocks) {
        if (rwLocks == null || rwLocks.isEmpty()) {
            return null;
        }
        // 从每个 RReadWriteLock 中获取 writeLock()
        RLock[] writeLocks = rwLocks.stream()
                .map(RReadWriteLock::writeLock)
                .toArray(RLock[]::new);

        RLock multiLock = redissonClient.getMultiLock(writeLocks);
        // multiLock.lock(); // 自动续期
        // return multiLock;

        try {
            boolean isLocked = multiLock.tryLock(3, TimeUnit.SECONDS);
            if (isLocked) {
                return multiLock;
            } else {
                throw new RuntimeException("系统繁忙，获取组合写锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        }
    }

    /**
     * 锁定写锁
     * @param rwLock
     * @return
     */
    public RLock lockWrite(RReadWriteLock rwLock) {
        if (rwLock == null) {
            return null;
        }
        RLock writeLock = rwLock.writeLock();
        // writeLock.lock();
        // return writeLock;
        try {
            boolean isLocked = writeLock.tryLock(3, TimeUnit.SECONDS);
            if (isLocked) {
                return writeLock;
            } else {
                throw new RuntimeException("系统繁忙，获取写锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        }
    }

    /**
     * 锁定读锁
     * @param rwLock
     * @return
     */
    public RLock lockRead(RReadWriteLock rwLock) {
        if (rwLock == null) {
            return null;
        }
        RLock readLock = rwLock.readLock();
        // readLock.lock();
        // return readLock;
        try {
            boolean isLocked = readLock.tryLock(3, TimeUnit.SECONDS);
            if (isLocked) {
                return readLock;
            } else {
                throw new RuntimeException("系统繁忙，获取读锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        }
    }


    /**
     * 通用解锁方法
     * (MultiLock 本身也是一个 RLock, 解锁逻辑不变)
     */
    public void unlockAll(RLock multiLock) {
        if (multiLock != null && multiLock.isHeldByCurrentThread()) {
            try {
                multiLock.unlock();
            } catch (Exception e) {
                log.warn("解锁失败", e);
            }
        }
    }
}
