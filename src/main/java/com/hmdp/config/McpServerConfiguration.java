package com.hmdp.config;

import com.hmdp.mcp.server.McpServerProperties;
import com.hmdp.mcp.tools.BlogTools;
import com.hmdp.mcp.tools.OrderTools;
import com.hmdp.mcp.tools.ShopTools;
import com.hmdp.mcp.tools.VoucherTools;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准 MCP (Anthropic) 工具注册与执行器配置。
 * <p>
 * 同时提供两条工具调用路径：
 * <ul>
 *   <li><b>标准 MCP Server</b>：通过 SSE 传输暴露标准 MCP JSON-RPC 协议
 *       （tools/list、tools/call），可供外部 MCP Client 连接</li>
 *   <li><b>In-Process Bridge</b>：供 AiChatServiceImpl 内部直接调用，零网络开销</li>
 * </ul>
 */
@Slf4j
@Configuration
public class McpServerConfiguration {

    @Resource
    private McpServerProperties mcpServerProperties;

    @Resource
    private ShopTools shopTools;

    @Resource
    private VoucherTools voucherTools;

    @Resource
    private OrderTools orderTools;

    @Resource
    private BlogTools blogTools;

    // ===================== 标准 MCP Server Bean =====================

    /**
     * MCP JSON Mapper（基于 Jackson 3.x）。
     * 为 McpServer 构建和传输层提供 JSON 序列化能力。
     */
    @Bean
    public McpJsonMapper mcpJsonMapper() {
        return new JacksonMcpJsonMapper(JsonMapper.builder().build());
    }

    /**
     * MCP SSE 传输提供者。
     * 继承 HttpServlet，自动处理 SSE 连接和 JSON-RPC 消息。
     */
    @Bean
    public HttpServletSseServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper) {
        return HttpServletSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .baseUrl(mcpServerProperties.getBaseUrl())
                .sseEndpoint(mcpServerProperties.getSseEndpoint())
                .messageEndpoint(mcpServerProperties.getMessageEndpoint())
                .build();
    }

    /**
     * 将 MCP Transport Servlet 注册到 Spring Boot 容器。
     * 仅在 ai.mcp.server.enabled=true 时生效。
     */
    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpServletRegistration(
            HttpServletSseServerTransportProvider transportProvider) {
        if (!mcpServerProperties.isEnabled()) {
            log.info("[MCP-Server] MCP Server 已禁用，不注册 SSE 端点");
            return null;
        }
        ServletRegistrationBean<HttpServletSseServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider);
        registration.addUrlMappings(
                mcpServerProperties.getSseEndpoint(),
                mcpServerProperties.getMessageEndpoint());
        log.info("[MCP-Server] MCP SSE 端点已注册: {} + {}",
                mcpServerProperties.getSseEndpoint(),
                mcpServerProperties.getMessageEndpoint());
        return registration;
    }

    /**
     * 标准 MCP SyncServer（单会话模式）。
     * 注册全部 6 个业务工具，实现标准 MCP JSON-RPC 协议。
     */
    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer mcpSyncServer(
            HttpServletSseServerTransportProvider transportProvider,
            McpJsonMapper jsonMapper) {

        McpSyncServer server = McpServer.sync(transportProvider)
                .jsonMapper(jsonMapper)
                .serverInfo("hm-dianping", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .toolCall(searchShopsSchema(), this::handleSearchShops)
                .toolCall(getShopDetailSchema(), this::handleGetShopDetail)
                .toolCall(getVouchersSchema(), this::handleGetVouchers)
                .toolCall(getSeckillVouchersSchema(), this::handleGetSeckillVouchers)
                .toolCall(queryOrderStatusSchema(), this::handleQueryOrderStatus)
                .toolCall(getHotBlogsSchema(), this::handleGetHotBlogs)
                .build();

        log.info("[MCP-Server] 标准 MCP Server 已启动，注册 {} 个工具，SSE: {}",
                server.listTools() != null ? server.listTools().size() : 0,
                mcpServerProperties.getSseEndpoint());
        return server;
    }

    // ===================== 工具 Schema 定义 =====================

    private McpSchema.Tool searchShopsSchema() {
        return McpSchema.Tool.builder()
                .name("search_shops")
                .description("搜索附近的美食店铺，支持按关键词和店铺类型筛选")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        orderedMapOf(
                                "keyword", orderedMapOf("type", "string", "description", "搜索关键词，如'火锅'、'日料'、'咖啡'"),
                                "typeId", orderedMapOf("type", "integer", "description", "店铺类型ID，如1=美食、2=娱乐（可选）"),
                                "pageSize", orderedMapOf("type", "integer", "description", "返回结果数量上限，默认10（可选）")
                        ),
                        List.of("keyword"),
                        false, null, null))
                .build();
    }

    private McpSchema.Tool getShopDetailSchema() {
        return McpSchema.Tool.builder()
                .name("get_shop_detail")
                .description("查询指定店铺的详细信息，包括评分、营业时间、地址、人均价格")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        orderedMapOf("shopId", orderedMapOf("type", "integer", "description", "店铺ID")),
                        List.of("shopId"),
                        false, null, null))
                .build();
    }

    private McpSchema.Tool getVouchersSchema() {
        return McpSchema.Tool.builder()
                .name("get_vouchers")
                .description("查询指定店铺可领取的优惠券，包括券名、面值、使用条件、库存")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        orderedMapOf("shopId", orderedMapOf("type", "integer", "description", "店铺ID")),
                        List.of("shopId"),
                        false, null, null))
                .build();
    }

    private McpSchema.Tool getSeckillVouchersSchema() {
        return McpSchema.Tool.builder()
                .name("get_seckill_vouchers")
                .description("查询指定店铺的秒杀优惠券活动信息，包括秒杀价、库存、活动时间")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        orderedMapOf("shopId", orderedMapOf("type", "integer", "description", "店铺ID")),
                        List.of("shopId"),
                        false, null, null))
                .build();
    }

    private McpSchema.Tool queryOrderStatusSchema() {
        return McpSchema.Tool.builder()
                .name("query_order_status")
                .description("查询指定用户的订单列表或指定订单的详情状态")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        orderedMapOf(
                                "userId", orderedMapOf("type", "integer", "description", "用户ID"),
                                "orderId", orderedMapOf("type", "integer", "description", "订单ID（可选，不传则返回用户最近订单）")
                        ),
                        List.of("userId"),
                        false, null, null))
                .build();
    }

    private McpSchema.Tool getHotBlogsSchema() {
        return McpSchema.Tool.builder()
                .name("get_hot_blogs")
                .description("查询热门探店笔记，可按店铺筛选")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        orderedMapOf(
                                "shopId", orderedMapOf("type", "integer", "description", "店铺ID（可选，不传则查全局热门）"),
                                "limit", orderedMapOf("type", "integer", "description", "返回条数，默认5（可选）")
                        ),
                        List.of(),
                        false, null, null))
                .build();
    }

    // ===================== 标准 MCP 工具 Handler =====================

    private McpSchema.CallToolResult handleSearchShops(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String text = shopTools.searchShops(
                getStringArg(args, "keyword"),
                getLongArg(args, "typeId"),
                getIntArg(args, "pageSize"));
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private McpSchema.CallToolResult handleGetShopDetail(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        String text = shopTools.getShopDetail(
                getLongArg(request.arguments(), "shopId"));
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private McpSchema.CallToolResult handleGetVouchers(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        String text = voucherTools.getVouchers(
                getLongArg(request.arguments(), "shopId"));
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private McpSchema.CallToolResult handleGetSeckillVouchers(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        String text = voucherTools.getSeckillVouchers(
                getLongArg(request.arguments(), "shopId"));
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private McpSchema.CallToolResult handleQueryOrderStatus(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String text = orderTools.queryOrderStatus(
                getLongArg(args, "userId"),
                getLongArg(args, "orderId"));
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private McpSchema.CallToolResult handleGetHotBlogs(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String text = blogTools.getHotBlogs(
                getLongArg(args, "shopId"),
                getIntArg(args, "limit"));
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    // ===================== 保留：供 AiChatServiceImpl 使用的 Bean =====================

    /**
     * 所有已注册的工具 Schema 列表。
     * 与 MCP Server 注册的工具使用相同的 Schema 定义（私有方法），保证一致性。
     * 独立于 McpSyncServer，因此即使 MCP Server 禁用也能正常为内部 AI 客服提供服务。
     */
    @Bean
    public List<McpSchema.Tool> mcpTools() {
        List<McpSchema.Tool> tools = new ArrayList<>();
        tools.add(searchShopsSchema());
        tools.add(getShopDetailSchema());
        tools.add(getVouchersSchema());
        tools.add(getSeckillVouchersSchema());
        tools.add(queryOrderStatusSchema());
        tools.add(getHotBlogsSchema());
        log.info("[MCP-Config] 已注册 {} 个工具 Schema（内部 AI 客服使用）", tools.size());
        return tools;
    }

    /**
     * MCP 工具执行器（In-Process Bridge）。
     * 直接调用业务方法，返回纯文本字符串，供 AiChatServiceImpl 的 ReAct 循环使用。
     */
    @Bean
    public ToolExecutor toolExecutor() {
        return (toolName, params) -> {
            McpSchema.CallToolResult result = executeToolDirectly(toolName, params);
            if (result == null || result.content() == null || result.content().isEmpty()) {
                return "工具执行完成（无返回内容）";
            }
            StringBuilder sb = new StringBuilder();
            for (McpSchema.Content c : result.content()) {
                if (c instanceof McpSchema.TextContent tc) {
                    sb.append(tc.text());
                }
            }
            return sb.toString();
        };
    }

    /**
     * 直接执行工具（零网络开销，同 JVM 内调用）。
     */
    private McpSchema.CallToolResult executeToolDirectly(String toolName, Map<String, Object> args) {
        try {
            String textResult = switch (toolName) {
                case "search_shops" -> shopTools.searchShops(
                        getStringArg(args, "keyword"),
                        getLongArg(args, "typeId"),
                        getIntArg(args, "pageSize"));
                case "get_shop_detail" -> shopTools.getShopDetail(
                        getLongArg(args, "shopId"));
                case "get_vouchers" -> voucherTools.getVouchers(
                        getLongArg(args, "shopId"));
                case "get_seckill_vouchers" -> voucherTools.getSeckillVouchers(
                        getLongArg(args, "shopId"));
                case "query_order_status" -> orderTools.queryOrderStatus(
                        getLongArg(args, "userId"),
                        getLongArg(args, "orderId"));
                case "get_hot_blogs" -> blogTools.getHotBlogs(
                        getLongArg(args, "shopId"),
                        getIntArg(args, "limit"));
                default -> "未知工具: " + toolName;
            };
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(textResult)), false, null, null);
        } catch (Exception e) {
            log.error("[MCP-Config] 工具 {} 执行失败", toolName, e);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("工具执行失败: " + e.getMessage())),
                    true, null, null);
        }
    }

    // ==================== 工具接口 ====================

    /**
     * MCP 工具执行器接口（In-Process Bridge）。
     */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String toolName, Map<String, Object> params);
    }

    // ==================== 辅助方法 ====================

    @SafeVarargs
    private static Map<String, Object> orderedMapOf(String k1, Object v1, Object... rest) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        for (int i = 0; i < rest.length; i += 2) {
            map.put((String) rest[i], rest[i + 1]);
        }
        return map;
    }

    private static String getStringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private static Long getLongArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer getIntArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
