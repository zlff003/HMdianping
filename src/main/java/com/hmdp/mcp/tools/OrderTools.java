package com.hmdp.mcp.tools;

import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 订单相关工具（标准 MCP 工具）。
 * 工具注册由 McpServerConfiguration 通过 MCP SDK 完成。
 */
@Slf4j
@Component
public class OrderTools {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private IVoucherService voucherService;

    public String queryOrderStatus(Long userId, Long orderId) {

        log.info("[MCP] query_order_status: userId={}, orderId={}", userId, orderId);
        if (userId == null) return "缺少用户ID参数";
        try {
            List<VoucherOrder> orders;
            if (orderId != null) {
                VoucherOrder o = voucherOrderService.getById(orderId);
                orders = o != null ? java.util.Arrays.asList(o) : Collections.emptyList();
            } else {
                orders = voucherOrderService.query()
                        .eq("user_id", userId)
                        .orderByDesc("create_time")
                        .last("LIMIT 5").list();
            }
            if (orders == null || orders.isEmpty()) return "未找到相关订单";
            StringBuilder sb = new StringBuilder("找到 " + orders.size() + " 条订单：\n");
            for (VoucherOrder o : orders) {
                Voucher voucher = voucherService.getById(o.getVoucherId());
                String payValue = voucher != null && voucher.getPayValue() != null ? voucher.getPayValue() + "元" : "未知";
                String title = voucher != null && voucher.getTitle() != null ? voucher.getTitle() : "优惠券";
                sb.append("- 订单").append(o.getId())
                        .append(" | 券名: ").append(title)
                        .append(" | 状态: ").append(o.getStatus() != null ? o.getStatus() : "未知")
                        .append(" | 面值: ").append(payValue)
                        .append(" | 时间: ").append(o.getCreateTime() != null ? o.getCreateTime() : "未知")
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[MCP] query_order_status 失败 userId={}", userId, e);
            return "查询订单失败：" + e.getMessage();
        }
    }
}
