package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cors.cache.MybatisRedisCache;
import com.cors.domain.entity.FileAssociation;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@CacheNamespace(implementation = MybatisRedisCache.class)
public interface FileAssociationMapper extends BaseMapper<FileAssociation> {

}
