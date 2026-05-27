package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 使用缓存空值策略查询店铺（解决缓存穿透问题）
     * 核心思路：数据库中也查不到数据时，将空值写入缓存，避免大量请求打到数据库
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否存在
        if (StrUtil.isNotBlank(json)) {
            // 缓存存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 3. 判断是否为空值（用于解决缓存穿透）
        if (json != null) {
            // 之前查询过且数据库中没有该数据
            return null;
        }

        // 4. 缓存不存在，根据ID查询数据库
        R r = dbFallback.apply(id);

        // 5. 数据库中也不存在该数据，将空值写入Redis防止缓存穿透
        if (r == null) {
            // 设置较短的过期时间（2分钟）
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 数据库中存在该数据，写入Redis缓存
        this.set(key, r, time, unit);
        return r;
    }


    /**
     * 使用互斥锁查询店铺（解决缓存击穿问题）
     * 核心思路：缓存失效时，只允许一个线程查询数据库并重建缓存，其他线程等待后重试
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 从Redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否存在
        if (StrUtil.isNotBlank(json)) {
            // 缓存存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 3. 判断是否为空值（用于解决缓存穿透）
        if (json != null) {
            // 之前查询过且数据库中没有该数据
            return null;
        }

        // 4. 缓存不存在，实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);

            // 4.2 判断是否成功获取锁
            if (!isLock) {
                // 获取锁失败，休眠一段时间后递归重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 4.3 获取锁成功，根据ID查询数据库
            r = dbFallback.apply(id);
            // 模拟延时，测试并发场景
            Thread.sleep(200);

            // 4.4 数据库中没有该数据，写入空值防止缓存穿透
            if (r == null) {
                // 将空值写入Redis，设置较短的过期时间
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 4.5 数据库中存在该数据，写入Redis缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 5. 释放互斥锁
            unLock(lockKey);
        }

        return r;
    }

    /**
     * 缓存重建线程池（用于逻辑过期策略的异步缓存更新）
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 使用逻辑过期策略查询店铺（解决缓存击穿问题）
     * 核心思路：不设置Redis TTL，而是在值中存储逻辑过期时间，异步重建缓存
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 从Redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否命中了空值
        if (json != null && StrUtil.isBlank(json)) {
            return null;
        }

        // 3. 缓存未命中，查询数据库并重建逻辑过期缓存
        if (json == null) {
            R r = dbFallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.setWithLogicalExpire(key, r, time, unit);
            return r;
        }

        // 4. 缓存命中，将JSON反序列化为RedisData对象（包含数据和逻辑过期时间）
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判断逻辑过期时间是否有效
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }

        // 6. 已过期，需要缓存重建
        // 6.1 获取互斥锁，防止多个线程同时重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 6.2 判断是否成功获取锁
        if (isLock) {
            // 获取锁成功，开启独立线程异步重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    if (r1 == null) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return;
                    }
                    // 写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    unLock(lockKey);
                }
            });
        }

        // 7. 无论是否获取锁，都立即返回旧的店铺信息（保证高可用）
        return r;
    }


    /**
     * 尝试获取分布式锁（基于Redis SETNX实现）
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放分布式锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}
