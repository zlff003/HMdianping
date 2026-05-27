package com.hmdp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块实体，代表知识库中一个已向量化的文本片段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    /** 唯一 ID（source + 分块序号） */
    private String id;
    /** 来源文件名，如 faq.txt */
    private String source;
    /** 原始文本内容 */
    private String content;
    /** 向量（由 Embedding 模型生成） */
    private float[] vector;
}
