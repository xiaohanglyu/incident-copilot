# 架构与设计

[English](../en/architecture.md) · **简体中文** · [项目介绍](../../README.zh-CN.md)

应用由 Spring Boot 后端和 React 前端组成。后端检索处理手册、向模型提供查询工具，
并返回结构化报告；前端展示排查进度、工具结果和最终报告。
当前工具读取项目内的样例文件，本地对话模型配置使用 Gemma。

## 一次请求如何执行

```text
浏览器 / API 客户端
    → InvestigationController：接收问题及可选的时间、服务线索
    → InvestigationService：按问题检索处理手册，组装模型输入
    → ChatClient：由 Spring AI 处理模型与工具之间的调用
        → 服务列表、服务说明、指标、日志、变更工具
        → FixtureLoader：读取样例文件
        → ToolCallLog：记录每次工具调用
    → Report：解析模型返回的原因、证据和建议
    → InvestigationResult：合并报告、工具记录和检索片段
    → 客户端
```

知识检索在模型调用前执行一次，当前不会根据后续调查结果重新检索。
模型决定调用哪些工具以及调用参数；系统提示词要求它先读取服务说明，再解释指标和日志，
但代码没有强制校验这个顺序。

当前编排只有一次 `ChatClient.call()`，这不代表底层只有一次模型请求。
Spring AI 可以在这次调用中处理多次工具往返。路线图中的“多轮调查”指后续由应用显式管理假设、
补充查询、重新检索和停止条件。

检索由编排代码显式调用，命中的片段会保留在最终响应中，便于核对报告引用。

## 数据和工具

| 工具 | 用途 | 当前数据来源 |
|---|---|---|
| `listServices` | 列出可查询的服务 | fixtures 下的服务目录 |
| `describeService` | 查询技术栈、字段单位、时区和基线 | `profile.md` |
| `getMetrics` | 查询指标 | `metrics.txt` |
| `searchLogs` | 查询日志 | `logs.txt` |
| `getRecentChanges` | 查询发布和变更 | `changes.txt` |

指标和日志可以按工具参数中的时间窗过滤。起始时间会按 `window-lookback` 向前扩展，
默认扩展一小时，以便查看异常出现前的数据。变更记录始终完整返回。
没有匹配时间段的数据时，工具返回可用数据范围；服务无法匹配时，返回已知服务列表。

服务名匹配除了精确匹配，还允许大小写归一化及包含关系匹配。

工具按查询能力拆分，便于各自处理真实数据源的客户端、凭证和查询语法。
时间窗向前扩展是为了寻找更早的线索，不能据此认定最近一次发布就是根因。
用户提供的服务名同样只是线索；跨服务调查仍待评估验证。

## 知识库

启动时，`KnowledgeIndexer` 读取 Markdown 处理手册，按二级标题切分，
使用进程内的 all-MiniLM-L6-v2 模型生成 384 维向量，再写入 `SimpleVectorStore`。
索引保存在内存中，每次启动重建。对话模型和 embedding 模型分别配置，Gemma 不负责知识库向量化。

服务说明与处理手册分开维护：前者解释某个服务的字段、单位和基线，按服务名查询；
后者提供一类问题的排查方法，按问题检索。例如，连接池上限是 50 属于服务说明，
如何区分慢查询和连接泄漏属于处理手册。

本地向量化减少了额外服务依赖。更换 embedding 模型时，需要重新生成向量，并确认存储维度兼容。

## 结果与流式返回

普通接口等待调查结束后返回完整 JSON。流式接口通过 SSE 依次发送知识片段、
每次完成的工具调用，以及最终结果或错误。流式调查在虚拟线程中执行，超时设置为五分钟。
详见 [API 文档](api.md)。

工具执行时会把参数、结果和耗时写入当前调查的 `ToolCallLog`，通过 `ScopedValue` 隔离不同调查。
这些记录随结果返回，提供可核对的原始材料。当前不落库，也没有历史调查查询功能；
报告是否正确引用数据、是否遗漏反证，仍需人工或后续评估检查。

## 代码位置

| 目录 | 负责内容 |
|---|---|
| `src/main/java/dev/xiaohanglyu/incidentcopilot/investigation/` | 接口、调查编排、报告与响应模型 |
| `src/main/java/dev/xiaohanglyu/incidentcopilot/tools/` | 工具、样例文件读取、时间过滤、调用记录 |
| `src/main/java/dev/xiaohanglyu/incidentcopilot/knowledge/` | Markdown 索引、向量存储和检索 |
| `src/main/java/dev/xiaohanglyu/incidentcopilot/shared/` | 应用配置 |
| `src/main/resources/prompts/` | 系统提示词 |
| `src/main/resources/knowledge/` | 故障处理手册 |
| `src/main/resources/fixtures/` | 各服务的说明与样例数据 |
| `web/src/` | React、TypeScript 前端和 SSE 客户端 |
| `web/e2e/` | Playwright 浏览器测试 |

Maven 使用 Java 25、Spring Boot 4.1 和 Spring AI 2.0 构建后端，并将 Vite 构建的前端写入
`target/classes/static/`，打包后一并由 Spring Boot 提供服务。
工具和知识服务不依赖调查编排层；后续接入真实数据源时，需要实现客户端、配置和查询映射，
尽量保持面向模型的工具接口稳定。

构建和运行方式见[开发文档](development.md)，后续工作见[路线图](roadmap.md)。
