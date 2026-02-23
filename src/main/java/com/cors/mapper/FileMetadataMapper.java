package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.cors.cache.MybatisRedisCache;
import com.cors.domain.entity.FileMetadata;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
@CacheNamespace(implementation = MybatisRedisCache.class)
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    /**
     * 使用 MySQL 8.0 递归 CTE 实现
     */
    List<FileMetadata> listAllAncestors(@Param("id") Long id);

    List<FileMetadata> listAllDescendants(@Param("id") Long id);

    /**
     * 递归查询所有后代节点（包括子文件夹和文件）
     * @param id 根文件夹ID
     * @return 所有后代节点列表
     */
    List<FileMetadata> selectAllDescendants(@Param("id") Long id);
}
