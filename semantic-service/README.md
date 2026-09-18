# ChronoEngram Semantic Service

Java 主服务（`src/`）的 **本地语义侧车**，与 Maven 工程同级，独立 Python 运行时。

## 为什么在 `ChronoEngram/semantic-service/`？

```text
ChronoEngram/
  pom.xml              # Java 构建
  src/main/java/...    # 主服务：CLI、记忆图、编排
  docs/                # 架构与产品设计
  data/                # 本地数据（gitignore）
  semantic-service/    # Python FastAPI（本目录）
```

- **单仓协作**：一个仓库里能同时改 Java 编排与 Python 语义，版本对齐。
- **职责清晰**：Java 管事务与记忆图；Python 只管文本理解，不混入 `src/`。
- **部署独立**：侧车可单独 `uvicorn` 启动，端口默认 `8710`，Java 通过 HTTP 调用。
- **后续可拆**：若团队变大，整个目录可迁成独立 repo，接口不变。

## API（`/api/v1`）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/health` | 健康检查、embedding 后端信息 |
| POST | `/analyze` | 写入：标签/实体/分层 hints + 向量 |
| POST | `/understand` | 检索：intent + hints + 查询向量 |
| POST | `/embed` | 批量向量化（补历史节点） |

与 [分层记忆模型](../docs/hierarchical-memory-model.md) 对齐：`layer_hints` 的 `layer` 为 `SCHEMA | THEME | CONCEPT`。

## 快速启动

```bash
cd semantic-service
python3.10+ -m venv .venv   # 最低 3.9（已做兼容）；推荐 3.10+
source .venv/bin/activate
pip install -r requirements.txt

# 无 GPU / 首次联调：用哈希向量回退（不下载模型）
export JINJIN_SEMANTIC_EMBEDDING_BACKEND=none
python -m app.main
```

默认会加载 `BAAI/bge-small-zh-v1.5`（需网络，首次较慢）。生产本地推荐保持默认 `auto`。

```bash
curl -s http://127.0.0.1:8710/api/v1/health | python3 -m json.tool
curl -s -X POST http://127.0.0.1:8710/api/v1/analyze \
  -H 'Content-Type: application/json' \
  -d '{"text":"今天语文课老师抽查登鹳雀楼，我第一句背错了。"}' | python3 -m json.tool
```

## 配置（环境变量前缀 `JINJIN_SEMANTIC_`）

| 变量 | 默认 | 说明 |
|------|------|------|
| `HOST` | `127.0.0.1` | 监听地址 |
| `PORT` | `8710` | 监听端口 |
| `EMBEDDING_MODEL` | `BAAI/bge-small-zh-v1.5` | sentence-transformers 模型 |
| `EMBEDDING_BACKEND` | `auto` | `auto` / `none`（`none` = 仅规则+jieba，哈希向量） |

## 与 Java 的衔接

Java 主服务通过 `SemanticServiceClient` 调用本侧车（默认 `http://127.0.0.1:8710`）：

- **写入** `POST /analyze`：`SemanticEnricher` 生成标签、实体、分层 hints、embedding，并写入 `MemoryEvent`
- **检索** `POST /understand`：`SearchEngine` 使用 intent、hints 与向量相似度增强打分
- **建图** `MemoryIndexer` 优先使用记录上的 `schemaLabel` / `themeLabel` / `conceptLabel`

环境变量：

| 变量 | 说明 |
|------|------|
| `JINJIN_SEMANTIC_URL` | 侧车地址，默认 `http://127.0.0.1:8710`；设为 `off` 禁用 |
| `JINJIN_SEMANTIC_EMBEDDING_BACKEND` | Python 侧：`auto` / `none` |

联调步骤：

```bash
# 终端 1：启动语义侧车
cd semantic-service && source .venv/bin/activate
export JINJIN_SEMANTIC_EMBEDDING_BACKEND=none
python -m app.main

# 终端 2：Java CLI（在 ChronoEngram 根目录）
javac -encoding UTF-8 -d target/classes $(find src/main/java -name '*.java')
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main add 今天语文课老师抽查登鹳雀楼
java -Dfile.encoding=UTF-8 -cp target/classes com.jinjin.memory.Main search 登鹳雀楼
```
