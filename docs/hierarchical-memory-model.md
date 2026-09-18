# 分层记忆模型设计

> 本文描述空间/语义组织；当前时间维度采用不可变事件流，详见 [temporal-event-model.md](temporal-event-model.md)。旧段落中的 `MemoryRecord` 应理解为现有 `MemoryEvent`。

## 1. 设计目标

早期 MVP 的搜索方式是扁平的：所有记忆事件平铺存储，查询时做关键词打分。这适合验证“能搜到”，但不适合模拟人的回忆过程。

人的回忆通常不是直接扫描全部细节，而是：

```text
触发线索
  -> 激活高层图式
  -> 收窄到主题
  -> 定位具体概念
  -> 取出事实和原始情景
```

例如：

```text
老师问我登鹳雀楼的内容
  -> 学习
  -> 语文 / 古诗
  -> 登鹳雀楼
  -> 全文、作者、名句、课堂背诵经历
```

所以我们要构建的不是“文件夹式分类”，而是“可解释的回忆路径”：每次搜索都能回答系统是如何从一个线索一步步想起来的。

## 2. 对已有方案的修正

你给出的五层模型方向正确，但需要做三点修正。

### 2.1 不是纯树，而是主树 + 图

`Schema -> Theme -> Concept -> Fact -> Trace` 可以作为主归属路径，但真实记忆会有大量横向关联。

例如：

```text
登鹳雀楼 -> 王之涣
登鹳雀楼 -> 黄河入海流
登鹳雀楼 -> 语文课抽查
登鹳雀楼 -> 唐诗
```

因此底层模型应该是图结构：

- `PART_OF` 表示主层级归属。
- `RELATED` 表示横向相关。
- `DERIVED_FROM` 表示从原始痕迹抽取。
- `SAME_EVENT` 表示同一事件。
- `ALIAS_OF` 表示同义、别名、简称。

### 2.2 不要一开始强依赖大模型抽取 Fact

L1 Fact 很有价值，但它最难自动生成。MVP 阶段如果过度依赖高质量事实抽取，会拖慢产品闭环。

建议：

- MVP：先生成 Trace、Concept、Theme、Schema，Fact 可以由规则和模板少量生成。
- 增强阶段：引入本地 NLP / embedding 模型辅助抽取 Fact。
- 成熟阶段：做定期 consolidation，把多条 Trace 压缩成更可靠的 Fact 和 Concept 摘要。

### 2.3 标签不是终点，而是节点候选

当前 `AutoTagger` 生成的标签，不应只是挂在 `MemoryRecord` 上的字符串。它们应该成为或指向记忆图里的节点候选。

例如：

```text
标签：古诗
  -> Theme 节点：古诗

实体：登鹳雀楼
  -> Concept 节点：登鹳雀楼

标签：学习
  -> Schema 节点：学习
```

## 3. 五层记忆模型

### L0 Trace 痕迹层

含义：原始输入和情景记录。

保存：

- 用户原文。
- 时间。
- 来源。
- 场景。
- 上下文。

示例：

```text
今天语文课老师抽查登鹳雀楼，我第一句背错了。
```

作用：

- 保留原始证据。
- 回答“我什么时候说过 / 发生过什么”。
- 作为所有上层节点的来源。

### L1 Fact 事实层

含义：可独立成立的语义碎片。

示例：

```text
登鹳雀楼的作者是王之涣。
登鹳雀楼首句是白日依山尽。
我在语文课抽查时背错过登鹳雀楼第一句。
```

作用：

- 回答具体事实问题。
- 支撑问答和摘要。
- 连接 Trace 与 Concept。

注意：Fact 必须带 `confidence` 和 `sourceTraceIds`，因为自动抽取可能出错。

### L2 Concept 概念层

含义：一个具体对象、知识点、人、项目、地点、事项。

示例：

```text
登鹳雀楼
张三合同
生产服务器部署
每天喝水
```

保存：

- 概念名。
- 别名。
- 摘要。
- 关键实体。
- 代表性事实。
- 来源痕迹。

作用：

- 作为搜索的主要定位层。
- 把多条原始记忆压缩成一个稳定对象。

### L3 Theme 主题层

含义：一组相关概念的归纳。

示例：

```text
古诗
客户合同
部署问题
生活常识
健康习惯
```

作用：

- 做中层收窄。
- 支撑“我学过哪些古诗”“最近有哪些部署问题”这类问题。

### L4 Schema 图式层

含义：最高层生活域、工作域或长期语境。

示例：

```text
学习
工作
生活
健康
财务
人际关系
```

作用：

- 快速激活大方向。
- 对主题提供先验权重。
- 让系统不是先扫所有原文，而是先选择回忆域。

## 4. 核心数据模型

### 4.1 MemoryNode

```text
id: Snowflake ID
layer: TRACE | FACT | CONCEPT | THEME | SCHEMA
label: String
summary: String
content: String?
status: ACTIVE | ARCHIVED | DELETED
confidence: Double
importance: Integer
createdAt: Instant
updatedAt: Instant
lastActivatedAt: Instant?
activationCount: Long
```

字段说明：

- `label` 是节点显示名，例如“登鹳雀楼”。
- `summary` 是该层抽象摘要，上层检索主要看它。
- `content` 只在 Trace / Fact 层保留较完整内容。
- `activationCount` 和 `lastActivatedAt` 用来模拟“越常用越容易想起来”。

### 4.2 MemoryLink

```text
id: Snowflake ID
fromNodeId: Snowflake ID
toNodeId: Snowflake ID
type: PART_OF | INSTANCE_OF | RELATED | DERIVED_FROM | SAME_EVENT | ALIAS_OF | CONTRADICTS
weight: Double
confidence: Double
createdAt: Instant
updatedAt: Instant
```

关键关系：

- `PART_OF`：登鹳雀楼属于古诗。
- `INSTANCE_OF`：登鹳雀楼是唐诗。
- `RELATED`：登鹳雀楼关联王之涣。
- `DERIVED_FROM`：某个 Fact 来自某条 Trace。
- `SAME_EVENT`：几条 Trace 属于同一次课堂事件。
- `ALIAS_OF`：登鹳雀楼和“鹳雀楼”可能是别名关系。
- `CONTRADICTS`：两条事实相互冲突，用于后续纠错。

### 4.3 MemoryEvidence

为了避免上层摘要失真，需要单独保存证据关系。

```text
id: Snowflake ID
nodeId: Snowflake ID
traceId: Snowflake ID
quote: String
confidence: Double
createdAt: Instant
```

作用：

- 回答时可追溯。
- 用户纠错时能定位错误来源。
- consolidation 时知道摘要来自哪些原文。

### 4.4 MemoryEmbedding

后续引入本地语义模型时，embedding 不必塞进主表。

```text
id: Snowflake ID
nodeId: Snowflake ID
modelName: String
vectorRef: String
createdAt: Instant
```

如果使用 PostgreSQL + pgvector，可以把向量直接存在该表；如果使用外部向量库，`vectorRef` 指向外部索引。

## 5. 写入流程

写入不再只是 append 一条记录，而是“保存痕迹 + 建立索引节点 + 向上压缩”。

```text
用户输入
  -> 创建 L0 Trace
  -> 解析 cues：实体、关键词、时间、场景
  -> 找或建 L2 Concept
  -> 找或建 L3 Theme
  -> 找或建 L4 Schema
  -> 可选生成 L1 Fact
  -> 建立 MemoryLink
  -> 更新上层 summary / activation
```

### 5.1 MVP 写入策略

在不引入在线大模型的前提下，第一版可以这样做：

- 原文一定进入 L0 Trace。
- 自动标签升级为 Theme 候选。
- 自动实体升级为 Concept 候选。
- 预置少量 Schema：学习、工作、生活、健康、财务、人际。
- 用规则判断 Schema，例如“老师、古诗、学习、课程”倾向学习。
- Fact 暂时只对明显模板生成，例如“X 的作者是 Y”“X 首句是 Y”。

### 5.2 Consolidation 向上压缩

当同一 Concept 下 Trace 数量增加时，系统定期更新摘要。

```text
多条关于登鹳雀楼的 Trace
  -> 更新 Concept 摘要
  -> 更新 Theme 下的代表概念
  -> 保留来源证据
```

MVP 可以先做简单规则：

- 每次写入都更新 `activationCount`。
- Concept 摘要先使用最新或最高权重 Trace 的标题。
- 同一 label 的 Concept 合并。

后续再引入本地摘要模型。

## 6. 检索流程：Cascade Recall

### 6.1 查询理解

不要直接把查询拆成关键词。先抽取回忆线索。

```text
query: 老师问我登鹳雀楼的内容
intent: RECALL_CONTENT
schemaHints: [学习]
themeHints: [语文, 古诗]
conceptHints: [登鹳雀楼]
factHints: [全文, 内容]
detailLevel: FACT
```

MVP 阶段可以用规则和词典做：

- “老师、课、背诵、古诗” -> 学习。
- “部署、接口、数据库、服务器” -> 工作 / 技术。
- “吃饭、睡眠、运动、喝水” -> 生活 / 健康。

### 6.2 分层激活

检索不是直接扫 L0，而是分层打分。

```text
L4 Schema score
  -> L3 Theme score
  -> L2 Concept score
  -> L1 Fact score
  -> L0 Trace score
```

每层分数：

```text
score =
  keywordScore * 0.30
  + entityScore * 0.20
  + semanticScore * 0.25
  + activationScore * 0.10
  + parentPathBoost * 0.15
```

MVP 没有 embedding 时，`semanticScore` 可以先置 0，由关键词、实体、父路径权重支撑。

### 6.3 扩散激活

当上层命中时，给下层节点加权。

```text
命中 Schema: 学习
  -> 学习下的 Theme 加 parentPathBoost

命中 Theme: 古诗
  -> 古诗下的 Concept 加 parentPathBoost

命中 Concept: 登鹳雀楼
  -> 相关 Fact / Trace 加 parentPathBoost
```

横向关联也可以扩散，但权重要低于主路径。

```text
登鹳雀楼 -> 王之涣
登鹳雀楼 -> 黄河入海流
```

这样用户问“王之涣写过什么”，也可能激活“登鹳雀楼”。

## 7. 返回结果

搜索结果不应只返回 snippet，而要返回回忆路径。

```text
RecallResult {
    nodeId: Snowflake ID
    layer: FACT | TRACE | CONCEPT
    answer: String
    recallPath: [学习, 古诗, 登鹳雀楼, 登鹳雀楼全文]
    evidenceTraceIds: [...]
    score: Double
    reason: String
}
```

示例：

```text
回忆路径：学习 -> 古诗 -> 登鹳雀楼
命中内容：白日依山尽，黄河入海流。欲穷千里目，更上一层楼。
匹配原因：Schema“学习”命中，Theme“古诗”命中，Concept“登鹳雀楼”精确命中。
来源痕迹：2026-05-20 语文课抽查背诵。
```

## 8. PostgreSQL 表设计草案

### memory_nodes

```sql
create table memory_nodes (
  id bigint primary key,
  layer varchar(20) not null,
  label text not null,
  summary text,
  content text,
  status varchar(20) not null default 'ACTIVE',
  confidence double precision not null default 1.0,
  importance int not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  last_activated_at timestamptz,
  activation_count bigint not null default 0
);
```

### memory_links

```sql
create table memory_links (
  id bigint primary key,
  from_node_id bigint not null references memory_nodes(id),
  to_node_id bigint not null references memory_nodes(id),
  type varchar(30) not null,
  weight double precision not null default 1.0,
  confidence double precision not null default 1.0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

### memory_evidence

```sql
create table memory_evidence (
  id bigint primary key,
  node_id bigint not null references memory_nodes(id),
  trace_id bigint not null references memory_nodes(id),
  quote text,
  confidence double precision not null default 1.0,
  created_at timestamptz not null
);
```

建议索引：

```sql
create index idx_memory_nodes_layer on memory_nodes(layer);
create index idx_memory_nodes_label on memory_nodes(label);
create index idx_memory_links_from on memory_links(from_node_id);
create index idx_memory_links_to on memory_links(to_node_id);
create index idx_memory_links_type on memory_links(type);
```

后续可加：

- PostgreSQL full-text index。
- pgvector embedding index。
- `(layer, label)` 唯一或近似去重策略。

## 9. 和当前代码的演进关系

当前实现：

```text
MemoryRecord
  -> 原始记忆
AutoTagger
  -> 标签和实体字符串
SearchEngine
  -> 平铺关键词搜索
JsonlMemoryRepository
  -> memories.jsonl
```

目标实现：

```text
MemoryNode
  -> 所有层级节点
MemoryLink
  -> 节点关系
MemoryEvidence
  -> 溯源证据
MemoryIndexer
  -> 写入时创建/更新节点和边
HierarchicalRecallEngine
  -> 分层激活和路径解释
PostgresMemoryGraphRepository
  -> PostgreSQL 节点图存储
```

建议演进顺序：

1. 保留 `MemoryRecord`，新增 `MemoryNode` / `MemoryLink`。
2. 新增 `MemoryGraphRepository`，先用 JSONL 存 `nodes.jsonl` 和 `links.jsonl`。
3. 新增 `MemoryIndexer`，写入时同时生成 Trace、Theme、Schema、Concept。
4. 新增 `HierarchicalRecallEngine`，先用规则分层召回。
5. 切 PostgreSQL，把 `nodes`、`links`、`evidence` 入库。
6. 引入本地 embedding，增强 Concept / Theme 的语义匹配。
7. 做定期 consolidation，自动更新上层摘要。

## 10. MVP 分层版本

为了最快落地，第一版分层记忆不要追求完整五层全部智能化。

建议 MVP：

```text
L0 Trace：每条输入都创建
L2 Concept：从实体和标题中创建
L3 Theme：从标签创建
L4 Schema：从规则词典创建
L1 Fact：先预留，少量规则生成
```

最小回忆路径：

```text
Schema -> Theme -> Concept -> Trace
```

对于“登鹳雀楼”：

```text
学习 -> 古诗 -> 登鹳雀楼 -> 今天语文课老师抽查登鹳雀楼...
```

这已经比平铺搜索更接近人的回忆方式，并且可以在不引入在线大模型的前提下实现。
