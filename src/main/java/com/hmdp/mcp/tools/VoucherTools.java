package com.hmdp.mcp.tools;

import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优惠券相关工具（标准 MCP 工具）。
 * 工具注册由 McpServerConfiguration 通过 MCP SDK 完成。
 */
@Slf4j
@Component
public class VoucherTools {

    @Autowired
    private VoucherMapper voucherMapper;

    public String getVouchers(Long shopId) {

        log.info("[MCP] get_vouchers: shopId={}", shopId);
        if (shopId == null) return "缺少店铺ID参数";
        try {
            List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);
            if (vouchers == null || vouchers.isEmpty()) return "该店铺当前没有可领取的优惠券";
            StringBuilder sb = new StringBuilder("该店铺有以下优惠券：\n");
            for (Voucher v : vouchers) {
                sb.append("- ").append(v.getTitle())
                        .append(" | 面值: ").append(v.getPayValue() != null ? v.getPayValue() : "免费").append("元")
                        .append(" | 条件: ").append(v.getRules() != null ? v.getRules() : "无限制")
                        .append(" | 剩余: ").append(v.getStock() != null ? v.getStock() : "充足");
                if (v.getBeginTime() != null && v.getEndTime() != null) {
                    sb.append(" | 时间: ").append(v.getBeginTime()).append(" ~ ").append(v.getEndTime());
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[MCP] get_vouchers 失败 shopId={}", shopId, e);
            return "查询优惠券失败：" + e.getMessage();
        }
    }

    public String getSeckillVouchers(Long shopId) {

        log.info("[MCP] get_seckill_vouchers: shopId={}", shopId);
        if (shopId == null) return "缺少店铺ID参数";
        try {
            List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);
            if (vouchers == null || vouchers.isEmpty()) return "该店铺当前没有秒杀活动";

            StringBuilder sb = new StringBuilder("秒杀优惠券信息：\n");
            boolean hasSeckill = false;
            for (Voucher v : vouchers) {
                if (v.getBeginTime() != null && v.getEndTime() != null && v.getStock() != null) {
                    hasSeckill = true;
                    sb.append("- ").append(v.getTitle())
                            .append(" | 秒杀价: ").append(v.getPayValue() != null ? v.getPayValue() : 0).append("元")
                            .append(" | 剩余: ").append(v.getStock())
                            .append(" | 时间: ").append(v.getBeginTime()).append(" ~ ").append(v.getEndTime())
                            .append("\n");
                }
            }

            if (!hasSeckill) return "该店铺当前没有秒杀活动";
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[MCP] get_seckill_vouchers 失败 shopId={}", shopId, e);
            return "查询秒杀券失败：" + e.getMessage();
        }
    }
}
