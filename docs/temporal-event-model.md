# 时间事件模型

## 1. 核心定义

系统只为一条记忆事实保留两个语义时间：

- `occurredTime`：被描述事件实际发生的时间，可带范围、精度、原始表达和置信度。
- `learnedAt`：用户把这件事写入系统、也就是系统得知这件事的时间。

`confirmedAt`、`correctedAt`、`lastRecalledAt` 不属于事实字段。确认、纠正和回忆本身都是新事件，它们同样拥有自己的 `occurredTime` 和 `learnedAt`。

## 2. 事件模型

`MemoryEvent` 是不可变事件，主要字段如下：

```text
MemoryEvent
  id
  topicKey
  eventType: OBSERVATION | ACTION | CONFIRMATION | CORRECTION | UPDATE | RECALL
  content / tags / entities / hierarchy / embedding
  occurredTime: TemporalRange
  learnedAt: Instant
  source / confidence / deleted
```

`TemporalRange` 不强迫系统伪造精确时刻。它保存 `start`、`end`、`precision`、`originalExpression` 和 `confidence`。没有明确发生时间时，以 `learnedAt` 作为查询锚点，但精度标记为 `INFERRED`，表示“发生时间未知”。

## 3. 主题事件链

同一主题使用稳定的 `topicKey` 聚合。事件间关系单独写入 `data/timeline/relations.jsonl`：

```text
第一次得知事件
  -> CONFIRMS -> 后续确认事件
  -> CORRECTS -> 后续纠正事件
  -> UPDATES  -> 后续状态更新
  -> RECALLS  -> 后续回忆事件
```

关系链按照 `learnedAt` 表达认知演进顺序；`timeline` 命令按照 `occurredTime` 展示客观事件时间轴。两者不能混成一个时间字段。

`recall` 命令成功命中后，会把本次回忆作为新的 `RECALL` 事件追加到命中主题。神经网络仍可更新激活和突触强度，但这些内部状态不会冒充领域事件时间。

## 4. 示例

输入：

```text
add 张三上周一离职，我今天才知道。
add HR今天确认张三已经离职。
timeline 张三离职
```

第一条事件的 `occurredTime` 是上周一，`learnedAt` 是今天。第二条是今天发生并今天得知的 `CONFIRMATION`，通过 `CONFIRMS` 指向同主题的前一条事件。

## 5. 旧数据兼容

旧 JSONL 中只有 `createdAt` 的记录不会被重写。读取时将其解释为 `learnedAt`，并生成置信度较低的 `INFERRED occurredTime`；事件类型默认为 `OBSERVATION`，来源标记为 `legacy`。

神经元、突触的激活时间属于索引引擎的可塑性状态，不是用户记忆事实，因此仍可作为内部技术字段存在。
