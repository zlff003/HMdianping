package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 店铺控制器
 * 提供店铺相关的HTTP接口，包括查询、新增、更新等功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据ID查询店铺信息（使用逻辑过期策略解决缓存击穿）
     *
     * @param id 店铺ID
     * @return 店铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增店铺信息
     *
     * @param shop 店铺数据
     * @return 店铺ID
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺ID
        return Result.ok(shop.getId());
    }

    /**
     * 更新店铺信息（先更新数据库，再删除缓存）
     *
     * @param shop 店铺数据
     * @return 操作结果
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    /**
     * 根据店铺类型分页查询店铺列表
     *
     * @param typeId 店铺类型ID
     * @param current 当前页码（默认为1）
     * @return 店铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        
        return Result.ok(page.getRecords());
    }

    /**
     * 根据店铺名称关键字分页查询店铺列表
     *
     * @param name 店铺名称关键字（可选）
     * @param current 当前页码（默认为1）
     * @return 店铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据名称模糊查询（name不为空时才添加条件）
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        return Result.ok(page.getRecords());
    }
}
