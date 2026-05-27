package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hmdp.dto.ChatMessageDTO;
import com.hmdp.mcp.client.McpClientService;
import com.hmdp.service.IAiChatService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final String HISTORY_KEY_PREFIX = "ai:chat:history:";
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final long SESSION_TTL_SECONDS = 30 * 60L;

    private static final String SYSTEM_PROMPT_TEMPLATE =
            "你是「黑马点评」App 的 AI 智能客服，专门解答美食推荐、店铺查询、优惠券使用、" +
            "订单问题等方面的咨询。性格友好、热情，专业。\n\n" +
            "{mcp_tools}\n" +
            "【重要规则】\n" +
            "- 当用户询问某个具体店铺的优惠券时，必须按顺序调用：先 search_shops 获取 shopId，再 get_vouchers 查询优惠券\n" +
            "- 如果用户问题中包含'优惠券'、'有什么券'、'有没有优惠'等关键词，且提到了店铺名称，必须查询优惠券\n" +
            "- 绝对不要编造店铺信息、优惠券信息或要求用户提供额外信息，必须使用工具获取真实数据\n" +
            "- 如果 search_shops 返回多个结果，选择最匹配的一个进行后续查询\n" +
            "- 如果用户问的是通用问题（如'怎么用优惠券'），可以直接回答，无需调用工具\n\n" +
            "【工具调用格式】\n" +
            "如果需要调用工具，请按以下JSON格式输出（一行一个，可连续输出多个）：\n" +
            "{\"name\":\"工具名\",\"arguments\":{\"参数名\":\"值\"}}\n\n" +
            "如果不需要调用工具，直接回答即可。\n\n" +
            "下方是知识库参考资料（可能有用）：\n" +
            "{rag_context}";

    /** 从 LLM 响应中提取 JSON 工具调用: {"name":"xxx","arguments":{...}} */
    private static final Pattern TOOL_CALL_JSON_PATTERN = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"(\\w+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[^}]+\\})\\s*\\}",
            Pattern.DOTALL
    );

    @Resource
    private ChatModel chatModel;

    @Resource
    private McpClientService mcpClientService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Override
    public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter) {
        String effectiveId = (userId != null) ? String.valueOf(userId)
                : (sessionId != null ? sessionId : "anonymous");

        try {
            // 1. RAG 检索
            List<String> ragContexts = Collections.emptyList();
            try {
                ragContexts = knowledgeRetrievalService.retrieve(userMessage);
            } catch (Exception e) {
                log.warn("[RAG] 检索失败: {}", e.getMessage());
            }

            // 2. 构建完整 SystemPrompt（工具列表从 MCP Server 动态获取）
            String ragText = buildRagText(ragContexts);
            String toolsText = mcpClientService.buildToolsPromptSection();
            String fullSystemPrompt = SYSTEM_PROMPT_TEMPLATE
                    .replace("{mcp_tools}", toolsText)
                    .replace("{rag_context}", ragText);

            // 3. 存用户消息
            appendHistory(effectiveId, new ChatMessageDTO("user", userMessage));

            // 4. ReAct 模式：先让 LLM 判断是否需要调用工具
            log.info("[MCP] 推理阶段，用户消息: {}", userMessage);
            String llmFirstResponse = chatModel.chat(
                    SystemMessage.from(fullSystemPrompt),
                    UserMessage.from(userMessage)
            ).aiMessage().text();
            log.info("[MCP] LLM 第一轮回复: {}", llmFirstResponse);

            // 5. 智能检测：如果 LLM 没有调用工具，但问题包含店铺/优惠券关键词，强制搜索
            String enhancedResponse = llmFirstResponse;
            if (!containsToolCall(llmFirstResponse) &&
                (userMessage.contains("店") || userMessage.contains("铺") || userMessage.contains("优惠券"))) {
                log.info("[MCP] 检测到可能需要工具调用，自动搜索店铺");
                String keyword = extractShopKeyword(userMessage);
                enhancedResponse = "{\"name\":\"search_shops\",\"arguments\":{\"keyword\":\"" + keyword + "\"}}\n" + llmFirstResponse;
            }

            // 6. 通过 MCP 执行工具调用（ReAct 循环，最多3轮）
            String finalAnswer = executeMcpToolLoop(enhancedResponse, fullSystemPrompt, userMessage);

            log.info("[MCP] 最终回答: {}", finalAnswer);

            // 7. 流式推送
            pushStream(emitter, finalAnswer, effectiveId);

        } catch (Exception e) {
            log.error("[AI] chatStream 异常 userId={}", effectiveId, e);
            try {
                emitter.send(SseEmitter.event().name("error").data("服务异常，请稍后再试"));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    /**
     * MCP 工具调用循环（最多3轮）。
     * 从 LLM 响应中提取 JSON 格式工具调用，通过 MCP Client 执行，
     * 将工具结果回传给 LLM 继续推理，防止重复调用同一工具。
     */
    private String executeMcpToolLoop(String llmResponse, String systemPrompt, String userQuestion) {
        String current = llmResponse;
        Long extractedShopId = null; // 从 search_shops 结果中提取的 shopId，用于后续优惠券查询
        Set<String> calledToolNames = new LinkedHashSet<>(); // 记录已调用的工具名，防止重复
        List<String> toolResultHistory = new ArrayList<>(); // 记录所有工具执行结果，用于回传 LLM

        for (int loop = 0; loop < 3; loop++) {
            Matcher matcher = TOOL_CALL_JSON_PATTERN.matcher(current);
            if (!matcher.find()) break;

            String toolName = matcher.group(1).trim();
            String rawParams = matcher.group(2).trim();

            // 解析参数 JSON
            Map<String, Object> params = parseParams(rawParams);

            // 防止重复调用同一工具（已在之前轮次调用过）
            if (calledToolNames.contains(toolName)) {
                log.warn("[MCP] 工具 {} 已被调用过，移除当前工具调用并跳过", toolName);
                current = current.substring(matcher.end());
                loop--; // 不计入循环次数，继续处理剩余内容
                continue;
            }
            calledToolNames.add(toolName);

            // 特殊处理：优惠券查询如果缺少 shopId，自动补充之前搜索到的 shopId
            if (("get_vouchers".equals(toolName) || "get_seckill_vouchers".equals(toolName))
                    && !params.containsKey("shopId") && extractedShopId != null) {
                params.put("shopId", extractedShopId);
                log.info("[MCP] 自动补充 shopId: {}", extractedShopId);
            }

            // 通过 MCP Client 执行工具
            String toolResult;
            try {
                toolResult = mcpClientService.executeTool(toolName, params);
            } catch (Exception e) {
                log.error("[MCP] 工具 {} 调用失败", toolName, e);
                toolResult = "工具调用失败: " + e.getMessage();
            }
            log.info("[MCP] 工具 {} 执行结果: {}", toolName, toolResult);

            // 记录工具执行结果，用于后续回传给 LLM
            String toolCallSummary = "调用 " + toolName + "(" + rawParams + ")";
            toolResultHistory.add(toolCallSummary + " → " + toolResult);

            // 如果是 search_shops，尝试从结果中提取第一个店铺的 ID（供后续查询使用）
            if ("search_shops".equals(toolName) && toolResult.contains("ID:")) {
                try {
                    Pattern idPattern = Pattern.compile("ID:\\s*(\\d+)");
                    Matcher idMatcher = idPattern.matcher(toolResult);
                    if (idMatcher.find()) {
                        extractedShopId = Long.parseLong(idMatcher.group(1));
                        log.info("[MCP] 从搜索结果中提取到 shopId: {}", extractedShopId);
                    }
                } catch (Exception e) {
                    log.warn("[MCP] 提取 shopId 失败", e);
                }
            }

            // 提取剩余未处理内容（去掉当前工具调用的 JSON）
            current = current.substring(matcher.end());
            if (current.trim().isEmpty()) {
                // 工具结果之后没有更多 LLM 内容，让 LLM 基于真实工具结果继续推理
                String nextPrompt = buildNextPrompt(systemPrompt, userQuestion, toolResultHistory);
                log.info("[MCP] 第{}轮工具执行后调用 LLM 继续推理", loop + 1);
                current = chatModel.chat(
                        UserMessage.from(nextPrompt)
                ).aiMessage().text();
                log.info("[MCP] LLM 第{}轮回复: {}", loop + 1, current);
            }
        }

        String result = cleanLlmResponse(current);
        if (result.isEmpty()) {
            // 回退：用工具结果构造兜底回答
            result = buildFallbackAnswer(toolResultHistory);
        }
        return result;
    }

    /**
     * 构建回传给 LLM 的提示词，包含已执行工具的真实结果。
     * 明确告知 LLM 哪些工具已调用、结果是什么、不要重复调用。
     */
    private String buildNextPrompt(String systemPrompt, String userQuestion,
                                    List<String> toolResultHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt);
        sb.append("\n\n【用户问题】\n").append(userQuestion);
        sb.append("\n\n【已执行的工具调用及结果】\n");
        for (int i = 0; i < toolResultHistory.size(); i++) {
            sb.append((i + 1)).append(". ").append(toolResultHistory.get(i)).append("\n");
        }
        sb.append("\n【当前指令】\n");
        sb.append("上面是已经完成的工具调用及真实结果，请勿重复调用已执行过的工具。\n");
        sb.append("如果还需要更多信息（如查询优惠券、秒杀活动），请调用对应工具。\n");
        sb.append("如果信息已足够，请直接给出最终回答（简洁，不超过200字）。");
        return sb.toString();
    }

    /**
     * 兜底回答：当 LLM 最终返回空时，用工具结果拼接一段可用的回答。
     */
    private String buildFallbackAnswer(List<String> toolResultHistory) {
        if (toolResultHistory.isEmpty()) {
            return "抱歉，暂未找到相关信息，建议联系人工客服";
        }
        // 把最后一次工具执行结果的"→"后面的内容提取出来
        String lastResult = toolResultHistory.get(toolResultHistory.size() - 1);
        int arrowIdx = lastResult.lastIndexOf("→ ");
        String rawData = arrowIdx >= 0 ? lastResult.substring(arrowIdx + 2).trim() : lastResult;
        // 去掉工具调用前缀格式，只留核心数据
        rawData = rawData.replace("调用 ", "").trim();
        return "根据查询结果，" + rawData + "\n\n如需了解更多信息，请继续问我哦！";
    }

    private boolean containsToolCall(String response) {
        if (response == null) return false;
        return TOOL_CALL_JSON_PATTERN.matcher(response).find();
    }

    private Map<String, Object> parseParams(String rawParams) {
        try {
            JSONObject json = JSON.parseObject(rawParams);
            return new LinkedHashMap<>(json);
        } catch (Exception e) {
            log.warn("[MCP] 参数JSON解析失败: {}", rawParams, e);
            return Collections.emptyMap();
        }
    }

    private String cleanLlmResponse(String text) {
        if (text == null) return "";
        text = TOOL_CALL_JSON_PATTERN.matcher(text).replaceAll("");
        return text.trim();
    }

    /**
     * 从用户消息中提取店铺名称关键词。
     * 去除常见的问句后缀（"有没有优惠券"、"在哪里"等），保留核心店铺名。
     */
    private String extractShopKeyword(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) return userMessage;
        // 截断到20字，优先保留前半段（通常是店铺名）
        String msg = userMessage.trim();
        if (msg.length() > 20) {
            msg = msg.substring(0, 20);
        }
        // 去掉常见问句后缀，保留店铺名部分
        // 匹配模式: "xxx有没有" / "xxx有什么" / "xxx在哪里" / "xxx怎么样" / "xxx的优惠" 等
        msg = msg.replaceAll("(有没有|有什么|在哪里|怎么样|好不好|多少钱|贵不贵|几点|什么时候|怎么).*$", "");
        msg = msg.replaceAll("(的优惠券|的秒杀|的券|的详细信息|的详情|的地址|的电话).*$", "");
        msg = msg.replaceAll("附近|好吃的|帮我|麻烦|请问|我想|我要|给我|推荐|搜索|查一下|一下", "");
        msg = msg.trim();
        return msg.isEmpty() ? userMessage.trim().substring(0, Math.min(userMessage.trim().length(), 20)) : msg;
    }

    // ==================== SSE 流式推送 & 历史管理 ====================

    /**
     * 逐字流式推送最终回答到前端。
     * 直接将文本按小片段分批发给 SSE，不再经过 LLM，
     * 避免 LLM 误解文本内容而产生改写/润色等非预期输出。
     */
    private void pushStream(SseEmitter emitter, String text, String effectiveId) {
        if (text == null || text.isEmpty()) {
            text = "抱歉，暂未找到相关信息，建议联系人工客服";
        }
        final String safeText = text.length() > 500 ? text.substring(0, 500) + "..." : text;
        final String content = safeText;

        new Thread(() -> {
            try {
                // 逐字推送，每批 1~3 字，间隔 30~60ms 模拟打字机效果
                int i = 0;
                while (i < content.length()) {
                    int chunkSize = Math.min(2 + (i % 2), content.length() - i);
                    String chunk = content.substring(i, i + chunkSize);
                    emitter.send(SseEmitter.event().data(chunk));
                    i += chunkSize;
                    Thread.sleep(30 + (long) (Math.random() * 30));
                }
                // 保存完整回复到历史
                appendHistory(effectiveId, new ChatMessageDTO("assistant", content));
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (IOException e) {
                log.warn("[SSE] 推送失败", e);
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            }
        }, "sse-pusher").start();
    }

    @Override
    public List<ChatMessageDTO> getHistory(Long userId) {
        String effectiveId = (userId != null) ? String.valueOf(userId) : "anonymous";
        return getHistoryBySessionId(effectiveId);
    }

    private List<ChatMessageDTO> getHistoryBySessionId(String sessionId) {
        String key = HISTORY_KEY_PREFIX + sessionId;
        List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return raw.stream()
                .map(s -> JSON.parseObject(s, ChatMessageDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public void clearHistory(Long userId) {
        String effectiveId = (userId != null) ? String.valueOf(userId) : "anonymous";
        stringRedisTemplate.delete(HISTORY_KEY_PREFIX + effectiveId);
    }

    // -------------------- 私有辅助 --------------------

    private String buildRagText(List<String> ragContexts) {
        if (ragContexts == null || ragContexts.isEmpty()) return "（暂无参考资料）";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ragContexts.size(); i++) {
            sb.append(i + 1).append(". ").append(ragContexts.get(i)).append("\n");
        }
        return sb.toString();
    }

    private void appendHistory(String sessionId, ChatMessageDTO msg) {
        String key = HISTORY_KEY_PREFIX + sessionId;
        stringRedisTemplate.opsForList().rightPush(key, JSON.toJSONString(msg));
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size != null && size > MAX_HISTORY_MESSAGES) {
            stringRedisTemplate.opsForList().leftPop(key);
        }
        stringRedisTemplate.expire(key, SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
