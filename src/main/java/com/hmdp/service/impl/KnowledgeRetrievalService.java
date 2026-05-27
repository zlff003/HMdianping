package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.hmdp.config.AiProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索服务
 * 将用户问题 Embedding 后，在 Redis 中做余弦相似度检索，返回最相关的 Top-K 文本片段
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiProperties aiProperties;

    /**
     * 检索与问题最相关的文档片段
     *
     * @param question 用户问题
     * @return 相关文本片段列表（按相似度降序），用于注入 Prompt
     */
    public List<String> retrieve(String question) {
        // 1. 对问题向量化
        Response<Embedding> embResp = embeddingModel.embed(question);
        float[] questionVec = embResp.content().vector();

        // 2. 获取所有 chunk ID
        Set<String> ids = stringRedisTemplate.opsForSet().members(KnowledgeIndexService.CHUNK_SET_KEY);
        if (ids == null || ids.isEmpty()) {
            log.warn("[RAG] 向量库为空，跳过检索");
            return Collections.emptyList();
        }

        int topK = aiProperties.getRag().getTopK();
        double minScore = aiProperties.getRag().getMinScore();

        // 3. 遍历所有 chunk，计算余弦相似度
        List<ScoredChunk> scored = new ArrayList<>();
        for (String id : ids) {
            String hashKey = KnowledgeIndexService.CHUNK_HASH_PREFIX + id;
            String content = (String) stringRedisTemplate.opsForHash().get(hashKey, "content");
            String vectorJson = (String) stringRedisTemplate.opsForHash().get(hashKey, "vector");
            if (content == null || vectorJson == null) continue;

            float[] chunkVec = toFloatArray(JSON.parseArray(vectorJson));
            double score = cosineSimilarity(questionVec, chunkVec);
            if (score >= minScore) {
                scored.add(new ScoredChunk(content, score));
            }
        }

        // 4. 按相似度降序，取 Top-K
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .map(ScoredChunk::getContent)
                .collect(Collectors.toList());
    }

    // -------------------- 工具方法 --------------------

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    private float[] toFloatArray(com.alibaba.fastjson.JSONArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.getFloat(i);
        }
        return result;
    }

    private static class ScoredChunk {
        private final String content;
        private final double score;
        ScoredChunk(String content, double score) { this.content = content; this.score = score; }
        String getContent() { return content; }
        double getScore() { return score; }
    }
}
