# 终端命令架构设计

> 领域对象已由 `MemoryRecord` 升级为 `MemoryEvent`，时间语义见 [temporal-event-model.md](temporal-event-model.md)。本文中的旧类型名仅用于说明最初的命令分层设计。

## 1. 背景

当前 MVP 已经能通过终端保存和搜索记忆，但命令解析、校验、执行、输出展示都集中在 `Main` 里。这个结构适合快速验证，不适合继续扩展。

下一步在改 PostgreSQL 之前，先拆出命令解析层和执行层。

## 2. 分层目标

```text
用户输入
  -> 命令解析层
  -> 命令执行层
  -> 应用服务层
  -> 基础设施层
```

核心原则：

- 命令解析层只理解“用户想执行什么命令”。
- 命令执行层只负责“执行已经确定的命令”。
- 应用服务层负责记忆业务。
- 基础设施层负责存储、搜索、标签等实现细节。

## 3. 命令解析层

职责：

- 接收原始输入字符串。
- 识别命令名称和别名。
- 提取参数。
- 校验参数是否满足最低要求。
- 生成结构化命令对象。

不做：

- 不访问数据库。
- 不执行搜索。
- 不生成标签。
- 不拼接终端输出结果。

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

## 4. 命令执行层

职责：

- 接收解析后的 `ParsedCommand`。
- 根据命令类型调用对应的业务服务。
- 返回结构化执行结果。

不做：

- 不解析原始字符串。
- 不控制交互循环。
- 不直接关心终端提示符。

建议接口：

```java
public interface CommandExecutor {
    CommandType supports();

    CommandResult execute(ParsedCommand command);
}
```

```java
public interface CommandDispatcher {
    CommandResult dispatch(ParsedCommand command);
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

执行器拆分：

```text
AddMemoryCommandExecutor
SearchMemoryCommandExecutor
ListMemoryCommandExecutor
ShowMemoryCommandExecutor
DeleteMemoryCommandExecutor
HelpCommandExecutor
ExitCommandExecutor
```

## 5. 应用服务层

应用服务层不关心用户从终端来，还是未来从 HTTP API 来。

建议接口：

```java
public interface MemoryCommandService {
    MemoryRecord add(String content);

    boolean deleteByPrefix(String idPrefix);
}
```

```java
public interface MemoryQueryService {
    List<MemoryRecord> list();

    Optional<MemoryRecord> findByPrefix(String idPrefix);

    List<SearchResult> search(String query, int limit);
}
```

## 6. 推荐调用链

### 6.1 交互模式

```text
TerminalLoop
  -> readLine()
  -> CommandParser.parse(line)
  -> CommandValidator.validate(command)
  -> CommandDispatcher.dispatch(command)
  -> TerminalRenderer.render(result)
```

### 6.2 单次命令模式

```text
Main(args)
  -> join args
  -> CommandParser.parse(input)
  -> CommandValidator.validate(command)
  -> CommandDispatcher.dispatch(command)
  -> TerminalRenderer.render(result)
```

交互模式和单次命令模式应共用同一套 parser、validator、dispatcher。

## 7. 包结构草案

```text
src/main/java/com/jinjin/memory
  Main.java

  cli
    TerminalLoop.java
    TerminalRenderer.java

  cli/command
    CommandParser.java
    DefaultCommandParser.java
    CommandValidator.java
    DefaultCommandValidator.java
    ParsedCommand.java
    CommandType.java
    CommandResult.java
    CommandDispatcher.java
    DefaultCommandDispatcher.java

  cli/command/executor
    CommandExecutor.java
    AddMemoryCommandExecutor.java
    SearchMemoryCommandExecutor.java
    ListMemoryCommandExecutor.java
    ShowMemoryCommandExecutor.java
    DeleteMemoryCommandExecutor.java
    HelpCommandExecutor.java

  application
    MemoryCommandService.java
    MemoryQueryService.java
    DefaultMemoryService.java

  domain
    MemoryRecord.java
    SearchResult.java

  infrastructure
    persistence
      MemoryRepository.java
      JsonlMemoryRepository.java
      PostgresMemoryRepository.java
    search
      SearchEngine.java
    tagging
      AutoTagger.java
```

## 8. 和 PostgreSQL 改造的关系

先拆命令架构，再换 PostgreSQL，会更稳。

原因：

- PostgreSQL 只影响 `MemoryRepository` 实现。
- 命令解析层完全不需要变化。
- 执行层最多只依赖 application service，不直接依赖数据库。
- 可以保留 `JsonlMemoryRepository` 做本地快速测试，再新增 `PostgresMemoryRepository`。

建议顺序：

1. 从 `Main` 拆出 parser、validator、dispatcher、executor。
2. 从 `Main` 拆出 domain、application、infrastructure 类。
3. 保持 JSONL 存储，确认行为不变。
4. 新增 PostgreSQL 表结构和 `PostgresMemoryRepository`。
5. 通过配置选择 JSONL 或 PostgreSQL。
