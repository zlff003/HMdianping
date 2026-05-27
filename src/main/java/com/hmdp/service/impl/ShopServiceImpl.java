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

import jakarta.annotation.Resource;
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
