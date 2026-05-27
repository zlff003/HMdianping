package com.hmdp.controller;

import com.hmdp.dto.ChatMessageDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IAiChatService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private IAiChatService aiChatService;

    /**
     * SSE 流式对话接口
     * GET /ai/chat/stream?message=xxx
     * userId 从 UserHolder 获取，未登录用户用 sessionId（可选）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId) {

        SseEmitter emitter = new SseEmitter(0L);
        if (message == null || message.trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("消息不能为空"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        Long userId = null;
        try {
            userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        } catch (Exception e) {
            log.warn("[AI] 无法从 UserHolder 获取 userId", e);
        }

        log.info("[AI] 对话请求: userId={}, sessionId={}, msg={}",
                userId, sessionId, message.trim());
        
        // Lambda 表达式需要 final 变量，创建副本
        final Long finalUserId = userId;
        final String finalSessionId = sessionId;
        final String finalMessage = message.trim();
        new Thread(() -> aiChatService.chatStream(finalUserId, finalSessionId, finalMessage, emitter)).start();
        return emitter;
    }

    /**
     * 获取历史消息
     * GET /ai/chat/history
     */
    @GetMapping("/chat/history")
    public Result getHistory(@RequestParam(required = false) String sessionId) {
        Long userId = getCurrentUserId();
        log.info("[AI] 获取历史: userId={}", userId);
        List<ChatMessageDTO> history = aiChatService.getHistory(userId);
        return Result.ok(history);
    }

    /**
     * 清空历史消息
     * DELETE /ai/chat/history
     */
    @DeleteMapping("/chat/history")
    public Result clearHistory() {
        Long userId = getCurrentUserId();
        log.info("[AI] 清空历史: userId={}", userId);
        aiChatService.clearHistory(userId);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        try {
            return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
