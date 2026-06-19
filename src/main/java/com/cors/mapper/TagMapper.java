package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cors.domain.entity.Tag;
import com.cors.domain.vo.FileMetadataVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签 Mapper
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 查询某文件/文件夹绑定的所有标签
     *
     * @param fileId 文件或文件夹ID
     * @return 标签列表
     */
    List<Tag> selectTagsByFileId(@Param("fileId") Long fileId);

    /**
     * 按标签ID分页查询绑定的文件/文件夹列表
     *
     * @param page  分页参数
     * @param tagId 标签ID
     * @return 文件元数据列表
     */
    Page<FileMetadataVo> selectFilesByTagId(Page<FileMetadataVo> page, @Param("tagId") Long tagId);
}
