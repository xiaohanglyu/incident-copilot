# API 参考

[English](../en/api.md) · **简体中文** · [项目介绍](../../README.zh-CN.md)

本地服务默认地址为 `http://localhost:8080`，根路径提供网页。
调查接口提供普通 JSON 和 SSE 两种返回方式，使用相同的请求体。

| 方法与路径 | 返回方式 | 用途 |
|---|---|---|
| `POST /api/investigate` | JSON | 等待调查完成，取得完整结果 |
| `POST /api/investigate/stream` | Server-Sent Events | 接收排查进度和最终结果，网页使用此接口 |

## 请求参数

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `query` | string | 是 | 故障现象或问题，不能为空或仅含空白 |
| `since` | string | 否 | 发现异常的起始时间，ISO 8601 格式，包含时区或 `Z` |
| `until` | string | 否 | 发现异常的结束时间，格式同上 |
| `services` | string[] | 否 | 提问者认为相关的服务名 |

时间和服务名作为上下文传给模型，不会直接限制可调用的工具。
模型调用指标、日志工具时可以指定查询时间窗，工具再按自身规则过滤，并将起点向前扩展。
具体规则见[架构](architecture.md)。不传这些可选字段也可以发起调查。

```bash
curl http://localhost:8080/api/investigate \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "checkout-service 响应时间突然变高",
    "since": "2026-08-24T14:25:00+08:00",
    "services": ["checkout-service"]
  }'
```

示例时间对应项目自带数据；样例不会随当前日期更新。

## JSON 响应

响应由三部分组成：

| 字段 | 来源与用途 |
|---|---|
| `report` | 模型生成的原因、证据、验证步骤和缓解建议 |
| `toolCalls` | 后端记录的工具调用，包含参数、返回结果和耗时 |
| `retrievedKnowledge` | 本次调查检索到的处理手册片段 |

下面是格式示例，文本和调用记录已简化，不是固定输出：

```json
{
  "report": {
    "mostLikelyCause": "发布后的查询变慢，导致数据库连接池耗尽。",
    "confidence": 0.8,
    "evidence": [
      {"source": "getMetrics", "detail": "连接池使用量达到 50/50，延迟升高。"},
      {"source": "getRecentChanges", "detail": "异常前发布的版本修改了数据库查询。"}
    ],
    "suggestedVerification": ["核对被修改查询的耗时和执行计划。"],
    "suggestedMitigation": ["确认影响后，考虑回滚相关版本。"]
  },
  "toolCalls": [
    {"tool": "getMetrics", "arguments": "service=checkout-service", "result": "连接池 50/50；p99 3.4s", "millis": 3},
    {"tool": "getRecentChanges", "arguments": "service=checkout-service", "result": "14:20 发布，修改数据库查询", "millis": 1}
  ],
  "retrievedKnowledge": [
    {"source": "connection-pool-exhaustion.md", "content": "连接池耗尽的排查方法……"}
  ]
}
```

`report.evidence` 的每一项包含 `source` 和 `detail`。模型被要求注明工具名，
或使用 `knowledge` 表示检索片段。`confidence` 是模型按提示词给出的 0–1 自评分，
不是经过校准的正确概率，服务端也尚未强制校验范围或逐条检查证据。

`toolCalls[].arguments` 是便于阅读的字符串，不是 JSON 参数对象；`millis` 为工具执行耗时，
不含完整的模型推理时间。真实响应会包含本次调查的全部调用和检索结果。

## SSE 响应

```bash
curl -N http://localhost:8080/api/investigate/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"query": "Kafka 消息为什么积压了？"}'
```

成功的调查按以下顺序发送事件，每个事件的 `data` 都是 JSON：

| 事件 | 数据 | 发送时机 |
|---|---|---|
| `knowledge` | `retrievedKnowledge` 对应的片段数组 | 检索完成后一次，允许为空数组 |
| `tool-call` | 一个 `ToolCall` 对象 | 每次工具调用结束后，可能有多条，也可能没有 |
| `result` | 与普通 JSON 接口相同的完整响应 | 调查完成后一次，随后关闭流 |
| `error` | `{"message":"错误信息"}` | 调查异常时替代最终结果；可能发生在任何阶段 |

客户端可用 `tool-call` 展示进度，收到 `result` 后以其中的完整记录展示最终结果。
这不是模型逐字输出的文本流，也不会在 `result` 后再执行建议操作。

## 错误与连接中断

空白 `query`、无法解析的 JSON 或时间格式会在请求处理阶段返回 HTTP 400。
普通接口的调查异常通过 HTTP 错误响应返回；SSE 开始后的调查异常尽可能以 `error` 事件返回，
客户端不能只依据 HTTP 状态判断调查成功，还需要收到 `result`。

SSE 超时配置为五分钟。客户端断开后，后端在后续发送事件失败时停止当前处理，
但没有独立的取消接口，也不保证立即中断已经发出的模型请求。重试会发起新调查，当前不保存历史结果。
