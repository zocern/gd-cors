package com.cors.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.dto.TagDto;
import com.cors.domain.entity.Tag;
import com.cors.domain.vo.FileMetadataVo;
import com.cors.domain.vo.TagVo;

import java.util.List;

/**
 * 标签 Service 接口
 */
public interface TagService extends IService<Tag> {

    /**
     * 创建标签
     *
     * @param dto 标签信息
     * @return 新标签ID
     */
    Long createTag(TagDto dto);

    /**
     * 更新标签
     *
     * @param id  标签ID
     * @param dto 标签信息
     */
    void updateTag(Long id, TagDto dto);

    /**
     * 删除标签（同时解除所有文件绑定）
     *
     * @param id 标签ID
     */
    void deleteTag(Long id);

    /**
     * 查询标签列表（非分页，限制最大返回条数）
     *
     * @param project 项目标识（可选，为 null 时查全表）
     * @param keyword 关键字（可选，按名称模糊搜索）
     * @return 标签列表
     */
    List<TagVo> listTags(String project, String keyword);

    /**
     * 分页查询标签列表
     *
     * @param project  项目标识（可选，为 null 时查全表）
     * @param keyword  关键字（可选，按名称模糊搜索）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页标签列表
     */
    PageDTO<TagVo> pageTags(String project, String keyword, Integer pageNum, Integer pageSize);

    /**
     * 查询单个标签详情
     *
     * @param id 标签ID
     * @return 标签VO
     */
    TagVo getTagById(Long id);

    /**
     * 为文件/文件夹绑定标签（追加，不覆盖已有绑定）
     *
     * @param fileId 文件或文件夹ID
     * @param tagIds 标签ID列表
     */
    void bindTags(Long fileId, List<Long> tagIds);

    /**
     * 更新文件/文件夹的标签绑定（全量替换：先清空再绑定）
     *
     * @param fileId 文件或文件夹ID
     * @param tagIds 新的标签ID列表（空列表表示清空所有标签）
     */
    void updateFileTags(Long fileId, List<Long> tagIds);

    /**
     * 解绑文件/文件夹的指定标签
     *
     * @param fileId 文件或文件夹ID
     * @param tagIds 要解绑的标签ID列表
     */
    void unbindTags(Long fileId, List<Long> tagIds);

    /**
     * 查询文件/文件夹绑定的所有标签
     *
     * @param fileId 文件或文件夹ID
     * @return 标签列表
     */
    List<TagVo> getTagsByFileId(Long fileId);

    /**
     * 按标签分页查询绑定的文件/文件夹列表
     *
     * @param tagId    标签ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页文件列表
     */
    PageDTO<FileMetadataVo> getFilesByTagId(Long tagId, Integer pageNum, Integer pageSize);

    /**
     * 校验标签列表是否全部存在（公开方法，供 Controller 在绑定前预校验）
     *
     * @param tagIds 标签ID列表（null 或空时直接通过）
     */
    void validateTagsExist(List<Long> tagIds);
}
