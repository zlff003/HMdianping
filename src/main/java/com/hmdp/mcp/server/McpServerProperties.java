package com.hmdp.mcp.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP Server 配置属性（Standard Anthropic MCP Java SDK）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.mcp.server")
public class McpServerProperties {
    /** 是否启用 MCP Server */
    private boolean enabled = true;
    /** 传输模式: sse, stdio */
    private String transport = "sse";
    /** 服务基础 URL（用于告知客户端消息端点地址，默认 http://localhost:8081） */
    private String baseUrl = "http://localhost:8081";
    /** SSE 连接端点 */
    private String sseEndpoint = "/mcp/sse";
    /** JSON-RPC 消息端点 */
    private String messageEndpoint = "/mcp/message";
}
