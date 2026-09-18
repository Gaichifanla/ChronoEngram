# 记忆数据库架构规划

> 时间与事实模型已升级为不可变 `MemoryEvent` 双时间事件流。本文早期示例中的 `MemoryRecord.createdAt` 仅保留为历史设计记录，当前实现以 [temporal-event-model.md](temporal-event-model.md) 为准。

## 1. 产品目标

构建一个“输入一句话，就能找回想要的数据”的记忆数据库。用户输入可以是：

- 问题：例如“上次说的服务器部署密码相关信息在哪里？”
- 陈述句：例如“我记得有一个关于客户 A 续费风险的记录”
- 关键词：例如“客户 A 续费”

系统需要理解用户意图，从历史记忆中召回相关内容，并给出可追溯、可解释、可继续追问的结果。

## 2. 核心原则

- 自然语言优先：用户不需要学习查询语法。
- 多路召回：关键词检索、语义检索、结构化过滤共同工作。
- 结果可追溯：每条回答都能回到原始记忆片段。
- 记忆可演进：后续支持补充、合并、纠错、遗忘。
- 本地优先起步：先做一个可运行的最小闭环，再扩展到服务化和多端。
- 自动标注优先：标签、实体、主题应由系统自动生成，用户只负责可选纠错。
- 不依赖在线大模型：默认使用本地语义模型和规则模型，避免隐私、成本和网络依赖问题。

## 3. 目标用户体验

### 3.1 写入记忆

用户输入一段自然语言，系统自动抽取标题、正文、标签、时间、实体、来源等元数据。

示例：

```text
明天下午三点要提醒我跟进张三的合同，他担心付款周期太长。
```

系统保存为：

- 标题：跟进张三合同
- 内容：明天下午三点要提醒我跟进张三的合同，他担心付款周期太长。
- 实体：张三、合同、付款周期
- 时间：明天下午三点
- 标签：提醒、客户、合同

### 3.2 检索记忆

用户可以用问题、陈述句或关键词搜索。

示例：

```text
张三之前担心什么？
```

系统返回：

- 命中的记忆摘要
- 原始内容
- 相关时间和标签
- 匹配原因
- 后续操作入口：追问、编辑、删除、标记重要

## 4. 总体架构

```text
用户输入
  |
  v
接口层 API / CLI / UI
  |
  v
应用服务层
  |-- 记忆写入服务
  |-- 查询理解服务
  |-- 检索编排服务
  |-- 回答生成服务
  |
  v
领域层
  |-- Memory 记忆实体
  |-- MemoryChunk 记忆片段
  |-- Tag 标签
  |-- Entity 实体
  |-- SearchQuery 查询对象
  |
  v
基础设施层
  |-- 关系数据库：保存原文和元数据
  |-- 全文索引：关键词和 BM25 检索
  |-- 向量索引：语义检索
  |-- 本地语义模型 / NLP 模块：标签、实体、摘要、向量化
```

## 5. 模块设计

### 5.0 分层记忆模型

当前 MVP 是扁平 `MemoryRecord` 检索，后续目标是升级为类似人脑回忆路径的分层记忆模型：

```text
L4 Schema 图式
  -> L3 Theme 主题
  -> L2 Concept 概念
  -> L1 Fact 事实
  -> L0 Trace 痕迹
```

检索时不直接扫描全部原文，而是先激活高层图式，再逐层收窄到具体事实和原始痕迹。完整设计见 [分层记忆模型设计](hierarchical-memory-model.md)。

在分层图之外，项目已增加稀疏联想记忆实验链路：用 `MemoryUnit + Synapse + MemoryEngram` 表示可塑性记忆，通过线索注入、扩散激活、竞争抑制和回忆强化完成召回。实现与测试方式见 [稀疏联想记忆 MVP](associative-memory-mvp.md)。

### 5.1 记忆写入服务

职责：

- 接收原始输入。
- 规范化文本，例如去除空白、识别语言、保存来源。
- 生成或更新记忆实体。
- 对长文本切分为片段。
- 提取元数据：标题、标签、实体、时间、地点、重要程度。
- 生成 embedding，用于语义检索。
- 写入数据库、全文索引和向量索引。

建议接口：

```java
MemoryRecord createMemory(CreateMemoryCommand command);
MemoryRecord updateMemory(UpdateMemoryCommand command);
void deleteMemory(MemoryId id);
```

### 5.2 查询理解服务

职责：

- 判断输入类型：问题、陈述句、关键词、命令。
- 提取查询意图：查找事实、查找时间、查找人、查找任务、查找来源。
- 提取过滤条件：时间范围、标签、实体、重要程度。
- 改写查询：生成适合关键词检索和语义检索的多个查询版本。

示例：

```text
输入：上周客户 A 说过什么风险？
意图：查找客户 A 相关风险信息
过滤：时间范围 = 上周，实体 = 客户 A，标签候选 = 风险
```

### 5.3 检索编排服务

职责：

- 同时执行关键词检索、语义检索和结构化过滤。
- 合并多路结果。
- 重排序并去重。
- 给每条结果生成匹配解释。

第一阶段建议实现：

- 关键词检索：数据库 `LIKE` 或轻量全文索引。
- 语义检索：先预留接口，后续接入向量数据库。
- 结构化过滤：基于时间、标签、实体做过滤。

第二阶段升级：

- BM25 全文检索。
- embedding 向量检索。
- hybrid search：关键词分数 + 向量相似度 + 时间衰减 + 重要程度。
- rerank：用模型对候选结果重新排序。

### 5.4 回答生成服务

职责：

- 把检索结果组织为自然语言回答。
- 保留来源引用。
- 当置信度不足时，不强行编造答案，而是展示候选记忆。
- 支持追问上下文。

回答形态：

```text
我找到了 2 条相关记忆：

1. 张三合同付款周期风险
   原文：...
   时间：2026-05-22
   匹配原因：同时命中“张三”“合同”“付款周期”。

综合来看，张三主要担心付款周期太长。
```

### 5.5 自动标注服务

职责：

- 自动生成标签，不要求用户手动打标签。
- 自动抽取实体，例如人名、组织、项目、地点、时间、任务。
- 自动识别主题，例如合同、付款、部署、健康、学习、会议。
- 自动计算标签置信度。
- 当模型不确定时，把标签标记为低置信度，而不是强行写死。
- 接收用户纠错，并把用户确认过的标签作为更高权重的元数据。

用户侧原则：

- 默认不让用户填写标签，降低输入成本。
- 搜索结果页可以允许用户增删标签。
- 编辑记忆时可以显示“系统建议标签”，用户确认后转为“用户标签”。
- 用户标签权重大于系统标签。

标签来源建议：

```text
AUTO_RULE        规则生成，例如时间、明确关键词
AUTO_MODEL       本地语义模型生成
USER_CONFIRMED   用户确认
USER_CREATED     用户手动创建
```

## 6. 数据模型

ID 统一采用雪花 ID。当前实现用进程派生的 worker id 生成 64 位整数，并以十进制字符串保存和展示；后续切 PostgreSQL 时可以存为 `BIGINT`。终端展示完整 ID，避免雪花 ID 时间前缀相同导致短 ID 不易区分。

### 6.1 MemoryRecord

```text
id: Snowflake ID
title: String
content: String
summary: String
source: String
importance: Integer
createdAt: Instant
updatedAt: Instant
occurredAt: Instant?
deletedAt: Instant?
```

### 6.2 MemoryChunk

```text
id: Snowflake ID
memoryId: Snowflake ID
content: String
chunkIndex: Integer
embeddingRef: String?
createdAt: Instant
```

### 6.3 MemoryTag

```text
id: Snowflake ID
memoryId: Snowflake ID
name: String
confidence: Double
source: AUTO_RULE | AUTO_MODEL | USER_CONFIRMED | USER_CREATED
createdAt: Instant
```

### 6.4 MemoryEntity

```text
id: Snowflake ID
memoryId: Snowflake ID
name: String
type: String
confidence: Double
source: AUTO_RULE | AUTO_MODEL | USER_CONFIRMED | USER_CREATED
createdAt: Instant
```

实体类型示例：

- PERSON
- ORGANIZATION
- PROJECT
- LOCATION
- DATE
- TASK
- TOPIC

### 6.5 MemoryTopic

主题是比标签更稳定的高层分类，用来提升搜索和聚合体验。

```text
id: Snowflake ID
memoryId: Snowflake ID
name: String
confidence: Double
source: AUTO_MODEL | USER_CONFIRMED
createdAt: Instant
```

### 6.6 SearchRequest

```text
query: String
limit: Integer
timeRange: TimeRange?
tags: List<String>
entities: List<String>
searchMode: HYBRID | KEYWORD | SEMANTIC
```

### 6.7 SearchResult

```text
memoryId: Snowflake ID
chunkId: Snowflake ID?
title: String
snippet: String
score: Double
matchReason: String
metadata: Map<String, Object>
```

## 7. 检索策略

### 7.1 关键词检索

适合：

- 人名、项目名、精确术语。
- 用户直接输入关键词。
- 需要高可解释性的场景。

排序因素：

- 标题命中优先。
- 标签和实体命中优先。
- 正文命中次数。
- 最近更新时间。
- 重要程度。

### 7.2 语义检索

适合：

- 用户表达和原文不一致。
- 问题式查询。
- 模糊回忆。

示例：

```text
查询：谁对合同付款有顾虑？
原文：张三担心付款周期太长。
```

### 7.3 结构化过滤

适合：

- “上周”“昨天”“最近”
- “客户 A”
- “提醒事项”
- “重要的”

### 7.4 混合排序公式

可从简单公式开始：

```text
finalScore =
  keywordScore * 0.35 +
  semanticScore * 0.45 +
  metadataScore * 0.15 +
  recencyScore * 0.05
```

后续根据真实使用数据调参。

## 8. 技术选型建议

当前项目是 Java 21 + Maven，建议分阶段推进。

### 8.1 MVP 阶段

目标：单机可运行，完成写入和搜索闭环。

建议：

- Web 框架：Spring Boot
- 数据库：H2 或 SQLite
- ORM：Spring Data JDBC 或 JPA
- 搜索：数据库 `LIKE` + 简单打分
- API：REST
- 测试：JUnit 5

优点：

- 本地启动快。
- 依赖少。
- 先验证产品手感。

### 8.2 增强阶段

目标：提升召回质量。

建议：

- 全文检索：PostgreSQL full-text search 或 Lucene
- 向量检索：pgvector、Qdrant、Milvus 三选一
- Embedding：优先接入本地 embedding 模型
- 标签抽取：本地语义模型 + 规则抽取
- 查询改写：先用规则和同义词词典，必要时接入本地小模型
- 摘要生成：MVP 可用原文片段和规则摘要，后续再接本地摘要模型

不建议在默认架构中引入在线大模型：

- 记忆内容通常包含私人信息、客户信息或账号线索。
- 在线调用会带来隐私、成本、延迟和可用性风险。
- 这个产品的核心不是“生成长答案”，而是“准确找回已有事实”。
- 语义标签、向量化和相似度检索可以由本地模型完成。

### 8.3 Python 语义模块

Java 服务可以负责产品主链路，Python 模块负责本地语义能力。推荐采用“Java 主服务 + Python 语义侧车”的方式。

职责划分：

```text
Java / Spring Boot
  - API
  - 权限
  - 记忆写入和查询编排
  - 数据库事务
  - 搜索结果聚合和排序

Python / NLP Service
  - 文本向量化
  - 标签推荐
  - 实体抽取
  - 主题分类
  - 相似度计算或 rerank
```

通信方式：

- MVP：Java 通过 HTTP 调用本地 Python FastAPI 服务。
- 单机部署：Java 服务和 Python 服务放在同一台机器。
- 后续优化：可改成 gRPC 或异步任务队列。

本地模型候选方向：

- 中文 embedding：BGE 系列、E5 系列、多语言 sentence-transformers。
- 中文分词和关键词：jieba、HanLP、THULAC。
- 主题和标签：embedding 相似度 + 规则词典 + 聚类。
- 实体抽取：HanLP、spaCy 中文模型，或轻量 NER 模型。

标签生成策略：

```text
候选标签 =
  规则关键词标签
  + 实体标签
  + 主题分类标签
  + 与已有标签最相似的语义标签

最终标签 =
  去重
  + 置信度排序
  + 用户历史偏好加权
  + 最多保留 3 到 8 个
```

### 8.4 产品化阶段

目标：多用户、权限、同步、审计。

建议：

- 数据库：PostgreSQL
- 缓存：Redis
- 异步任务：Spring Scheduler / MQ
- 权限：用户空间、记忆 ACL
- 部署：Docker Compose 起步

## 9. 推荐包结构

### 9.1 服务化阶段包结构

```text
src/main/java/com/jinjin/memory
  MemoryApplication.java

  api
    MemoryController.java
    SearchController.java

  application
    MemoryCommandService.java
    MemorySearchService.java
    QueryUnderstandingService.java
    AnswerGenerationService.java

  domain
    MemoryRecord.java
    MemoryChunk.java
    MemoryTag.java
    MemoryEntity.java
    SearchRequest.java
    SearchResult.java

  infrastructure
    persistence
      MemoryRepository.java
      JdbcMemoryRepository.java
    search
      KeywordSearchEngine.java
      SemanticSearchEngine.java
      HybridSearchEngine.java
    ai
      EmbeddingClient.java
      LocalNlpClient.java
      TaggingClient.java
      EntityExtractionClient.java
```

### 9.2 终端 MVP 分层结构

终端版 MVP 先不引入 UI 和 HTTP API，但仍然需要把命令解析和命令执行拆开。这样后续切 PostgreSQL、接 Python 语义模块、增加更多命令时，不会把 `Main` 变成一个越来越重的入口类。

推荐分为两个核心模块：

```text
cli
  CommandParser.java
  CommandValidator.java
  ParsedCommand.java
  CommandType.java
  TerminalLoop.java

commands
  CommandExecutor.java
  AddMemoryCommandExecutor.java
  SearchMemoryCommandExecutor.java
  ListMemoryCommandExecutor.java
  ShowMemoryCommandExecutor.java
  DeleteMemoryCommandExecutor.java
  HelpCommandExecutor.java

application
  MemoryService.java
  MemorySearchService.java

domain
  MemoryRecord.java
  SearchResult.java

infrastructure
  persistence
    MemoryRepository.java
    JsonlMemoryRepository.java
    PostgresMemoryRepository.java
  tagging
    AutoTagger.java
  search
    SearchEngine.java
```

## 10. 终端命令架构

### 10.1 架构目标

终端输入不是业务本身，它只是用户操作入口。架构上应避免让终端字符串直接穿透到业务层。

推荐链路：

```text
原始输入字符串
  -> CommandParser
  -> ParsedCommand
  -> CommandValidator
  -> CommandDispatcher
  -> CommandExecutor
  -> Application Service
  -> Repository / Search / Tagging
  -> CommandResult
  -> TerminalRenderer
```

### 10.2 命令解析层

职责：

- 解析原始字符串或命令行参数。
- 识别命令类型，例如 `ADD`、`SEARCH`、`LIST`、`SHOW`、`DELETE`、`HELP`、`EXIT`。
- 提取命令参数，例如记忆内容、查询词、记忆 ID 前缀。
- 做基础校验，例如必填参数是否为空。
- 不直接读写数据库。
- 不直接实现搜索、打标签、删除等业务逻辑。

建议接口：

```java
public interface CommandParser {
    ParsedCommand parse(String input);
}
```

```java
public interface CommandValidator {
    ValidationResult validate(ParsedCommand command);
}
```

```java
public record ParsedCommand(
        CommandType type,
        String rawInput,
        String argument
) {
}
```

```java
public enum CommandType {
    ADD,
    SEARCH,
    LIST,
    SHOW,
    DELETE,
    HELP,
    EXIT,
    UNKNOWN
}
```

命令解析层可以调用执行层，但调用方式应该通过统一的 dispatcher 或 executor 接口，而不是 `switch` 里直接写业务逻辑。

### 10.3 执行层

职责：

- 接收已经解析和校验过的 `ParsedCommand`。
- 调用应用服务完成业务动作。
- 返回结构化的 `CommandResult`。
- 不负责解析原始终端字符串。
- 不负责控制交互循环。

建议接口：

```java
public interface CommandExecutor {
    CommandType supports();

    CommandResult execute(ParsedCommand command);
}
```

```java
public record CommandResult(
        boolean success,
        String message,
        Object payload
) {
}
```

不同命令可以拆成独立 executor：

```text
AddMemoryCommandExecutor
  -> MemoryService.add(content)

SearchMemoryCommandExecutor
  -> MemorySearchService.search(query)

ListMemoryCommandExecutor
  -> MemoryService.list()

ShowMemoryCommandExecutor
  -> MemoryService.findByPrefix(idPrefix)

DeleteMemoryCommandExecutor
  -> MemoryService.deleteByPrefix(idPrefix)
```

### 10.4 分层边界

命令解析层可以知道：

- 命令名称。
- 参数文本。
- 参数是否为空。
- 命令别名，例如 `search` 和 `s`。

命令解析层不应该知道：

- 数据库表结构。
- 标签生成规则。
- 搜索打分公式。
- PostgreSQL 或 JSONL 的存储细节。

执行层可以知道：

- 当前命令要调用哪个 application service。
- 成功和失败如何表达为 `CommandResult`。

执行层不应该知道：

- 用户是在交互模式输入，还是通过单次命令输入。
- 终端提示符如何展示。
- 原始字符串如何切分。

### 10.5 为什么这样拆

- 后续可以把 JSONL 换成 PostgreSQL，不影响命令解析。
- 后续可以增加 Python 语义模块，不影响终端输入解析。
- 后续可以加 HTTP API，共用 application service 和 repository。
- 命令越来越多时，每个 executor 都能单独测试。
- 交互模式和单次命令模式可以共用同一套 parser、validator、executor。

## 11. API 草案

### 11.1 创建记忆

```http
POST /api/memories
Content-Type: application/json

{
  "content": "明天下午三点要提醒我跟进张三的合同，他担心付款周期太长。",
  "source": "manual"
}
```

### 11.2 搜索记忆

```http
POST /api/search
Content-Type: application/json

{
  "query": "张三之前担心什么？",
  "limit": 10
}
```

### 11.3 获取记忆详情

```http
GET /api/memories/{id}
```

### 11.4 更新记忆

```http
PATCH /api/memories/{id}
Content-Type: application/json

{
  "title": "张三合同付款周期风险",
  "importance": 4
}
```

### 11.5 删除记忆

```http
DELETE /api/memories/{id}
```

## 12. 关键流程

### 12.1 写入流程

```text
接收输入
  -> 清洗文本
  -> 生成标题和摘要
  -> 抽取标签、实体、时间
  -> 文本分块
  -> 生成 embedding
  -> 保存原文和元数据
  -> 更新全文索引和向量索引
  -> 返回记忆 ID
```

### 12.2 查询流程

```text
接收一句话
  -> 查询理解
  -> 生成关键词查询
  -> 生成语义查询向量
  -> 执行多路召回
  -> 合并、去重、重排序
  -> 生成摘要和匹配解释
  -> 返回结果
```

## 13. 风险与决策点

### 13.1 记忆质量

风险：自动标签和摘要不准确会影响搜索体验。

策略：

- MVP 允许用户编辑标题、标签和重要程度。
- 保存原文，不丢失信息。
- 自动元数据必须带置信度。

### 13.2 搜索误召回

风险：语义检索可能找出看似相关但实际不相关的内容。

策略：

- 展示匹配原因和原文片段。
- 设置最低分阈值。
- 当置信度低时返回候选，不生成确定答案。

### 13.3 隐私与安全

风险：记忆数据库可能保存敏感信息。

策略：

- 默认本地存储。
- 后续增加加密、导出、删除和审计能力。
- 对接第三方模型前明确哪些字段会被发送。

### 13.4 成本与延迟

风险：每次写入和搜索都调用模型会慢且贵。

策略：

- embedding 只在写入和变更时生成。
- 查询理解可以先用规则实现，再逐步接入模型。
- 缓存常见查询和结果。

## 14. 分阶段里程碑

### Milestone 1：本地 MVP

- Spring Boot 项目初始化。
- 创建记忆 API。
- 搜索 API。
- H2 / SQLite 持久化。
- 基于标题、标签、正文的关键词检索。
- 简单打分和结果摘要。

验收标准：

- 可以写入一条自然语言记忆。
- 可以用关键词搜到。
- 可以用简单问题搜到。
- 搜索结果展示原文、标题、分数和匹配原因。

### Milestone 2：智能元数据

- 自动生成标题和摘要。
- 自动抽取标签和实体。
- 支持时间解析。
- 支持手动编辑元数据。

验收标准：

- 写入后系统能生成可用标题和标签。
- 搜索可以按标签、实体、时间过滤。

### Milestone 3：语义检索

- 接入本地 embedding 模型。
- 引入向量索引。
- 实现 hybrid search。
- 支持问题式查询改写。

验收标准：

- 查询和原文用词不一致时仍能召回。
- 搜索结果排序明显优于纯关键词。

### Milestone 4：回答与追问

- 基于检索结果生成自然语言回答。
- 支持来源引用。
- 支持多轮追问。
- 支持“这个不对”“记住这个修正”等反馈。

验收标准：

- 系统能回答“谁、何时、为什么、之前说过什么”类问题。
- 回答不确定时能显示候选而不是编造。

## 15. 下一步建议

建议先实现 Milestone 1，保持边界清晰：

1. 把项目改造成 Spring Boot。
2. 定义 MemoryRecord、SearchRequest、SearchResult。
3. 用 H2 或 SQLite 保存记忆。
4. 实现 `/api/memories` 和 `/api/search`。
5. 用简单关键词打分模拟最终产品手感。

这样可以最快验证核心体验：一句话输入，能否找到用户脑子里想找的那条记忆。
