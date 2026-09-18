# ChronoEngram

ChronoEngram（时忆）是一个本地优先的实验性记忆数据库。它以不可变事件为事实底座，同时使用双时间轴、分层语义图和稀疏联想网络组织与检索记忆。

项目不依赖在线大模型。可选的 Python 语义侧车使用本地语义模型，也可以关闭并回退到 Java 规则引擎。

## 核心模型

- **双时间事件**：分别记录事情实际发生的 `occurredTime` 和系统得知它的 `learnedAt`。
- **认知事件流**：确认、纠正、更新和回忆都是新的不可变事件，不覆盖历史认知。
- **主题时间轴**：同一主题通过 `CONFIRMS`、`CORRECTS`、`UPDATES`、`RECALLS` 等关系串联。
- **分层语义图**：按照 Schema → Theme → Concept → Trace 组织回忆路径。
- **联想记忆网络**：使用 MemoryUnit、Synapse 和 MemoryEngram 实现稀疏编码与扩散激活。
- **多路召回**：组合关键词、分层图、可选语义向量和联想网络。

## 快速开始

需要 JDK 21。

```bash
mkdir -p target/classes
javac --release 21 -encoding UTF-8 -d target/classes $(find src/main/java -name '*.java')

# 禁用可选语义侧车，直接启动终端
JINJIN_SEMANTIC_URL=off java -Dfile.encoding=UTF-8 \
  -cp target/classes com.jinjin.memory.Main
```

进入终端后可以尝试：

```text
add 张三上周一离职，我今天才知道。
add HR今天确认张三已经离职。
timeline 张三离职
search 张三发生了什么？
recall 张三离职
timeline 张三离职
stats
```

## 主要命令

| 命令 | 作用 |
|---|---|
| `add <内容>` | 写入一条自然语言记忆事件 |
| `search <查询>` | 混合搜索记忆 |
| `recall <线索>` | 使用扩散激活进行联想回忆 |
| `timeline <主题>` | 查看同主题事件时间轴 |
| `list` / `show <id>` | 列出或查看记忆 |
| `reindex` | 重建分层图、时间轴和关键词索引 |
| `relearn` | 重建稀疏联想网络 |
| `stats` | 查看记忆与索引状态 |

## 项目结构

```text
src/main/java/       Java CLI、领域模型、检索与持久化
semantic-service/    可选 Python 本地语义侧车
docs/                架构与模型设计文档
data/                本地记忆数据（已被 Git 忽略）
```

详细文档：

- [时间事件模型](docs/temporal-event-model.md)
- [分层记忆模型](docs/hierarchical-memory-model.md)
- [联想记忆 MVP](docs/associative-memory-mvp.md)
- [终端使用说明](docs/mvp-terminal-usage.md)
- [命令架构](docs/terminal-command-architecture.md)

## 当前状态

项目处于 MVP / research prototype 阶段。目前默认使用 JSONL 持久化，后续计划迁移到 PostgreSQL，并继续完善中文时间解析、实体识别、索引性能与评测体系。
