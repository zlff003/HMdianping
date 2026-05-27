package com.hmdp.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Resource
    private AiProperties aiProperties;

    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(aiProperties.getModel().getApiKey())
                .baseUrl(aiProperties.getModel().getBaseUrl())
                .modelName(aiProperties.getModel().getModelName())
                .timeout(Duration.ofSeconds(aiProperties.getModel().getTimeout()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * 非流式 ChatModel，用于 MCP 工具调用推理阶段
     * LangChain4j 的 tool calling 需要在 ChatLanguageModel（非 Streaming）上执行
     */
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(aiProperties.getModel().getApiKey())
                .baseUrl(aiProperties.getModel().getBaseUrl())
                .modelName(aiProperties.getModel().getModelName())
                .timeout(Duration.ofSeconds(aiProperties.getModel().getTimeout()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(aiProperties.getModel().getApiKey())
                .baseUrl(aiProperties.getModel().getBaseUrl())
                .modelName(aiProperties.getEmbedding().getModelName())
                .timeout(Duration.ofSeconds(aiProperties.getModel().getTimeout()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}

