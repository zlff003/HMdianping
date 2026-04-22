package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  店铺服务实现类
 *  提供店铺查询、更新等功能，实现了多种缓存策略来解决缓存穿透和缓存击穿问题
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 根据ID查询店铺信息（使用逻辑过期策略解决缓存击穿）
     *
     * @param id 店铺ID
     * @return 店铺信息结果
     */
    @Override
    public Result queryById(Long id) {
        // 使用逻辑过期策略解决缓存击穿问题
//        Shop shop = queryWithLogicalExpire(id);

        // 缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id,
//                Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存穿透
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id,
                Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 缓存重建线程池（用于逻辑过期策略的异步缓存更新）
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    
    /**
     * 使用逻辑过期策略查询店铺（解决缓存击穿问题）
     * 核心思路：不设置Redis TTL，而是在值中存储逻辑过期时间，异步重建缓存
     *
     * @param id 店铺ID
     * @return 店铺信息，如果缓存不存在则返回null
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        
        // 1. 从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        
        // 2. 判断缓存是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 缓存不存在，直接返回null
            return null;
        }
        
        // 3. 缓存命中，将JSON反序列化为RedisData对象（包含数据和逻辑过期时间）
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        
        // 4. 判断逻辑过期时间是否有效
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return shop;
        }
        
        // 5. 已过期，需要缓存重建
        // 5.1 获取互斥锁，防止多个线程同时重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        
        // 5.2 判断是否成功获取锁
        if (isLock) {
            // 获取锁成功，开启独立线程异步重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存并设置新的逻辑过期时间（20秒）
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        
        // 6. 无论是否获取锁，都立即返回旧的店铺信息（保证高可用）
        return shop;
    }


    /**
     * 使用缓存空值策略查询店铺（解决缓存穿透问题）
     * 核心思路：数据库中也查不到数据时，将空值写入缓存，避免大量请求打到数据库
     *
     * @param id 店铺ID
     * @return 店铺信息，如果不存在则返回null
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        
        // 1. 从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        
        // 2. 判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 缓存存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        
        // 3. 判断是否为空值（用于解决缓存穿透）
        if (shopJson != null) {
            // 之前查询过且数据库中没有该数据
            return null;
        }
        
        // 4. 缓存不存在，根据ID查询数据库
        Shop shop = getById(id);
        
        // 5. 数据库中也不存在该数据，将空值写入Redis防止缓存穿透
        if (shop == null) {
            // 设置较短的过期时间（2分钟）
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        
        // 6. 数据库中存在该数据，写入Redis缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 更新店铺信息（先更新数据库，再删除缓存 - Cache Aside Pattern）
     *
     * @param shop 店铺信息
     * @return 操作结果
     */
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        
        // 1. 更新数据库
        updateById(shop);
        
        // 2. 删除缓存（下次查询时会重新加载最新数据）
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        
        return Result.ok();
    }

}
