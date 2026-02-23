package com.cors.cache;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.Cache;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.cors.constant.CommonConstants.MYBATIS_CACHE_KEY_PREFIX;

@Slf4j
public class MybatisRedisCache implements Cache {
    private final String id;
    private static RedisTemplate<String, Object> redisTemplate;
    private static final int expireHour = 24;

    public static void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        MybatisRedisCache.redisTemplate = redisTemplate;
    }

    public MybatisRedisCache(String id) {
        this.id = id; // id == 命名空间
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void putObject(Object key, Object value) {
        // redisTemplate.opsForValue().set(generateKey(key), value, expireHour, TimeUnit.HOURS);
        try {
            redisTemplate.opsForValue().set(generateKey(key), value, expireHour, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Redis putObject error", e);
        }
    }

    @Override
    public Object getObject(Object key) {
        return redisTemplate.opsForValue().get(generateKey(key));
    }

    @Override
    public Object removeObject(Object key) {
        return redisTemplate.delete(generateKey(key));
    }

    @Override
    public void clear() {
        // 只删除当前Mapper的缓存数据
        String prefix = generateKey("");
        // System.out.println(prefix);
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public int getSize() {
        return Objects.requireNonNull(redisTemplate.execute(RedisServerCommands::dbSize)).intValue();
    }

    private String generateKey(Object key) {
        return MYBATIS_CACHE_KEY_PREFIX + id + ":" + key;
    }
}
