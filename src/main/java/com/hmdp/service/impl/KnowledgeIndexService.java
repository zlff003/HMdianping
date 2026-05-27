package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.hmdp.config.AiProperties;
import com.hmdp.entity.DocumentChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 知识库索引服务
 * 负责：文档读取 → 按段落分块 → Embedding 向量化 → 存储到 Redis
 */
@Slf4j
@Service
public class KnowledgeIndexService {

    /** Redis 向量库 Key：存储所有 chunk ID 的 Set */
    public static final String CHUNK_SET_KEY = "rag:chunks";
    /** 每个 chunk 的 Hash Key 前缀 */
    public static final String CHUNK_HASH_PREFIX = "rag:chunk:";

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiProperties aiProperties;

    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rag-index-thread");
        t.setDaemon(true);
        return t;
    });

    /**
     * 应用启动后异步执行索引，仅当 Redis 中无向量数据时才重建
     */
    @PostConstruct
    public void initIndex() {
        indexExecutor.submit(() -> {
            try {
                Long count = stringRedisTemplate.opsForSet().size(CHUNK_SET_KEY);
                if (count != null && count > 0) {
                    log.info("[RAG] Redis 中已有 {} 个向量片段，跳过重建索引", count);
                    return;
                }
                log.info("[RAG] 开始构建知识库向量索引...");
                buildIndex();
                log.info("[RAG] 知识库向量索引构建完成");
            } catch (Exception e) {
                log.error("[RAG] 向量索引构建失败", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        indexExecutor.shutdown();
    }

    /**
     * 强制重建索引（清空旧数据后重新生成）
     */
    public void rebuildIndex() {
        clearIndex();
        try {
            buildIndex();
            log.info("[RAG] 知识库索引重建完成");
        } catch (Exception e) {
            log.error("[RAG] 知识库索引重建失败", e);
            throw new RuntimeException("知识库索引重建失败: " + e.getMessage(), e);
        }
    }

    public void clearIndex() {
        Set<String> ids = stringRedisTemplate.opsForSet().members(CHUNK_SET_KEY);
        if (ids != null && !ids.isEmpty()) {
            ids.forEach(id -> stringRedisTemplate.delete(CHUNK_HASH_PREFIX + id));
        }
        stringRedisTemplate.delete(CHUNK_SET_KEY);
        log.info("[RAG] 已清空向量索引");
    }

    // -------------------- 私有方法 --------------------

    private void buildIndex() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = aiProperties.getRag().getKnowledgeDir() + "*.txt";
        org.springframework.core.io.Resource[] resources = resolver.getResources(pattern);

        for (org.springframework.core.io.Resource res : resources) {
            String fileName = res.getFilename();
            log.info("[RAG] 正在处理文档: {}", fileName);
            String text = readText(res);
            List<String> chunks = splitIntoChunks(text,
                    aiProperties.getRag().getChunkSize(),
                    aiProperties.getRag().getChunkOverlap());
            log.info("[RAG] 文档 {} 分成 {} 个片段，开始 Embedding...", fileName, chunks.size());

            for (int i = 0; i < chunks.size(); i++) {
                String content = chunks.get(i);
                Response<Embedding> embResp = embeddingModel.embed(content);
                float[] vector = embResp.content().vector();

                DocumentChunk chunk = new DocumentChunk(
                        UUID.randomUUID().toString().replace("-", ""),
                        fileName,
                        content,
                        vector
                );
                saveChunk(chunk);
                log.debug("[RAG] 已存储片段 {}/{} from {}", i + 1, chunks.size(), fileName);
            }
        }
    }

    private void saveChunk(DocumentChunk chunk) {
        String hashKey = CHUNK_HASH_PREFIX + chunk.getId();
        stringRedisTemplate.opsForHash().put(hashKey, "id", chunk.getId());
        stringRedisTemplate.opsForHash().put(hashKey, "source", chunk.getSource());
        stringRedisTemplate.opsForHash().put(hashKey, "content", chunk.getContent());
        stringRedisTemplate.opsForHash().put(hashKey, "vector", JSON.toJSONString(chunk.getVector()));
        stringRedisTemplate.opsForSet().add(CHUNK_SET_KEY, chunk.getId());
    }

    private String readText(org.springframework.core.io.Resource res) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 先按双换行（段落边界）分块，超长段落按 chunkSize 强制截断
     */
    private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        String[] paragraphs = text.split("\n\n+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (current.length() + para.length() + 1 <= chunkSize) {
                if (current.length() > 0) current.append("\n");
                current.append(para);
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    String tail = current.toString();
                    current = new StringBuilder(
                            tail.length() > overlap ? tail.substring(tail.length() - overlap) : tail
                    );
                    current.append("\n");
                }
                if (para.length() > chunkSize) {
                    int start = 0;
                    while (start < para.length()) {
                        int end = Math.min(start + chunkSize, para.length());
                        chunks.add(para.substring(start, end));
                        start += chunkSize - overlap;
                    }
                } else {
                    current.append(para);
                }
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
