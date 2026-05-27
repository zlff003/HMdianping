package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.impl.KnowledgeIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 知识库管理接口（供管理员调用）
 */
@Slf4j
@RestController
@RequestMapping("/ai/knowledge")
public class KnowledgeAdminController {

    @Resource
    private KnowledgeIndexService knowledgeIndexService;

    /**
     * 重建向量索引（当知识库文档更新后调用）
     * POST /ai/knowledge/rebuild
     */
    @PostMapping("/rebuild")
    public Result rebuild() {
        log.info("[RAG] 手动触发知识库索引重建");
        knowledgeIndexService.rebuildIndex();
        return Result.ok("知识库索引重建完成");
    }

    /**
     * 清空向量索引
     * DELETE /ai/knowledge/index
     */
    @DeleteMapping("/index")
    public Result clearIndex() {
        knowledgeIndexService.clearIndex();
        return Result.ok("向量索引已清空");
    }
}
