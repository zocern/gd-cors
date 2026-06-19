package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cors.domain.entity.FileTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件标签关联 Mapper
 */
@Mapper
public interface FileTagRelationMapper extends BaseMapper<FileTagRelation> {

    /**
     * 批量插入文件标签关联（忽略重复）
     *
     * @param relations 关联列表
     */
    void insertBatchIgnore(@Param("list") List<FileTagRelation> relations);

    /**
     * 查询某文件绑定的所有标签ID
     *
     * @param fileId 文件ID
     * @return 标签ID列表
     */
    List<Long> selectTagIdsByFileId(@Param("fileId") Long fileId);
}
