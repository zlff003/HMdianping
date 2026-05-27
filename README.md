# 埃弗的黑马点评

本仓库是“黑马点评”项目的后端服务，实现了基础的店铺搜索/详情、优惠券、订单等业务，并集成了一个基于 RAG + ReAct + MCP 的 AI 智能客服模块（支持流式 SSE 响应）。

## 主要特性
- 基于 Spring Boot 3.5 和 Java 17
- RAG（检索增强生成）：本地知识库检索并注入 Prompt
- ReAct 推理-行动循环：LLM 可决定调用后端工具（数据库/查询）
- MCP（Model Context Protocol）：以工具形式对外暴露后端能力给 LLM
- SSE 流式输出：前端可逐步显示模型生成的 token
- 前端静态页面：位于 `nginx-1.18.0/html/hmdp/`（包含 `ai-chat.html`）

## 技术栈
- Spring Boot 3.5.14
- Java 17
- MyBatis-Plus (ORM)
- Redis (Lettuce)
- MySQL
- LangChain4j / langchain4j-open-ai
- MCP Java SDK

## 快速开始

先决条件：
- 安装 JDK 17
- 安装 Maven
- 可用的 MySQL 实例和 Redis 实例

1. 克隆仓库并进入项目根目录：

```powershell
cd D:/Projects/JAVA/HMdianping
```

2. 使用 Maven 构建：

```powershell
mvn clean package -DskipTests
```

3. 运行（开发模式）：

```powershell
mvn spring-boot:run
```

或运行打包后的 JAR：

```powershell
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

默认服务端口：`8081`（可在 `src/main/resources/application.yaml` 中修改）

## 配置说明
- 主配置文件：`src/main/resources/application.yaml`
  - 数据库连接使用环境变量覆盖：`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - Redis 连接可通过 `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD/REDIS_DATABASE` 配置
  - AI 模型相关配置：`ai.model.api-key`, `ai.model.base-url`, `ai.model.model-name` 等（建议将密钥放入环境变量）

示例（重要）：

- 知识库目录：`src/main/resources/knowledge/`（包含 `faq.txt`, `platform-rules.txt`, `activities.txt`）
- 数据库初始化脚本：`src/main/resources/db/hmdp.sql`、`hmdp_extra_data.sql`
- 秒杀脚本（Redis Lua）：`src/main/resources/seckill.lua`

## AI 智能客服模块概览
- 位置：主要代码位于 `src/main/java/com/hmdp/`（`controller`、`service`、`mcp`、`config` 等包）
- 前端对话页面：`nginx-1.18.0/html/hmdp/ai-chat.html`
- 工作流：用户请求 → RAG 检索知识片段 → 使用非流式 LLM 判断是否需要工具调用（ReAct）→ 如需调用，通过 MCP 执行工具 → 最终流式返回给前端（SSE）

详细技术说明请参阅：
- 文档：`docs/AI智能客服模块说明文档.md`

## 常用命令
- 构建：`mvn clean package`
- 运行：`mvn spring-boot:run` 或 `java -jar target/*.jar`
- 重新构建知识库索引（如实现了对应接口）：调用 `/ai/knowledge/rebuild` 管理接口（参见代码实现）

## 项目结构（简要）

```
src/main/java/com/hmdp/
├── config/        # AI 与全局配置
├── controller/    # REST / SSE 接口
├── service/       # 业务与 AI 实现
├── mcp/           # MCP 工具与客户端/服务端实现
└── entity/dto/    # 实体与 DTO

src/main/resources/
├── application.yaml
├── knowledge/     # 本地知识库文档
└── db/            # 数据初始化脚本

nginx-1.18.0/html/hmdp/  # 前端静态页面
```

## 注意事项与建议
- 将 AI API Key 与数据库密码等敏感信息通过环境变量提供，不要写入配置文件中。
- 在生产环境中，建议使用持久化的向量存储与更完善的检索服务（如 Milvus/Weaviate），以应对大规模知识库。
- 对 AI 输出应增加审查与安全策略（过滤敏感内容、注入检测、策略治理）。

## 参考文件
- 项目构建配置：`pom.xml`
- 主配置：`src/main/resources/application.yaml`
- AI 技术说明：`docs/AI智能客服模块说明文档.md`
- 前端对话页面：`nginx-1.18.0/html/hmdp/ai-chat.html`