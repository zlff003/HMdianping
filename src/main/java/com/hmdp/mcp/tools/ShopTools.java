package com.hmdp.mcp.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 店铺相关工具（标准 MCP 工具）。
 * 工具注册由 McpServerConfiguration 通过 MCP SDK 完成，不再使用自定义注解。
 */
@Slf4j
@Component
public class ShopTools {

    @Autowired
    private ShopMapper shopMapper;

    public String searchShops(String keyword, Long typeId, Integer pageSize) {

        log.info("[MCP] search_shops: keyword={}, typeId={}, pageSize={}", keyword, typeId, pageSize);
        try {
            int size = (pageSize != null && pageSize > 0) ? pageSize : 10;
            QueryWrapper<Shop> qw = new QueryWrapper<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                qw.like("name", keyword.trim());
            }
            if (typeId != null) {
                qw.eq("type_id", typeId);
            }
            qw.last("LIMIT " + size);
            List<Shop> shops = shopMapper.selectList(qw);
            if (shops == null || shops.isEmpty()) {
                return "未找到符合条件的店铺";
            }
            StringBuilder sb = new StringBuilder("找到 " + shops.size() + " 家店铺：\n");
            for (Shop shop : shops) {
                sb.append("- 店铺名: ").append(shop.getName())
                        .append(" | 评分: ").append(shop.getScore() != null ? shop.getScore() / 10.0 : "暂无")
                        .append(" | 地址: ").append(shop.getAddress() != null ? shop.getAddress() : "暂无")
                        .append(" | ID: ").append(shop.getId())
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[MCP] search_shops 失败", e);
            return "搜索店铺失败：" + e.getMessage();
        }
    }

    public String getShopDetail(Long shopId) {

        log.info("[MCP] get_shop_detail: shopId={}", shopId);
        if (shopId == null) return "缺少店铺ID参数";
        try {
            Shop shop = shopMapper.selectById(shopId);
            if (shop == null) return "未找到该店铺，shopId=" + shopId;
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(shop.getName()).append("】详情：\n");
            sb.append("评分：").append(shop.getScore() != null ? shop.getScore() / 10.0 : "暂无").append("/10\n");
            if (shop.getOpenHours() != null) sb.append("营业时间：").append(shop.getOpenHours()).append("\n");
            if (shop.getAddress() != null) sb.append("地址：").append(shop.getAddress()).append("\n");
            if (shop.getAvgPrice() != null) sb.append("人均：").append(shop.getAvgPrice()).append("元\n");
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[MCP] get_shop_detail 失败 shopId={}", shopId, e);
            return "查询店铺详情失败：" + e.getMessage();
        }
    }
}
