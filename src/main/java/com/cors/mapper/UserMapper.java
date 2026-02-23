package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cors.cache.MybatisRedisCache;
import com.cors.domain.entity.User;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@CacheNamespace(implementation = MybatisRedisCache.class)
public interface UserMapper extends BaseMapper<User> {

}
