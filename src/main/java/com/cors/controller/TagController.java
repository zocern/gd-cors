package com.cors.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.cors.domain.ResponseResult;
import com.cors.domain.dto.FileTagDto;
import com.cors.domain.dto.TagDto;
import com.cors.domain.vo.FileMetadataVo;
import com.cors.domain.vo.TagVo;
import com.cors.service.TagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理 Controller
 * <p>
 * 接口前缀：/api/v1/tags
 * 所有接口需登录（携带 Authorization Header）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;

    // ==================== 标签 CRUD ====================

    /**
     * 创建标签
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Long> createTag(@Valid @RequestBody TagDto dto) {
        return ResponseResult.success(tagService.createTag(dto));
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public ResponseResult<Void> updateTag(@PathVariable @NotNull(message = "标签ID不能为空") Long id,
                                          @Valid @RequestBody TagDto dto) {
        tagService.updateTag(id, dto);
        return ResponseResult.success();
    }

    /**
     * 删除标签（同时解除所有文件绑定）
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable @NotNull(message = "标签ID不能为空") Long id) {
        tagService.deleteTag(id);
    }

    /**
     * 查询标签列表（支持按项目和名称过滤，最多返回 1000 条）
     * project 不传时查全表；传入时只返回该项目下的标签
     */
    @GetMapping
    public ResponseResult<List<TagVo>> listTags(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String keyword) {
        return ResponseResult.success(tagService.listTags(project, keyword));
    }

    /**
     * 分页查询标签列表（标签数量较多时建议使用此接口）
     * project 不传时查全表；传入时只返回该项目下的标签
     */
    @GetMapping("/page")
    public ResponseResult<PageDTO<TagVo>> pageTags(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ResponseResult.success(tagService.pageTags(project, keyword, pageNum, pageSize));
    }

    /**
     * 查询单个标签详情
     */
    @GetMapping("/{id}")
    public ResponseResult<TagVo> getTag(@PathVariable @NotNull(message = "标签ID不能为空") Long id) {
        return ResponseResult.success(tagService.getTagById(id));
    }

    // ==================== 标签维度查询 ====================

    /**
     * 按标签分页查询绑定的文件/文件夹列表
     */
    @GetMapping("/{id}/files")
    public ResponseResult<PageDTO<FileMetadataVo>> getFilesByTag(
            @PathVariable @NotNull(message = "标签ID不能为空") Long id,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ResponseResult.success(tagService.getFilesByTagId(id, pageNum, pageSize));
    }
}
