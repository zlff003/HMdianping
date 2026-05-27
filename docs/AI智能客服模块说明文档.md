# 黑马点评 AI 智能客服模块 - 技术说明文档

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [技术栈](#3-技术栈)
4. [核心设计思想](#4-核心设计思想)
5. [模块详解 - 配置层](#5-模块详解---配置层)
6. [模块详解 - RAG 知识库](#6-模块详解---rag-知识库)
7. [模块详解 - MCP 工具系统](#7-模块详解---mcp-工具系统)
8. [模块详解 - ReAct 推理循环](#8-模块详解---react-推理循环)
9. [模块详解 - SSE 流式推送](#9-模块详解---sse-流式推送)
10. [模块详解 - 前端页面](#10-模块详解---前端页面)
11. [完整数据流](#11-完整数据流)
12. [对话测试例子](#12-对话测试例子)

---

## 1. 项目概述

本项目是"黑马点评"App 的后端服务，基于 **Spring Boot 3.5** 构建。AI 智能客服模块是项目的核心功能之一，它实现了一个**能够理解用户自然语言、自主查询数据库、并以流式方式返回回答**的智能对话机器人。

### 1.1 模块能做什么？

| 能力 | 说明 |
|------|------|
| 搜索店铺 | 根据关键词搜索附近美食店铺 |
| 查看店铺详情 | 查询指定店铺的评分、地址、营业时间、人均价格 |
| 查询优惠券 | 查询某店铺可领取的普通优惠券和秒杀券 |
| 查询订单 | 查询用户的订单状态和详情 |
| 热门笔记 | 查询探店热门笔记 |
| 通用问答 | 基于知识库回答优惠券使用、平台规则、账号问题等 FAQ |

### 1.2 文件结构总览

```
src/main/java/com/hmdp/
├── config/
│   ├── AiConfig.java              # AI 模型 Bean 配置
│   ├── AiProperties.java          # AI 相关配置属性
│   └── McpServerConfiguration.java # MCP 工具注册与执行器
├── controller/
│   ├── AiChatController.java      # AI 对话接口（SSE 流式）
│   └── KnowledgeAdminController.java # 知识库管理接口
├── service/
│   ├── IAiChatService.java        # AI 对话服务接口
│   └── impl/
│       ├── AiChatServiceImpl.java    # AI 对话核心实现（ReAct 循环）
│       ├── KnowledgeIndexService.java # 知识库向量索引构建
│       └── KnowledgeRetrievalService.java # 知识库语义检索
├── mcp/
│   ├── client/
│   │   └── McpClientService.java  # MCP 客户端（工具调用）
│   ├── server/
│   │   └── McpServerProperties.java # MCP Server 配置
│   └── tools/
│       ├── ShopTools.java         # 店铺搜索/详情工具
│       ├── VoucherTools.java      # 优惠券查询工具
│       ├── OrderTools.java        # 订单查询工具
│       └── BlogTools.java         # 热门笔记工具
├── dto/
│   └── ChatMessageDTO.java        # 消息 DTO
└── entity/
    └── DocumentChunk.java         # 知识库文档片段实体

src/main/resources/
├── application.yaml               # 全局配置（含 AI 配置）
└── knowledge/
    ├── faq.txt                    # 常见问题知识库
    ├── platform-rules.txt         # 平台规则知识库
    └── activities.txt             # 活动说明知识库

nginx-1.18.0/html/hmdp/
└── ai-chat.html                   # 前端对话页面
```

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (ai-chat.html)                       │
│                   Vue.js + SSE (Server-Sent Events)             │
└──────────────────────────┬──────────────────────────────────────┘
                           │ GET /ai/chat/stream?message=xxx
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                  AiChatController (控制器层)                      │
│             接收请求 → 开启新线程 → 异步处理 → 返回 SseEmitter     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                  AiChatServiceImpl (核心服务层)                   │
│                                                                  │
│   ┌──────────┐    ┌──────────────┐    ┌──────────────────┐     │
│   │ 1.RAG检索 │───▶│ 2.ReAct推理  │───▶│ 3.SSE 流式推送   │     │
│   │ 知识库    │    │  循环(≤3轮)  │    │ token by token  │     │
│   └──────────┘    └──────┬───────┘    └──────────────────┘     │
│                          │                                       │
│                          ▼                                       │
│               ┌─────────────────────┐                           │
│               │ McpClientService    │                           │
│               │ (工具调用分发)       │                           │
│               └──────────┬──────────┘                           │
│                          │                                       │
│          ┌───────────────┼───────────────┐                      │
│          ▼               ▼               ▼                      │
│   ┌──────────┐   ┌────────────┐  ┌────────────┐                │
│   │ShopTools │   │VoucherTools│  │OrderTools  │ ...            │
│   └────┬─────┘   └─────┬──────┘  └─────┬──────┘                │
│        │               │               │                        │
└────────┼───────────────┼───────────────┼────────────────────────┘
         ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    数据层 (MySQL + Redis)                        │
│   MySQL: 店铺/优惠券/订单/笔记    Redis: 历史消息/向量存储        │
└─────────────────────────────────────────────────────────────────┘
```

### 2.1 请求处理流程概览

```
用户输入 "附近有什么好吃的火锅？"
        │
        ▼
前端 fetch SSE 流式请求  ──▶  AiChatController
        │                        │
        │                  开新线程异步处理
        │                        ▼
        │              AiChatServiceImpl.chatStream()
        │                        │
        │            ┌───────────┼───────────┐
        │            ▼           ▼           ▼
        │       RAG知识库   构建System    保存用户消息
        │       语义检索     Prompt       到Redis
        │            │           │           │
        │            └───────────┼───────────┘
        │                        ▼
        │              LLM 第一轮推理(非流式)
        │              判断是否需要调用工具
        │                        │
        │            ┌───────────┴───────────┐
        │            ▼                       ▼
        │     需要工具调用              不需要工具
        │            │                       │
        │     ReAct循环(最多3轮)         直接回答
        │     提取JSON工具调用              │
        │     → MCP执行工具                 │
        │     → 结果注入对话                │
        │     → LLM继续推理                 │
        │            │                       │
        │            └───────────┬───────────┘
        │                        ▼
        │              SSE流式推送 token by token
        │                        │
        ◀────────────────────────┘
        前端逐字渲染 AI 回复
```

---

## 3. 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.14 | 后端框架 |
| Java | 17 | 编程语言 |
| MyBatis-Plus | 3.5.14 | ORM 数据库访问 |
| Redis | 7.x (Lettuce) | 缓存 + 聊天历史 + 向量存储 |
| MySQL | 8.x | 业务数据存储 |
| LangChain4j | 1.14.1 | LLM 集成框架（统一 API） |
| langchain4j-open-ai | 1.14.1 | OpenAI 兼容协议的 LLM 客户端 |
| MCP SDK | 1.0.0 (mcp-core) | 标准 MCP 工具协议 |
| FastJSON | 2.0.57 | JSON 序列化/反序列化 |
| OkHttp + OkHttp-SSE | 4.12.0 | HTTP 客户端 + SSE 支持 |
| 阿里云通义千问 | qwen-turbo | 大语言模型 |
| text-embedding-v2 | - | 向量嵌入模型 |
| Vue.js | 2.x | 前端框架 |
| Element UI | - | UI 组件库 |

---

## 4. 核心设计思想

这个模块融合了三个重要的 AI 应用设计模式：

### 4.1 RAG（Retrieval-Augmented Generation，检索增强生成）

**是什么**：在向 LLM 提问之前，先从本地知识库中检索与问题相关的文档片段，将检索结果作为"参考资料"一起注入到 Prompt 中，让 LLM 基于真实资料回答。

**为什么需要**：
- LLM 的训练数据有截止日期，不知道你的平台规则
- 防止 LLM 胡说八道（幻觉）
- 让 LLM 回答内容符合平台实际政策

**本项目实现**：
- 知识库文档（faq.txt 等）→ 分块 → Embedding 向量化 → 存入 Redis
- 用户提问 → 向量化 → Redis 中余弦相似度检索 → Top-K 相关片段 → 注入 Prompt

### 4.2 MCP（Model Context Protocol，模型上下文协议）

**是什么**：Anthropic 定义的一套标准协议，让 AI 模型能够"调用工具"——把外部能力（如数据库查询）以标准化的方式暴露给 LLM。

**为什么需要**：
- LLM 本身不能直接查数据库
- 需要一个标准的方式让 LLM "知道"有哪些工具可用，以及工具的参数格式
- MCP 是行业标准协议，未来可以更方便地扩展工具

**本项目实现**：
- 通过 MCP SDK 的 `McpSchema.Tool` 定义工具 Schema（名称、描述、参数）
- 通过 In-Process Bridge 模式直接调用（零网络开销）
- 工具列表动态注入到 System Prompt 中

### 4.3 ReAct（Reasoning + Acting，推理-行动循环）

**是什么**：一种 AI 推理模式，让 LLM 在"思考"和"行动"之间循环——先推理判断需要什么信息，再调用工具获取信息，基于工具返回结果继续推理。

**为什么需要**：
- 单次 LLM 调用无法完成复杂任务（如"先搜店铺，再查优惠券"）
- 需要 LLM 自主决策调用哪些工具、如何组合工具

**本项目实现**：
- 最多 3 轮 ReAct 循环
- LLM 以 JSON 格式输出工具调用指令
- 后端解析 JSON → 执行工具 → 结果注入对话 → LLM 继续推理

---

## 5. 模块详解 - 配置层

### 5.1 AiProperties.java - AI 配置属性

**文件路径**: `src/main/java/com/hmdp/config/AiProperties.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private Model model = new Model();
    private Embedding embedding = new Embedding();
    private Rag rag = new Rag();
```

使用 Spring Boot 的 `@ConfigurationProperties` 绑定 `application.yaml` 中以 `ai` 开头的配置。

**配置项说明**：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ai.model.api-key` | - | LLM API 密钥 |
| `ai.model.base-url` | - | LLM API 地址（兼容 OpenAI 协议） |
| `ai.model.model-name` | - | 模型名称，如 qwen-turbo |
| `ai.model.timeout` | 60 | 请求超时（秒） |
| `ai.model.max-retries` | 1 | 最大重试次数 |
| `ai.embedding.model-name` | text-embedding-ada-002 | 向量化模型名称 |
| `ai.rag.chunk-size` | 500 | 文档分块最大字符数 |
| `ai.rag.chunk-overlap` | 50 | 分块间重叠字符数 |
| `ai.rag.top-k` | 3 | 检索返回最多 K 个片段 |
| `ai.rag.min-score` | 0.5 | 最低余弦相似度阈值 |
| `ai.rag.knowledge-dir` | classpath:knowledge/ | 知识库文档目录 |

### 5.2 AiConfig.java - 模型 Bean 配置

**文件路径**: `src/main/java/com/hmdp/config/AiConfig.java`

这个配置类创建了三个核心 Bean：

```java
@Bean
public StreamingChatModel streamingChatModel() { ... }  // 流式对话模型

@Bean
public ChatModel chatModel() { ... }                     // 非流式对话模型

@Bean
public EmbeddingModel embeddingModel() { ... }           // 向量嵌入模型
```

**关键设计：为什么需要两个 ChatModel？**

- `ChatModel`（非流式）：用于 ReAct 推理阶段，需要一次性拿到 LLM 的完整响应来判断是否包含工具调用 JSON
- `StreamingChatModel`（流式）：用于最终回答的流式推送，让前端实现"打字机"逐字显示效果

两个 Bean 都通过 LangChain4j 的 `OpenAiChatModel` / `OpenAiStreamingChatModel` 创建，底层使用 OpenAI 兼容协议。本项目中实际对接的是**阿里云通义千问**（baseUrl 指向 `dashscope.aliyuncs.com`），因为其 API 兼容 OpenAI 格式。

### 5.3 application.yaml 中的 AI 配置

```yaml
ai:
  model:
    api-key: sk-b8f207ef80fe4d29a375bbcb0b6fb64e
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model-name: qwen-turbo          # 阿里云通义千问
    timeout: 60
    max-retries: 1
  embedding:
    model-name: text-embedding-v2   # 阿里云向量模型
  rag:
    chunk-size: 500
    chunk-overlap: 50
    top-k: 3
    min-score: 0.5
    knowledge-dir: classpath:knowledge/
  mcp:
    server:
      enabled: true
      transport: sse
      sse-endpoint: /mcp/sse
      message-endpoint: /mcp/message
```

**为什么用阿里云通义千问而不是 OpenAI？**
- 国内访问 OpenAI API 需要特殊网络环境
- 阿里云通义千问兼容 OpenAI 协议，可以无缝使用 LangChain4j 的 `OpenAiChatModel`
- 成本更低，中文效果好

---

## 6. 模块详解 - RAG 知识库

RAG是检索增强生成，让AI基于平台知识库回答用户问题。

### 6.1 知识库文档

3个知识库文档位于 `src/main/resources/knowledge/`：

| 文件 | 内容 |
|------|------|
| faq.txt | 常见问题（注册登录、店铺搜索、优惠券、笔记、关注、账号） |
| platform-rules.txt | 平台规则（用户行为、内容审核、秒杀规则、隐私保护） |
| activities.txt | 活动说明（签到、新用户优惠、美食节、秒杀、等级体系） |

### 6.2 工作原理

**离线索引阶段**（KnowledgeIndexService，应用启动时自动执行）：

```
知识库.txt → 读取文本 → 按段落分块(chunkSize=500, overlap=50)
→ Embedding模型向量化 → 存入Redis
  - rag:chunks (Set) 存所有chunk ID
  - rag:chunk:{id} (Hash) 存 id/source/content/vector
```

**在线检索阶段**（KnowledgeRetrievalService.retrieve）：

```
用户问题 → Embedding向量化 → 遍历Redis所有chunk
→ 计算余弦相似度 cos(a,b) = (a·b) / (|a|×|b|)
→ 过滤minScore<0.5 → 取Top-K(默认3)
→ 返回相关文本片段注入System Prompt
```

### 6.3 关键代码

**KnowledgeIndexService** - 分块算法：
- 优先按双换行（段落边界）分割
- 超长段落按chunkSize强制截断
- 相邻块之间保留overlap字符重叠，避免关键信息被截断

```java
@PostConstruct  // 启动时自动执行
public void initIndex() {
    // 检查Redis是否已有向量数据，有则跳过（幂等性）
    // 使用单线程池异步执行，不阻塞应用启动
}
```

**KnowledgeRetrievalService** - 余弦相似度：
```java
private double cosineSimilarity(float[] a, float[] b) {
    double dot = 0.0, normA = 0.0, normB = 0.0;
    for (int i = 0; i < a.length; i++) {
        dot += (double) a[i] * b[i];
        normA += (double) a[i] * a[i];
        normB += (double) b[i] * b[i];
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

### 6.4 Redis存储结构

```
Key: "rag:chunks" (Set) → ["abc123", "def456", ...]
Key: "rag:chunk:abc123" (Hash) → {id, source, content, vector(JSON数组)}
```

### 6.5 管理接口 (KnowledgeAdminController)

- `POST /ai/knowledge/rebuild` — 强制重建索引（知识库文件更新后调用）
- `DELETE /ai/knowledge/index` — 清空索引

---

## 7. 模块详解 - MCP 工具系统

MCP（Model Context Protocol）是 Anthropic 定义的标准协议，让 AI 模型能够调用外部工具。本项目使用 `mcp-core` SDK 的 `McpSchema.Tool` 类型定义工具的 Schema（名称、描述、参数），通过 In-Process Bridge 模式直接调用业务方法，**零网络开销**。

### 7.1 工具注册 (McpServerConfiguration.java)

**文件路径**: `src/main/java/com/hmdp/config/McpServerConfiguration.java`

该配置类负责将 6 个 MCP 工具注册为 Spring Bean：

```
mcpTools() Bean → List<McpSchema.Tool>
  ├── search_shops      搜索店铺（按关键词+类型筛选）
  ├── get_shop_detail   查询店铺详情（评分/地址/营业时间/人均）
  ├── get_vouchers      查询优惠券（券名/面值/条件/库存）
  ├── get_seckill_vouchers  查询秒杀券（秒杀价/库存/活动时间）
  ├── query_order_status    查询订单状态
  └── get_hot_blogs         查询热门探店笔记
```

每个工具的定义包含：
- `name`: 工具名称，LLM 通过此名称调用
- `description`: 功能描述，写入 System Prompt 供 LLM 参考
- `inputSchema`: 参数 Schema（JSON Schema 格式），包括类型、是否必填、参数说明

**ToolExecutor - 工具执行器**（In-Process Bridge）：

```java
@Bean
public ToolExecutor toolExecutor() {
    return (toolName, params) -> {
        McpSchema.CallToolResult result = executeToolDirectly(toolName, params);
        // 提取 TextContent，返回纯文本字符串
    };
}
```

`executeToolDirectly` 使用 Java 21 的 switch 表达式直接路由到对应的工具方法：

```java
private McpSchema.CallToolResult executeToolDirectly(String toolName, Map<String, Object> args) {
    String textResult = switch (toolName) {
        case "search_shops"       -> shopTools.searchShops(...);
        case "get_shop_detail"    -> shopTools.getShopDetail(...);
        case "get_vouchers"       -> voucherTools.getVouchers(...);
        case "get_seckill_vouchers" -> voucherTools.getSeckillVouchers(...);
        case "query_order_status" -> orderTools.queryOrderStatus(...);
        case "get_hot_blogs"      -> blogTools.getHotBlogs(...);
        default -> "未知工具: " + toolName;
    };
    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(textResult)), ...);
}
```

### 7.2 McpClientService - MCP 客户端

**文件路径**: `src/main/java/com/hmdp/mcp/client/McpClientService.java`

充当 LLM 和工具之间的桥梁，提供三个核心能力：

1. **`listTools()`** - 获取所有可用工具的 Schema 列表
2. **`buildToolsPromptSection()`** - 将工具 Schema 格式化为 System Prompt 中的工具描述文本，例如：
   ```
   你拥有以下工具能力：
   1. search_shops: 搜索附近的美食店铺，支持按关键词和店铺类型筛选
     - keyword (string, 必填): 搜索关键词，如'火锅'、'日料'
     - typeId (integer, 可选): 店铺类型ID
     - pageSize (integer, 可选): 返回结果数量上限
   2. get_shop_detail: 查询指定店铺的详细信息...
   ...
   ```
3. **`executeTool(toolName, params)`** - 执行指定工具并返回结果字符串

### 7.3 工具详解

#### ShopTools.java - 店铺工具

| 方法 | 对应 MCP 工具 | 功能 |
|------|--------------|------|
| `searchShops(keyword, typeId, pageSize)` | search_shops | 用 MyBatis-Plus QueryWrapper 对 shop 表做 LIKE 查询 |
| `getShopDetail(shopId)` | get_shop_detail | 通过 shopMapper.selectById 查询单条记录 |

#### VoucherTools.java - 优惠券工具

| 方法 | 对应 MCP 工具 | 功能 |
|------|--------------|------|
| `getVouchers(shopId)` | get_vouchers | 调用 voucherMapper.queryVoucherOfShop 查询优惠券列表 |
| `getSeckillVouchers(shopId)` | get_seckill_vouchers | 同上查询，筛选有 beginTime/endTime/stock 的秒杀券 |

#### OrderTools.java - 订单工具

`queryOrderStatus(userId, orderId)` — 查询订单：
- 传入 orderId → 查指定订单
- 不传 orderId → 查该用户最近 5 条订单

#### BlogTools.java - 笔记工具

`getHotBlogs(shopId, limit)` — 查询热门探店笔记：
- 传入 shopId → 查该店铺的热门笔记
- 不传 shopId → 查全局热门笔记

### 7.4 MCP 调用流程

```
LLM 输出: {"name":"search_shops","arguments":{"keyword":"火锅","pageSize":5}}
                    │
                    ▼
AiChatServiceImpl.executeMcpToolLoop()
  正则提取 → toolName="search_shops", params={keyword:"火锅", pageSize:5}
                    │
                    ▼
McpClientService.executeTool("search_shops", params)
                    │
                    ▼
ToolExecutor.execute("search_shops", params)
                    │
                    ▼
ShopTools.searchShops("火锅", null, 5)
                    │
                    ▼
返回文本: "找到 3 家店铺：\n- 店铺名: 海底捞火锅 | 评分: 9.2 | ID: 1\n..."
                    │
                    ▼
结果注入对话，LLM 基于结果生成回答
```

---

## 8. 模块详解 - ReAct 推理循环

ReAct（Reasoning + Acting）是 AI 智能客服的**核心大脑**，实现在 `AiChatServiceImpl.chatStream()` 方法中。

### 8.1 System Prompt 设计

```java
private static final String SYSTEM_PROMPT_TEMPLATE =
    "你是「黑马点评」App 的 AI 智能客服..." +
    "{mcp_tools}\n" +        // ← 动态注入工具列表
    "【重要规则】\n" +
    "- 优惠券查询：先 search_shops → 再 get_vouchers\n" +
    "- 绝对不要编造信息，必须使用工具获取真实数据\n" +
    "【工具调用格式】\n" +
    "{\"name\":\"工具名\",\"arguments\":{\"参数名\":\"值\"}}\n" +
    "{rag_context}";         // ← 动态注入 RAG 检索结果
```

**设计要点**：
- 通过占位符 `{mcp_tools}` 和 `{rag_context}` 动态注入上下文
- 明确工具调用格式（JSON），LLM 必须遵守
- 强调"不要编造信息"，防止幻觉

### 8.2 chatStream 主流程

```java
public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter) {
    // 1. RAG 检索 - 从知识库获取相关文档片段
    List<String> ragContexts = knowledgeRetrievalService.retrieve(userMessage);
    
    // 2. 构建 System Prompt（工具列表 + RAG 上下文）
    String fullSystemPrompt = SYSTEM_PROMPT_TEMPLATE
            .replace("{mcp_tools}", mcpClientService.buildToolsPromptSection())
            .replace("{rag_context}", buildRagText(ragContexts));
    
    // 3. 用户消息存入 Redis 历史
    appendHistory(effectiveId, new ChatMessageDTO("user", userMessage));
    
    // 4. 非流式推理 - 判断是否需要工具调用
    String llmResponse = chatModel.chat(
            SystemMessage.from(fullSystemPrompt),
            UserMessage.from(userMessage)
    ).aiMessage().text();
    
    // 5. 智能补充 - 如果 LLM 忘了调用工具但问题涉及店铺/优惠券
    if (!containsToolCall(llmResponse) && needsToolSearch(userMessage)) {
        // 自动补充 search_shops 工具调用
    }
    
    // 6. ReAct 循环 - 执行工具调用（最多3轮）
    String finalAnswer = executeMcpToolLoop(llmResponse, fullSystemPrompt, userMessage);
    
    // 7. SSE 流式推送最终回答
    pushStream(emitter, finalAnswer, effectiveId);
}
```

### 8.3 ReAct 循环详解 (executeMcpToolLoop)

```
第1轮推理:
  LLM 返回: "好的，让我搜索一下...{\"name\":\"search_shops\",\"arguments\":{\"keyword\":\"火锅\"}}"
  → 正则匹配到工具调用 JSON
  → 执行 search_shops → 返回 "找到 3 家店铺：..."
  → 从结果中提取 shopId=1
  → 将工具结果注入对话，继续循环

第2轮推理:
  LLM 返回: "{\"name\":\"get_vouchers\",\"arguments\":{}}"  ← 缺少shopId
  → 自动补充 shopId=1（从上一轮提取）
  → 执行 get_vouchers → 返回 "该店铺有以下优惠券：..."
  → 工具结果注入对话

第3轮（最终）:
  LLM 基于所有工具结果生成最终回答（纯文本，无JSON）
  → 正则检测无工具调用 → break
  → 清洗残留JSON → 返回干净文本
```

**关键设计**：

1. **最多3轮循环** - 防止无限循环，平衡功能完整性和响应延迟
2. **shopId 自动传递** - 从 search_shops 结果中提取 shopId，自动补充到后续 get_vouchers 调用
3. **JSON 正则提取** - 使用正则 `{"name":"xxx","arguments":{...}}` 从 LLM 输出中提取工具调用
4. **智能补充工具调用** - 如果 LLM 对店铺/优惠券问题没有调用工具，自动补充 search_shops

### 8.4 智能兜底机制

```java
// 如果 LLM 没有调用工具，但问题包含店铺/优惠券关键词
if (!containsToolCall(llmFirstResponse) &&
    (userMessage.contains("店") || userMessage.contains("铺") || userMessage.contains("优惠券"))) {
    // 自动注入 search_shops 工具调用，确保查询真实数据
    String keyword = userMessage.substring(0, Math.min(20, userMessage.length()));
    enhancedResponse = "{\"name\":\"search_shops\",\"arguments\":{\"keyword\":\"" + keyword + "\"}}\n" + llmFirstResponse;
}
```

---

## 9. 模块详解 - SSE 流式推送

SSE（Server-Sent Events）用于将 AI 回答逐字推送到前端，实现"打字机"效果。

### 9.1 Controller 层

```java
@GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestParam String message,
                              @RequestParam(required = false) String sessionId) {
    SseEmitter emitter = new SseEmitter(0L);  // 0 = 永不超时
    // 开新线程异步处理，避免阻塞主线程
    new Thread(() -> aiChatService.chatStream(userId, sessionId, message, emitter)).start();
    return emitter;  // 立即返回 emitter，后续通过 emitter.send() 推送数据
}
```

### 9.2 流式推送实现 (pushStream)

```java
private void pushStream(SseEmitter emitter, String text, String effectiveId) {
    // 对最终回答文本做截断保护（最多500字）
    final String safeText = text.length() > 500 ? text.substring(0, 500) + "..." : text;
    
    // 将回答文本作为"用户消息"发送给流式模型，
    // 利用流式模型的 token-by-token 输出实现逐字效果
    streamingChatModel.chat(safeText, new StreamingChatResponseHandler() {
        private final StringBuilder fullText = new StringBuilder();
        
        @Override
        public void onPartialResponse(String token) {
            fullText.append(token);
            emitter.send(SseEmitter.event().data(token));  // 逐 token 推送
        }
        
        @Override
        public void onCompleteResponse(ChatResponse response) {
            appendHistory(effectiveId, new ChatMessageDTO("assistant", fullText.toString()));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        }
        
        @Override
        public void onError(Throwable error) {
            emitter.send(SseEmitter.event().name("error").data("网络异常"));
            emitter.complete();
        }
    });
}
```

**巧妙的流式技巧**：最终回答来自 ReAct 循环（非流式），但为了给用户流式体验，将最终回答文本再发给 `StreamingChatModel`，利用其 token-by-token 输出实现逐字推送效果。

### 9.3 历史消息管理

- **存储**：Redis List，key = `ai:chat:history:{userId}`
- **上限**：`MAX_HISTORY_MESSAGES = 20` 条（超出时 leftPop 删除最早消息）
- **过期**：`SESSION_TTL = 30分钟`
- **格式**：JSON 序列化的 `ChatMessageDTO`（role + content）

### 9.4 API 接口汇总

| 接口 | 方法 | 说明 |
|------|------|------|
| `/ai/chat/stream?message=xxx` | GET | SSE 流式对话 |
| `/ai/chat/history` | GET | 获取历史消息 |
| `/ai/chat/history` | DELETE | 清空历史消息 |
| `/ai/knowledge/rebuild` | POST | 重建知识库索引 |
| `/ai/knowledge/index` | DELETE | 清空知识库索引 |

---

## 10. 模块详解 - 前端页面

**文件路径**: `nginx-1.18.0/html/hmdp/ai-chat.html`

基于 Vue.js 2.x + Element UI 的移动端对话页面。

### 10.1 页面结构

```
┌──────────────────────────┐
│  ← AI 智能客服    [清空]  │  ← 导航栏
├──────────────────────────┤
│  ┌────────────────────┐  │
│  │ 🍽️ 欢迎卡片        │  │
│  │ [快捷问题快捷问题]  │  │  ← 首次加载欢迎区
│  └────────────────────┘  │
│                          │
│  [用户消息气泡]         │
│       [AI消息气泡]      │  ← 对话区（可滚动）
│  [正在输入...]          │
│                          │
├──────────────────────────┤
│  [输入框]         [发送] │  ← 输入栏
└──────────────────────────┘
```

### 10.2 核心交互流程

```javascript
// 1. 发送消息
sendMessage() {
    this.messages.push({ role: "user", content: text });
    this.loading = true;
    this.streamingContent = "";         // 触发流式输出气泡
    this.fetchStream(url);
}

// 2. SSE 流式接收
fetchStream(url) {
    fetch(url, { credentials: "include" })
        .then(response => {
            const reader = response.body.getReader();
            function read() {
                reader.read().then(({ done, value }) => {
                    // 解析 SSE 行: "data: xxx"
                    lines.forEach(line => self.handleSseLine(line));
                    read();  // 递归读取
                });
            }
            read();
        });
}

// 3. 逐字渲染
handleSseLine(line) {
    if (!line.startsWith("data:")) return;
    const data = line.slice(5).trim();
    if (data === "[DONE]") return;     // 结束信号
    this.streamingContent += data;     // 追加文本 → Vue 自动更新 DOM
}
```

### 10.3 SSE vs WebSocket

本项目选用 SSE 而非 WebSocket 的原因：

| 特性 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务器→客户端） | 双向 |
| 协议 | HTTP | 独立协议 ws:// |
| 重连 | 浏览器自动 | 需手动实现 |
| 适用场景 | 流式推送 | 实时双向通信 |
| 复杂度 | 低 | 高 |

对于 AI 对话场景，用户通过 HTTP 请求发送消息（客户端→服务器），服务器通过 SSE 流式推送回答（服务器→客户端），SSE 完全够用且更简单。

---

## 11. 完整数据流

以下是一次典型对话的完整数据流：

```
用户输入: "海底捞有什么优惠券？"

═══════════════════════════════════════════════════
阶段1: RAG 语义检索
═══════════════════════════════════════════════════
KnowledgeRetrievalService.retrieve("海底捞有什么优惠券？")
  → Embedding("海底捞有什么优惠券？") → float[1536] questionVec
  → 遍历 Redis rag:chunks 中所有 chunk
  → 计算余弦相似度，过滤 minScore<0.5
  → Top-3: ["Q: 如何领取优惠券？...", "优惠券有效期...", "秒杀优惠券..."]
  → 返回 List<String> ragContexts

═══════════════════════════════════════════════════
阶段2: 构建 System Prompt
═══════════════════════════════════════════════════
{tools_text} = mcpClientService.buildToolsPromptSection()
  → 遍历 mcpTools Bean，生成工具描述文本

{rag_text} = buildRagText(ragContexts)
  → "1. Q: 如何领取优惠券？...\n2. 优惠券有效期...\n3. ..."

System Prompt = """
你是「黑马点评」App 的 AI 智能客服...
{tools_text}
{rag_text}
"""

═══════════════════════════════════════════════════
阶段3: 第一轮 LLM 推理（非流式）
═══════════════════════════════════════════════════
Input: SystemMessage(fullSystemPrompt) + UserMessage("海底捞有什么优惠券？")
  → ChatModel.chat() → 阿里云通义千问 API
Output: "我先搜索海底捞的信息。
{"name":"search_shops","arguments":{"keyword":"海底捞","pageSize":5}}"

═══════════════════════════════════════════════════
阶段4: ReAct 工具调用
═══════════════════════════════════════════════════
正则匹配: toolName="search_shops", params={keyword:"海底捞", pageSize:5}
  → McpClientService.executeTool("search_shops", params)
  → ShopTools.searchShops("海底捞", null, 5)
  → SQL: SELECT * FROM shop WHERE name LIKE '%海底捞%' LIMIT 5
  → 返回: "找到 1 家店铺：\n- 店铺名: 海底捞火锅 | 评分: 9.2 | ID: 1"
  → 提取 shopId=1

LLM 继续推理（无更多 JSON 工具调用）→ 基于工具结果生成最终回答

═══════════════════════════════════════════════════
阶段5: 最终回答清理
═══════════════════════════════════════════════════
cleanLlmResponse():
  → 移除残留的 JSON 工具调用文本
  → trim
  → 如果为空 → "抱歉，暂未找到相关信息"

最终回答: "海底捞火锅评分9.2分，目前有以下优惠券：
- 满100减20优惠券 | 面值: 20元 | 剩余: 50张
- 新品尝鲜券 | 面值: 10元 | 剩余: 30张
建议您尽快领取哦！"

═══════════════════════════════════════════════════
阶段6: SSE 流式推送
═══════════════════════════════════════════════════
streamingChatModel.chat(最终回答, handler):
  → onPartialResponse: "海" → emitter.send("海") → 前端显示 "海"
  → onPartialResponse: "底" → emitter.send("底") → 前端显示 "海底"
  → ... 逐 token 推送 ...
  → onCompleteResponse: emitter.send("[DONE]") → emitter.complete()
  → 最终回答存入 Redis 历史

═══════════════════════════════════════════════════
前端实时渲染
═══════════════════════════════════════════════════
fetch('/ai/chat/stream?message=海底捞有什么优惠券')
  → ReadableStream 读取 SSE 数据
  → handleSseLine: data="海" → streamingContent += "海" → Vue 更新 DOM
  → handleSseLine: data="底" → streamingContent += "底" → Vue 更新 DOM
  → ...
  → handleSseLine: data="[DONE]" → 将 streamingContent 存入 messages[]
  → 关闭 loading 状态
```

---

## 12. 对话测试例子

以下是模拟测试用例，展示 AI 智能客服在不同场景下的对话表现。

### 测试1：店铺搜索 + 优惠券查询（多轮工具调用）

**用户输入**：
```
附近有什么好吃的火锅店吗？
```

**后端处理流程**：
1. RAG 检索 → 可能命中"店铺搜索"、"优惠券"相关片段
2. LLM 第一轮推理 → 输出 JSON: `{"name":"search_shops","arguments":{"keyword":"火锅","pageSize":5}}`
3. 正则提取 → 执行 `search_shops` → 返回店铺列表（含 shopId）
4. LLM 生成最终回答

**AI 预期回复**：
```
找到几家火锅店推荐给你：
- 海底捞火锅 | 评分: 9.2/10 | 地址: 朝阳区建国路88号
- 蜀九香火锅 | 评分: 8.7/10 | 地址: 海淀区中关村大街1号
- 小龙坎老火锅 | 评分: 8.5/10 | 地址: 西城区西单北大街

你可以点击店铺查看详情，或者问我某家店有什么优惠券哦！
```

---

### 测试2：优惠券深度查询（shopId 自动传递）

**用户输入**：
```
海底捞有什么优惠券？
```

**后端处理流程**：
1. LLM 第一轮: `{"name":"search_shops","arguments":{"keyword":"海底捞"}}`
2. 工具返回: "店铺名: 海底捞火锅 | ID: 1"
3. 后端自动提取 `shopId=1`
4. LLM 第二轮: `{"name":"get_vouchers","arguments":{}}`（省略 shopId）
5. 后端自动补充 `shopId=1`
6. 工具返回优惠券列表 → LLM 生成回答

**AI 预期回复**：
```
海底捞火锅（评分9.2/10）目前有以下优惠券：

【普通券】
- 满200减50 | 条件: 消费满200元 | 剩余: 100张
- 新品尝鲜券 | 面值: 20元 | 条件: 新品专享 | 剩余: 50张

【秒杀券】
- 超值秒杀 | 秒杀价: 9.9元 | 剩余: 仅剩12张 | 时间: 5月16日 12:00

建议您尽快领取，热门券抢得很快哦！
```

---

### 测试3：通用 FAQ 问答（仅 RAG，无工具调用）

**用户输入**：
```
优惠券怎么使用？
```

**后端处理流程**：
1. RAG 检索 → 高相似度命中 faq.txt 中"Q: 优惠券如何使用？"
2. LLM 推理 → 不包含工具调用 JSON，直接基于 RAG 资料回答

**AI 预期回复**：
```
到店消费时，优惠券使用方法很简单：
1. 打开App，点击底部「我的」
2. 进入「我的券包」
3. 找到你要使用的优惠券
4. 出示给店员扫描二维码，或告知核销码即可完成核销

温馨提示：请确认优惠券在有效期内哦！
```

---

### 测试4：平台规则问答（RAG 检索）

**用户输入**：
```
发布笔记需要注意什么规则？
```

**后端处理流程**：
1. RAG 检索 → 命中 platform-rules.txt 中的用户行为规范和内容审核规则
2. LLM 获取 RAG 上下文后直接回答（无需工具调用）

**AI 预期回复**：
```
发布笔记需要遵守以下规则：
1. 内容必须真实客观，不能发布虚假评价
2. 禁止发布违法违规、色情暴力等内容
3. 不能发布广告性质的推销内容
4. 不能侵犯他人隐私或肖像权
5. 同一用户对同一店铺只能发布一条有效评价

另外，发布的笔记需要经过平台审核（1-24小时内），审核通过后才会公开展示。单篇笔记最多上传9张图片哦！
```

---

### 测试5：活动信息查询（RAG 检索）

**用户输入**：
```
最近有什么活动？
```

**后端处理流程**：
1. RAG 检索 → 命中 activities.txt 中的平台运营活动信息
2. LLM 基于 RAG 上下文直接回答

**AI 预期回复**：
```
最近平台有以下活动可以参与：

1. 每日签到送金币 - 每天登录可领1个金币，连续7天额外得10个

2. 周末美食节 - 每周六10:00至周日23:59，部分店铺推出特价优惠券

3. 秒杀活动 - 不定期举行，通常在中午12:00或晚上20:00，需关注首页公告

4. 美食探店任务 - Lv.2以上用户可参与，去指定店铺消费并发布笔记可获得50-500金币奖励

还有每周热点话题活动，参与可获得曝光和金币奖励。你对哪个活动感兴趣呢？
```

---

### 测试6：订单查询（需要 userId）

**用户输入**：
```
帮我查一下我的订单
```

**后端处理流程**：
1. Controller 从 UserHolder 获取 userId
2. LLM 推理 → 判断需要调用 `query_order_status`
3. 输出: `{"name":"query_order_status","arguments":{"userId":12345}}`
4. 查询用户最近5条订单

**AI 预期回复**：
```
你最近的订单如下：
- 订单1001 | 券名: 满100减20 | 状态: 已完成 | 面值: 20元 | 时间: 2026-05-14
- 订单1002 | 券名: 新品尝鲜券 | 状态: 待使用 | 面值: 10元 | 时间: 2026-05-13

还有1张待使用的优惠券，别忘了在有效期内使用哦！
```

---

### 测试7：边界情况 - 空输入

**用户输入**：
```
（空字符串或仅空格）
```

**后端处理流程**：
Controller 层直接拦截，返回错误 SSE 事件：

```java
if (message == null || message.trim().isEmpty()) {
    emitter.send(SseEmitter.event().name("error").data("消息不能为空"));
    emitter.complete();
    return emitter;
}
```

**AI 回复**：不会调用 LLM，直接返回错误提示。

---

### 测试8：边界情况 - 搜不到店铺

**用户输入**：
```
火星餐厅在哪里？
```

**后端处理流程**：
1. LLM → `{"name":"search_shops","arguments":{"keyword":"火星餐厅"}}`
2. ShopTools.searchShops → 数据库无匹配 → 返回 "未找到符合条件的店铺"
3. LLM 基于工具结果生成回答

**AI 预期回复**：
```
抱歉，没有搜索到"火星餐厅"的相关信息。你可以尝试：
- 换个关键词试试
- 在首页浏览分类推荐
- 查看热门榜单发现新店铺

有什么其他我可以帮你的吗？
```

---

### 测试9：边界情况 - 消息长度限制

**用户输入**：
```
（一段超过500字的超长问题）
```

**后端处理流程**：
最终回答在 `pushStream` 中被截断：
```java
final String safeText = text.length() > 500 ? text.substring(0, 500) + "..." : text;
```

---

### 测试10：快捷问题测试

前端页面提供4个快捷问题按钮，点击即可直接发送：

| 快捷问题 | 触发的能力 |
|---------|-----------|
| "附近有什么好吃的？" | RAG + search_shops |
| "优惠券怎么用？" | RAG 检索 FAQ |
| "今天有什么活动？" | RAG 检索活动信息 |
| "我的订单在哪里？" | query_order_status |

---

## 附录：关键配置汇总

### A. 可调参数一览

| 参数 | 默认值 | 位置 | 调优建议 |
|------|--------|------|---------|
| chunk-size | 500 | application.yaml | 减小可提高检索精度但增加存储量 |
| chunk-overlap | 50 | application.yaml | 增大减少信息截断风险 |
| top-k | 3 | application.yaml | 增大让 LLM 获得更多参考资料 |
| min-score | 0.5 | application.yaml | 降低可召回更多相关片段 |
| MAX_HISTORY | 20 | AiChatServiceImpl | 控制对话历史保留条数 |
| SESSION_TTL | 30分钟 | AiChatServiceImpl | 控制会话存活时间 |
| ReAct 最大轮数 | 3 | AiChatServiceImpl | 增大支持更复杂工具链 |
| 回答最大长度 | 500字 | AiChatServiceImpl | 防止回答过长 |
| API timeout | 60秒 | application.yaml | 超时设置需考虑 LLM API 响应速度 |

### B. 扩展新工具的步骤

1. 在 `mcp/tools/` 下创建新 Tool 类（如 `UserTools.java`）
2. 在 `McpServerConfiguration.mcpTools()` 中注册 Schema
3. 在 `executeToolDirectly` 的 switch 中添加路由
4. 重启应用，LLM 自动获得新工具能力

### C. 切换 LLM 模型

只需修改 `application.yaml`：

```yaml
ai:
  model:
    api-key: your-new-api-key
    base-url: https://api.openai.com/v1   # 或任何兼容 OpenAI 协议的地址
    model-name: gpt-4o                     # 模型名称
```

LangChain4j 的 `OpenAiChatModel` 基于 OpenAI 兼容协议，任何兼容 OpenAI 接口的 LLM 服务都可以无缝切换。

