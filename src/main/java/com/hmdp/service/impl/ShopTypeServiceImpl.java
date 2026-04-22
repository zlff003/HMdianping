package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 店铺类型服务实现类
 * 提供店铺类型查询功能，支持Redis缓存优化
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * 查询所有店铺类型列表（带缓存）
     * 查询流程：先查Redis缓存 -> 缓存命中直接返回 -> 缓存未命中查数据库 -> 写入缓存
     *
     * @return 店铺类型列表结果
     */
    @Override
    public Result queryList() {
        String cacheKey = "cache:shop:type";
        
        // 1. 从Redis查询缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(cacheKey);
        
        // 2. 判断缓存是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 缓存存在，将JSON反序列化为ShopType列表并返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        
        // 3. 缓存不存在，从数据库查询店铺类型列表（按sort字段升序排列）
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        
        // 4. 判断查询结果是否为空
        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        
        // 5. 将查询结果转换为JSON并写入Redis缓存
        String json = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(cacheKey, json);

        return Result.ok(shopTypes);
    }
}
