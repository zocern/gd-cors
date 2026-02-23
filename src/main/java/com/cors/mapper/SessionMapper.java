package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cors.cache.MybatisRedisCache;
import com.cors.domain.entity.Session;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
@CacheNamespace(implementation = MybatisRedisCache.class)
public interface SessionMapper extends BaseMapper<Session> {

}
