package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public class HelpCommandExecutor implements CommandExecutor {
    public static final String HELP = """
            可用命令：
              add 记忆内容                 保存一条记忆
              search 查询内容              用问题、陈述句或关键词搜索
              recall 回忆线索              用扩散激活进行联想回忆
              timeline 主题或事件关键词     按发生时间查看同主题事件链
              list                         列出所有记忆
              show 记忆ID前缀              查看记忆详情
              delete 记忆ID前缀            删除记忆
              seed [数量]                  批量写入生活常识测试记忆，默认 100000 条
              reindex                      清空并重建分层图 + 关键词倒排索引
              relearn                      从现有记忆重建稀疏联想网络
              embed [数量] [--force]       批量补向量（需 semantic-service）
              stats                        查看记忆/图/索引/侧车状态
              exit                         退出交互模式

            语义侧车（可选）：
              先启动 semantic-service（默认 http://127.0.0.1:8710）
              禁用：export JINJIN_SEMANTIC_URL=off

            示例：
              add 明天下午三点提醒我跟进张三合同，他担心付款周期太长。
              search 张三之前担心什么？
              recall 王之涣写过什么？
              timeline 张三离职
              reindex
              relearn
              embed 500
              stats
            """;

    @Override
    public CommandType supports() {
        return CommandType.HELP;
    }

    @Override
    public CommandResult execute(ParsedCommand command) {
        return CommandResult.success(HELP);
    }
}
