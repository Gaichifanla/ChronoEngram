# 稀疏联想记忆 MVP

## 1. 目标

这一版不模拟神经元的生物电活动，而是实现与人类联想记忆相关的几个核心机制：

- 稀疏编码：一条记忆由少量语义单元共同表示。
- 共激活学习：经常共同出现的单元形成更强连接。
- 扩散激活：查询线索沿连接传播，唤起相关单元。
- 竞争抑制：每轮只保留激活度最高的少量单元。
- 回忆强化：成功召回后，相关单元和连接轻微增强。
- 原文溯源：最终结果仍然回到原始 `MemoryEvent`。

## 2. 数据结构

### MemoryUnit

可复用的语义单元，分为：

```text
SCHEMA   学习、工作、生活、健康
THEME    古诗、生活常识、部署问题
CONCEPT  登鹳雀楼、睡眠、数据库
ENTITY   王之涣、张三、某个项目
FEATURE  全文、第一句、背诵、权限
```

单元保存出现次数、激活次数、稳定度和最近激活时间。

### Synapse

两个单元共同出现在一条记忆中时建立连接。共同出现次数越多，连接权重越高。

### MemoryEngram

一条原始记忆对应一个稀疏激活模式：

```text
traceId -> [unitId, unitId, ...] + [strength, strength, ...]
```

原始内容不重复存入 Engram，通过 `traceId` 回到 `memories.jsonl`。

## 3. 写入和学习

```text
MemoryEvent
  -> SparseUnitEncoder
  -> Top-K 语义单元
  -> 更新单元出现次数
  -> 增强共激活突触
  -> 保存 MemoryEngram
```

新增记忆会增量学习。已有记忆可以通过 `relearn` 全量重建。

## 4. 回忆算法

```text
查询
  -> 匹配初始线索单元
  -> 按单元稀有度调整初始激活
  -> 沿突触传播两轮
  -> 每轮保留 Top 64 激活单元
  -> 和 Engram 稀疏模式计算匹配分数
  -> 返回原始记忆和激活路径
  -> 强化第一名的成功回忆路径
```

高频单元会受到稀有度抑制，避免“生活常识”这类高频词压过“睡眠”“登鹳雀楼”等具体线索。

## 5. 终端命令

重建联想网络：

```text
relearn
```

进行联想回忆：

```text
recall 王之涣写过什么
recall 登鹳雀楼内容
recall 睡眠有什么建议
```

`search` 仍然保留，作为关键词、分层图和语义检索基线；`recall` 使用新的扩散激活链路。

## 6. 文件布局

```text
data/associative/units.jsonl
data/associative/synapses.jsonl
data/associative/engrams.jsonl
```

10 万条当前测试数据重建后的规模：

```text
MemoryUnit: 40
Synapse: 94
MemoryEngram: 100003
Engram 文件: 约 25MB
```

一次 10 万条规模的联想查询当前约为 0.9 秒，仍然需要通过倒排 posting 或 PostgreSQL 索引优化。

## 7. 当前限制

- 稀疏单元主要由规则和已有语义标签生成。
- 没有模拟脉冲、膜电位或神经元时序。
- 10 万条模板数据语义重复度高，只能用于压力测试。
- Engram 查询目前需要扫描文件，规模继续增长后会变慢。
- 强化已经实现，遗忘衰减和睡眠式 consolidation 尚未实现。
- 当前输出是“联想到哪些原始记忆”，还没有做答案重构。

## 8. 后续研究方向

1. 建立人工标注的查询与正确回忆集合。
2. 比较 `search` 与 `recall` 的 Recall@K、MRR、延迟和扫描量。
3. 将 Unit -> Engram 建成倒排索引，避免全量扫描。
4. 引入时间衰减、突触遗忘和记忆巩固。
5. 用本地 embedding 将文本映射为更稳定的稀疏单元。
6. 将 units、synapses、engrams 迁移到 PostgreSQL。
