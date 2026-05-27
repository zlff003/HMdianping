package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Model model = new Model();
    private Embedding embedding = new Embedding();
    private Rag rag = new Rag();

    @Data
    public static class Model {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private int timeout = 60;
        private int maxRetries = 1;
    }

    @Data
    public static class Embedding {
        private String modelName = "text-embedding-ada-002";
    }

    @Data
    public static class Rag {
        private int chunkSize = 500;
        private int chunkOverlap = 50;
        private int topK = 3;
        private double minScore = 0.5;
        private String knowledgeDir = "classpath:knowledge/";
    }
}
