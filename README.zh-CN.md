# incident-copilot

[English](README.md) · **简体中文**

基于 Spring AI 的故障调查助手。用一句话描述线上现象，Agent 会自行调用只读诊断工具、
检索本地知识库，输出一份结构化的判断供人审阅。

> v1 是一个自包含的 demo。诊断工具从 `resources/fixtures` 读取固定的样例数据，
> 因此只需一个 API key 即可运行——不依赖数据库，也不依赖任何外部服务。

## 它做什么

Agent 不会直接凭 prompt 回答，而是先去调查：

```
User: "kafka 消息堆积怎么回事"
  │
  ├─ listServices()       checkout-service, order-consumer
  ├─ getMetrics()         堆积 180 → 120 万 · 堆内存 48% → 95% · CPU 反而下降
  ├─ searchLogs()         OutOfMemoryError · 消费组反复 rebalance
  ├─ getRecentChanges()   v4.2.0 引入了一个不淘汰的内存 Map
  └─ 知识库                runbook：Kafka 消费积压
  │
  ▼
报告  ─  最可能的原因 · 证据 · 验证建议 · 缓解建议
  │
  ▼
人工审阅
```

提问没有指名服务，于是 Agent 先问自己能看哪些服务，挑出与现象匹配的那个，再去调查它。
所有工具都是只读的，报告只是建议，不会自行执行任何操作。

## 示例

打开 `http://localhost:8080` 有一个简单的页面，中英双语，报告下方可以展开完整的调查过程。
也可以直接调接口：

```bash
curl -X POST localhost:8080/api/investigate \
  -H 'Content-Type: application/json' \
  -d '{
        "query": "checkout-service 响应时间突然变高",
        "since": "2026-08-24T14:25:00+08:00",
        "services": ["checkout-service"]
      }'
```

只有 `query` 是必填的。`since` / `until` 和 `services` 本来就写在工单上——它们作为线索传入，
不作为过滤条件。不填的话，Agent 会自己判断该查哪个服务、该拉多宽的时间窗。

```json
{
  "report": {
    "mostLikelyCause": "连接池耗尽，起因是 v2025.8.19 在 OrderRepository.findRecentByCustomer
                        中加了一个 join 并去掉了 LIMIT。",
    "confidence": 0.95,
    "evidence": [
      { "source": "getMetrics",       "detail": "14:25 起 p99 从 118ms 升到 3.4s，连接池打满 50/50，CPU 与内存平稳" },
      { "source": "searchLogs",       "detail": "14:26:03 起出现 HikariPool 连接超时" },
      { "source": "getRecentChanges", "detail": "14:20 的发布改动了 OrderRepository.findRecentByCustomer" },
      { "source": "knowledge",        "detail": "Runbook：延迟上升而 CPU 平稳、连接池打满，即连接池耗尽" }
    ],
    "suggestedVerification": ["检查被改动 SQL 的查询耗时"],
    "suggestedMitigation": ["将 checkout-service 回滚到 v2025.8.18"]
  },
  "toolCalls": [
    { "tool": "describeService", "arguments": "service=checkout-service", "millis": 1, "result": "…" },
    { "tool": "getMetrics", "arguments": "service=checkout-service, from=2026-08-24T14:00:00+08:00, to=2026-08-24T15:00:00+08:00", "millis": 3, "result": "…" },
    { "tool": "searchLogs", "arguments": "service=checkout-service, keyword=Connection is not available, from=…", "millis": 1, "result": "…" },
    { "tool": "getRecentChanges", "arguments": "service=checkout-service", "millis": 1, "result": "…" }
  ],
  "retrievedKnowledge": [
    { "source": "connection-pool-exhaustion.md", "content": "…" }
  ]
}
```

`report` 是模型的结论。`toolCalls` 和 `retrievedKnowledge` 是实际发生的事，由服务端在工具
执行时记录——因此读的人可以拿结论去对数据，而不必相信模型对自己行为的复述。

注意前两次调用。Agent 先查清楚 checkout-service 的字段含义再去读数据，然后拉了一个从 14:00
开始的窗口——比给它的 14:25 更早，因为故障的原因总在症状之前。

## 工作原理

一次请求，三种机制：

| | |
|---|---|
| **工具调用** | 由模型决定调用哪些工具、传什么参数、以什么顺序调用。工具是标注了 `@Tool` 的普通 Spring Bean。它会先查清某个服务的字段含义再读数据，并在调查过程中自行收窄或放宽时间窗。 |
| **知识检索** | Runbook 在启动时写入向量库，检索由代码显式发起而非交给 advisor，命中的文档因此留在手里，可以在报告中引用。 |
| **结构化输出** | 报告绑定到 Java record，多次运行的响应结构保持一致。 |

有四个行为比看上去重要。

**问到没有数据的服务时，工具会明说没有、并列出它有哪些**，而不是返回另一个服务的数据——
否则模型会学到"返回的数据一定相关"，然后把它硬掰成问题的答案。

**请求的时间窗会先向前扩再过滤。** 用户报告的时间是人注意到症状的时刻，而原因在更早。
把 checkout 那次故障报成 14:25 开始，严格按字面取数就会把 14:20 那次发布挡在窗口外。

**字段语义靠工具查，不靠推断。** 日志格式模型自己就能读——JSON 和 Logback 文本在训练数据里
见过无数次。它猜不到的是"时间戳没有时区、按 Asia/Shanghai 读"，或者"`heap_used%` 是相对
Pod 内存上限而非 `GOMEMLIMIT`"。格式读对、单位读错，是一种没有任何征兆的失败，所以
`describeService` 是一次确定的工具调用，而不是一种指望。

**调查步骤由工具在执行时记录，而不是事后由模型复述**——自报的过程和模型其余输出同源，
拿它去核对模型自己，等于没核对。

## 技术栈

| | |
|---|---|
| Java 25 · Spring Boot 4.1 | 领域模型使用 record，工具并发调用可用虚拟线程 |
| Spring AI 2.0 | 工具调用、结构化输出绑定、向量库抽象 |
| OpenAI 兼容对话接口 | 任意网关均可，通过 URL 与 key 配置 |
| all-MiniLM-L6-v2 / ONNX Runtime | 进程内计算 embedding，384 维 |
| SimpleVectorStore | 内存索引，启动时重建 |
| Maven | 构建与依赖管理 |

## 架构

```
dev.xiaohanglyu.incidentcopilot
├── investigation    编排与领域模型 —— Report、Evidence、InvestigationResult
├── tools            适配层，一个数据源一个类，外加调用日志
├── knowledge        向量化、索引、检索
└── shared           配置
```

```
resources
├── prompts/         system prompt，改它不用碰 Java
├── knowledge/       runbook —— 某种现象组合意味着什么，跨服务通用（RAG）
├── fixtures/        每个服务一个目录：样例数据，外加 profile.md ——
│                    本服务的字段含义（按名字查表，不走检索）
└── static/          前端页面
```

依赖单向流动：`investigation → { tools, knowledge } → shared`。适配层不反向依赖编排层，
因此接入新数据源是新增文件，而不是修改已有文件。

## 快速开始

需要 JDK 25、Maven，以及任意 OpenAI 兼容接口的 API key。

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

首次启动会从 GitHub 下载 embedding 模型（约 90 MB）并缓存到本地，之后启动不再等待。

### 受限网络

如果访问不了 `raw.githubusercontent.com`（代理、防火墙或 TLS 劫持），启动会在加载
embedding 模型时失败。改为从 Hugging Face 预先下载这两个文件：

```bash
mkdir -p models
curl -Lo models/tokenizer.json \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json
curl -Lo models/model.onnx \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx
```

国内网络可把域名换成 `hf-mirror.com`。然后让应用指向本地文件：

```yaml
spring:
  ai:
    embedding:
      transformer:
        onnx:
          model-uri: file:./models/model.onnx
        tokenizer:
          uri: file:./models/tokenizer.json
```

`models/` 已在 gitignore 中。这样配置后启动完全不需要网络，唯一的对外流量就是对话 API。

## 配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `OPENAI_API_KEY` | — | 必填。 |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | 可指向任意兼容网关。 |
| `INCIDENT_COPILOT_MODEL` | `gpt-4o-mini` | 对话模型，需支持工具调用。 |
| `INCIDENT_COPILOT_TEMPERATURE` | `0.2` | 取值越低，报告越稳定可复现。 |

Embedding 在进程内计算 —— 不需要 key，不走网络，也不需要单独部署服务。

应用层的配置在 `application.yml` 的 `incident-copilot` 下：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `system-prompt` | `classpath:/prompts/investigation-system.md` | 调查员的指令。改文件、重启即可，不用重新编译 Java。 |
| `knowledge-location` | `classpath:/knowledge/*.md` | 启动时索引的 Markdown。 |
| `top-k` | `5` | 每次调查检索的知识片段数。 |
| `window-lookback` | `1h` | 请求的时间窗向前扩多少。 |
| `default-zone` | `Asia/Shanghai` | 不带时区的时间戳按哪个时区解释。 |

机器相关的配置放 `src/main/resources/application-local.yml`，该文件已 gitignore。
用 `mvn spring-boot:run -Dspring-boot.run.profiles=local` 启动即可生效。

## 接入你自己的知识

把 Markdown 文件放进 `src/main/resources/knowledge/`，启动时会按二级标题切分、向量化并建立
索引。向量库是内存实现，每次启动重建索引，因此该目录就是唯一的数据来源。

## 添加你自己的场景

工具能看到的每个服务，对应 `src/main/resources/fixtures/` 下的一个目录，内含
`metrics.txt`、`logs.txt` 和 `changes.txt`。项目自带两个场景：`checkout-service`
（连接池耗尽）和 `order-consumer`（无界缓存导致的 Kafka 积压）。加第三个只需三个文本文件，
不用写代码。

同一场景的三个文件必须互相咬合，原因和做法见
[fixtures/README.md](src/main/resources/fixtures/README.md)。

## 设计说明

**Embedding 放在进程内。** 远程对话 API 搭配本地 embedding 模型，使得部署成本只有一个
环境变量。模型输出 384 维向量；后续更换会导致整个索引重建，因此这个选择需要尽早固定。

**检索由代码显式发起，而非交给 advisor。** 直接调用知识服务比挂一个检索 advisor 多写几行，
但换来的是命中文档留在手里 —— 这既是报告能够引用来源的前提，也是将来 Agent 多轮调查时
按假设重新检索的前提。

**一个数据源一个类。** 三个返回固定数据的工具今天完全塞得进一个类，但每个真实数据源都会
带来各自的客户端、凭证与查询语言。

**Runbook 靠检索，服务画像靠查表。** "某种现象组合意味着什么"是跨服务通用的，所以放知识库、
按相似度检索；"这个服务的字段是什么单位"不通用，而且提问描述的是症状、按症状检索也捞不到它，
所以做成按服务名查表的工具。两者内容重叠不是无害的：画像作为工具返回值出现，权重压过被拼进
提示词的检索结果，报告会悄悄不再引用 runbook——这个回退在开发过程中真实发生过一次。

**调查过程是记录出来的，不是复述出来的。** 工具在执行时写入一个请求作用域的日志。另一种
做法是让模型自己列出走过的步骤——那会得到一个看起来像证据、实际由它自己生成的字段，而它
本该被用来核对的正是这个模型。

**不做持久化。** 调查结果不落库。存储是可增量添加的能力，此处刻意推后。

## 许可证

MIT
