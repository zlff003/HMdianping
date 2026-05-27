package com.hmdp.mcp.client;

import com.hmdp.config.McpServerConfiguration;
import com.hmdp.mcp.server.McpServerProperties;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 客户端服务（标准 Anthropic MCP Java SDK）。
 *
 * 工具 Schema 使用标准 MCP SDK 的 {@link McpSchema.Tool} 类型定义，
 * 工具执行通过 {@link McpServerConfiguration.ToolExecutor} 直接调用（In-Process Bridge），
 * 零网络开销，适合同一 JVM 内的 AI 对话场景。
 *
 * 如需对外暴露 MCP Server，可将 transport 切换为 sse 模式。
 */
@Slf4j
@Service
public class McpClientService {

    @Resource
    private McpServerProperties mcpServerProperties;

    @Resource
    private List<McpSchema.Tool> mcpTools;

    @Resource
    private McpServerConfiguration.ToolExecutor toolExecutor;

    /**
     * 获取所有可用工具的 Schema（标准 MCP Tool 类型）
     */
    public List<McpSchema.Tool> listTools() {
        if (!mcpServerProperties.isEnabled()) return Collections.emptyList();
        return mcpTools;
    }

    /**
     * 将工具定义格式化为 LLM System Prompt 中的工具描述文本
     */
    public String buildToolsPromptSection() {
        List<McpSchema.Tool> tools = listTools();
        if (tools.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("你拥有以下工具能力，请根据用户问题自主决定是否调用：\n\n");
        int idx = 1;
        for (McpSchema.Tool tool : tools) {
            sb.append(idx++).append(". **").append(tool.name()).append("**");
            String desc = tool.description();
            if (desc != null && !desc.trim().isEmpty()) {
                String[] parts = desc.split("\n");
                sb.append(": ").append(parts[0]); // 仅保留第一行作为描述
            }
            sb.append("\n");

            // 输出参数说明（properties 是 Map<String, Object>，值为 JsonSchema 片段 Map）
            McpSchema.JsonSchema inputSchema = tool.inputSchema();
            if (inputSchema != null && inputSchema.properties() != null) {
                Map<String, Object> props = inputSchema.properties();
                @SuppressWarnings("unchecked")
                List<String> required = inputSchema.required() != null
                        ? (List<String>) inputSchema.required() : Collections.emptyList();
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    String paramName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propMap = (Map<String, Object>) entry.getValue();
                    String type = propMap.getOrDefault("type", "string").toString();
                    String paramDesc = propMap.getOrDefault("description", "").toString();
                    boolean isRequired = required.contains(paramName);
                    sb.append("  - ").append(paramName)
                            .append(" (").append(type)
                            .append(isRequired ? ", 必填" : ", 可选")
                            .append("): ").append(paramDesc).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 执行指定的工具并返回结果字符串
     *
     * @param toolName 工具名称
     * @param params   参数映射
     * @return 工具执行结果文本
     */
    public String executeTool(String toolName, Map<String, Object> params) {
        if (!mcpServerProperties.isEnabled()) {
            return "MCP Server 已禁用";
        }
        log.info("[MCP-Client] 调用工具: {} 参数: {}", toolName, params);
        try {
            return toolExecutor.execute(toolName, params);
        } catch (Exception e) {
            log.error("[MCP-Client] 工具 {} 执行失败", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 获取工具名称列表（用于日志/调试）
     */
    public List<String> getToolNames() {
        return listTools().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toList());
    }
}
