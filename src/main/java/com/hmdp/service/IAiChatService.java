package com.hmdp.service;

import com.hmdp.dto.ChatMessageDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface IAiChatService {

    /**
     * 流式对话（SSE）
     * @param userId    登录用户ID（来自 UserHolder，未登录传 null）
     * @param sessionId 未登录用户的会话ID（来自前端）
     * @param userMessage 本次用户输入
     * @param emitter SSE 发射器
     */
    void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter);

    /**
     * 获取历史消息
     * @param userId 用户ID
     * @return 消息列表
     */
    List<ChatMessageDTO> getHistory(Long userId);

    /**
     * 清空历史消息
     * @param userId 用户ID
     */
    void clearHistory(Long userId);
}

