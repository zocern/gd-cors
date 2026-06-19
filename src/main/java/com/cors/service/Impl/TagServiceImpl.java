package com.cors.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.dto.TagDto;
import com.cors.domain.entity.FileMetadata;
import com.cors.domain.entity.FileTagRelation;
import com.cors.domain.entity.Tag;
import com.cors.domain.vo.FileMetadataVo;
import com.cors.domain.vo.TagVo;
import com.cors.exception.BadRequestException;
import com.cors.exception.NotFoundException;
import com.cors.mapper.FileMetadataMapper;
import com.cors.mapper.FileTagRelationMapper;
import com.cors.mapper.TagMapper;
import com.cors.service.TagService;
import com.cors.util.UserContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    /** 锁等待超时（秒） */
    private static final long LOCK_WAIT_SECONDS = 3L;
    /** 文件维度标签操作锁 key 前缀 */
    private static final String FILE_TAG_LOCK_PREFIX = "lock:file-tag:file:";
    /** 标签维度操作锁 key 前缀 */
    private static final String TAG_LOCK_PREFIX = "lock:tag:";

    private final TagMapper tagMapper;
    private final FileTagRelationMapper fileTagRelationMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final RedissonClient redissonClient;

    // 通过 @Lazy 注入自身代理，保证 @Transactional 方法在内部互调时仍然生效
    @Lazy
    @Autowired
    private TagServiceImpl self;

    // ==================== 标签 CRUD ====================

    @Override
    @Transactional
    public Long createTag(TagDto dto) {
        Long userId = UserContextUtil.getUserId();
        Tag tag = Tag.builder()
                .project(dto.getProject().trim())
                .name(dto.getName().trim())
                .color(dto.getColor())
                .description(dto.getDescription())
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        try {
            this.save(tag);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException(
                    "该项目下标签名称已存在: project=" + dto.getProject() + ", name=" + dto.getName());
        }
        log.info("标签创建成功: id={}, project={}, name={}", tag.getId(), tag.getProject(), tag.getName());
        return tag.getId();
    }

    @Override
    @Transactional
    public void updateTag(Long id, TagDto dto) {
        Tag tag = this.getById(id);
        if (tag == null) {
            throw new NotFoundException("标签不存在，id: " + id);
        }
        Long userId = UserContextUtil.getUserId();
        tag.setProject(dto.getProject().trim());
        tag.setName(dto.getName().trim());
        tag.setColor(dto.getColor());
        tag.setDescription(dto.getDescription());
        tag.setUpdatedBy(userId);
        try {
            this.updateById(tag);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException(
                    "该项目下标签名称已存在: project=" + dto.getProject() + ", name=" + dto.getName());
        }
        log.info("标签更新成功: id={}, project={}, name={}", id, tag.getProject(), tag.getName());
    }

    @Override
    public void deleteTag(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("标签ID无效");
        }
        // 标签维度加锁：避免删除时正有其他请求执行 bindTags 导致孤儿 relation
        RLock tagLock = acquireLock(TAG_LOCK_PREFIX + id);
        try {
            self.deleteTagInTransaction(id);
        } finally {
            releaseLock(tagLock);
        }
    }

    @Transactional
    public void deleteTagInTransaction(Long id) {
        Tag tag = this.getById(id);
        if (tag == null) {
            throw new NotFoundException("标签不存在，id: " + id);
        }
        // 先删除所有文件与该标签的关联
        fileTagRelationMapper.delete(
                new LambdaQueryWrapper<FileTagRelation>()
                        .eq(FileTagRelation::getTagId, id)
        );
        // 再删除标签本身
        this.removeById(id);
        log.info("标签删除成功: id={}, name={}", id, tag.getName());
    }

    @Override
    public List<TagVo> listTags(String project, String keyword) {
        // 非分页列表：通过 LIMIT 兜底，避免标签数量膨胀后一次性拉全量数据
        // project 为 null 时查全表，不为 null 时按项目过滤
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(StringUtils.hasText(project), Tag::getProject, project)
                .like(StringUtils.hasText(keyword), Tag::getName, keyword)
                .orderByAsc(Tag::getProject)
                .orderByAsc(Tag::getName)
                .last("LIMIT " + MAX_PAGE_SIZE);
        List<Tag> tags = this.list(wrapper);
        return BeanUtil.copyToList(tags, TagVo.class);
    }

    @Override
    public PageDTO<TagVo> pageTags(String project, String keyword, Integer pageNum, Integer pageSize) {
        int safePageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int safePageSize = (pageSize == null || pageSize < 1) ? 10
                : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<Tag> page = this.lambdaQuery()
                .eq(StringUtils.hasText(project), Tag::getProject, project)
                .like(StringUtils.hasText(keyword), Tag::getName, keyword)
                .orderByAsc(Tag::getProject)
                .orderByAsc(Tag::getName)
                .page(new Page<>(safePageNum, safePageSize));

        PageDTO<TagVo> pageDTO = new PageDTO<>(safePageNum, safePageSize, page.getTotal());
        pageDTO.setRecords(BeanUtil.copyToList(page.getRecords(), TagVo.class));
        return pageDTO;
    }

    @Override
    public TagVo getTagById(Long id) {
        Tag tag = this.getById(id);
        if (tag == null) {
            throw new NotFoundException("标签不存在，id: " + id);
        }
        return BeanUtil.copyProperties(tag, TagVo.class);
    }

    // ==================== 文件标签绑定 ====================

    @Override
    public void bindTags(Long fileId, List<Long> tagIds) {
        // 自防御：tagIds 为空直接返回，避免下游 NPE
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // 文件维度加锁，保证同一文件的标签操作串行化
        RLock fileLock = acquireLock(FILE_TAG_LOCK_PREFIX + fileId);
        try {
            self.bindTagsInTransaction(fileId, tagIds);
        } finally {
            releaseLock(fileLock);
        }
    }

    @Transactional
    public void bindTagsInTransaction(Long fileId, List<Long> tagIds) {
        validateFileExists(fileId);
        validateTagsExist(tagIds);

        List<FileTagRelation> relations = tagIds.stream()
                .distinct()
                .map(tagId -> FileTagRelation.builder()
                        .fileId(fileId)
                        .tagId(tagId)
                        .build())
                .collect(Collectors.toList());

        fileTagRelationMapper.insertBatchIgnore(relations);
        log.info("文件标签绑定成功: fileId={}, tagIds={}", fileId, tagIds);
    }

    @Override
    public void updateFileTags(Long fileId, List<Long> tagIds) {
        // 文件维度加锁，避免并发 DELETE+INSERT 出现脏数据
        RLock fileLock = acquireLock(FILE_TAG_LOCK_PREFIX + fileId);
        try {
            self.updateFileTagsInTransaction(fileId, tagIds);
        } finally {
            releaseLock(fileLock);
        }
    }

    @Transactional
    public void updateFileTagsInTransaction(Long fileId, List<Long> tagIds) {
        validateFileExists(fileId);
        if (tagIds != null && !tagIds.isEmpty()) {
            validateTagsExist(tagIds);
        }

        // 全量替换：先清空该文件的所有标签绑定
        fileTagRelationMapper.delete(
                new LambdaQueryWrapper<FileTagRelation>()
                        .eq(FileTagRelation::getFileId, fileId)
        );

        // 再重新绑定
        if (tagIds != null && !tagIds.isEmpty()) {
            List<FileTagRelation> relations = tagIds.stream()
                    .distinct()
                    .map(tagId -> FileTagRelation.builder()
                            .fileId(fileId)
                            .tagId(tagId)
                            .build())
                    .collect(Collectors.toList());
            fileTagRelationMapper.insertBatchIgnore(relations);
        }
        log.info("文件标签更新成功: fileId={}, tagIds={}", fileId, tagIds);
    }

    @Override
    public void unbindTags(Long fileId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        RLock fileLock = acquireLock(FILE_TAG_LOCK_PREFIX + fileId);
        try {
            self.unbindTagsInTransaction(fileId, tagIds);
        } finally {
            releaseLock(fileLock);
        }
    }

    @Transactional
    public void unbindTagsInTransaction(Long fileId, List<Long> tagIds) {
        validateFileExists(fileId);

        fileTagRelationMapper.delete(
                new LambdaQueryWrapper<FileTagRelation>()
                        .eq(FileTagRelation::getFileId, fileId)
                        .in(FileTagRelation::getTagId, tagIds)
        );
        log.info("文件标签解绑成功: fileId={}, tagIds={}", fileId, tagIds);
    }

    @Override
    public List<TagVo> getTagsByFileId(Long fileId) {
        validateFileExists(fileId);
        List<Tag> tags = tagMapper.selectTagsByFileId(fileId);
        return BeanUtil.copyToList(tags, TagVo.class);
    }

    @Override
    public PageDTO<FileMetadataVo> getFilesByTagId(Long tagId, Integer pageNum, Integer pageSize) {
        if (tagId == null || tagId <= 0) {
            throw new BadRequestException("标签ID无效");
        }
        // 分页参数兜底，防止负数/0/超大值导致 SQL LIMIT 异常或内存爆炸
        int safePageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int safePageSize = (pageSize == null || pageSize < 1) ? 10
                : Math.min(pageSize, MAX_PAGE_SIZE);

        Tag tag = this.getById(tagId);
        if (tag == null) {
            throw new NotFoundException("标签不存在，id: " + tagId);
        }

        Page<FileMetadataVo> page = new Page<>(safePageNum, safePageSize);
        tagMapper.selectFilesByTagId(page, tagId);

        PageDTO<FileMetadataVo> pageDTO = new PageDTO<>(safePageNum, safePageSize, page.getTotal());
        pageDTO.setRecords(page.getRecords() == null ? Collections.emptyList() : page.getRecords());
        return pageDTO;
    }

    // ==================== 私有辅助方法 ====================

    /** 单次分页最大允许返回的条数，防止恶意/异常请求导致大查询 */
    private static final int MAX_PAGE_SIZE = 1000;

    /**
     * 获取分布式锁，等待 LOCK_WAIT_SECONDS 秒
     */
    private RLock acquireLock(String key) {
        RLock lock = redissonClient.getLock(key);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BadRequestException("系统繁忙，请稍后重试");
            }
            return lock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("获取锁被中断");
        }
    }

    /**
     * 释放分布式锁
     */
    private void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
            } catch (Exception e) {
                log.warn("标签锁释放失败", e);
            }
        }
    }

    /**
     * 校验文件/文件夹是否存在
     */
    private void validateFileExists(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new BadRequestException("文件ID无效");
        }
        FileMetadata file = fileMetadataMapper.selectById(fileId);
        if (file == null) {
            throw new NotFoundException("文件或文件夹不存在，id: " + fileId);
        }
    }

    /**
     * 校验标签列表是否全部存在（对外公开，便于在外部操作前预校验）
     */
    @Override
    public void validateTagsExist(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // 去重，避免重复传入导致 count != size 误判
        List<Long> distinctIds = tagIds.stream().distinct().collect(Collectors.toList());
        long count = this.count(
                new LambdaQueryWrapper<Tag>().in(Tag::getId, distinctIds)
        );
        if (count != distinctIds.size()) {
            throw new NotFoundException("部分标签不存在，请检查标签ID列表");
        }
    }
}
