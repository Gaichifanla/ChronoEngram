# 终端版 MVP 使用说明

当前 MVP 是一个本地终端记忆数据库，不依赖在线大模型，也不需要可视化界面。

## 运行方式

如果使用 IntelliJ IDEA，可以直接运行 `com.jinjin.memory.Main`。

如果使用命令行，可以先编译：

```bash
javac -encoding UTF-8 -d target/classes $(find src/main/java -name '*.java')
```

再执行命令：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main help
```

## 交互模式

不带参数运行会进入交互模式：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main
```

进入后可以输入：

```text
add 明天下午三点提醒我跟进张三合同，他担心付款周期太长。
search 张三之前担心什么？
recall 张三之前担心什么？
timeline 张三合同
list
show 记忆ID前缀
delete 记忆ID前缀
seed 100000
relearn
exit
```

## 单次命令模式

保存一条记忆：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main add 明天下午三点提醒我跟进张三合同，他担心付款周期太长。
```

搜索记忆：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main search 张三之前担心什么？
```

列出记忆：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main list
```

批量写入 100000 条生活常识测试记忆：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main seed 100000
```

从现有原始记忆重建稀疏联想网络：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main relearn
```

使用扩散激活进行联想回忆：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main recall 睡眠有什么建议
```

## 数据位置

数据默认保存在项目目录：

```text
data/memories.jsonl
```

这个目录已经加入 `.gitignore`，避免把本地个人记忆提交到版本库。

## MVP 已支持

- 保存自然语言记忆。
- 自动生成短标题。
- 基于规则自动生成标签。
- 简单抽取实体。
- 支持问题、陈述句、关键词搜索。
- 返回匹配分数、匹配原因、标签和原文片段。
- 支持查看和删除记忆。
- 支持 `seed [数量]` 批量写入生活常识测试记忆。
- 支持按发生时间和得知时间查看同主题事件链。

## 语义侧车（可选）

在 `semantic-service/` 目录启动 Python FastAPI（默认 `http://127.0.0.1:8710`）后，Java CLI 会自动调用语义分析增强写入与搜索。禁用：`export JINJIN_SEMANTIC_URL=off`。详见 [semantic-service/README.md](../semantic-service/README.md)。

## 分层图检索

首次使用或数据变更后，建议执行一次：

```bash
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main reindex
```

会在 `data/graph/` 生成 `nodes.jsonl` 与 `links.jsonl`。`search` 会合并 **关键词打分** 与 **分层回忆路径**（Schema → Theme → Concept → Trace）。

## 批量补向量

需先启动 `semantic-service`：

```bash
java ... Main embed          # 为所有缺失向量的记忆补 embedding
java ... Main embed 500      # 最多处理 500 条
java ... Main embed 100 --force   # 强制重算 100 条
```

## 状态查看

```bash
java ... Main stats
```

## 仍待增强

- 向量近似索引（当前补向量后仍线性比对有向量的记录）。
- PostgreSQL / REST API。
- 增强中文实体识别。
- 增加用户确认标签和标签权重。
- 拆分 Java 类结构，加入单元测试。
