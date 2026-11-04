# 开发与配置

[English](../en/development.md) · **简体中文** · [项目介绍](../../README.zh-CN.md)

下面从本地 Gemma 模型开始运行项目，再介绍前端开发、测试和其他模型的配置方式。
所有命令默认在仓库根目录执行，另有说明除外。

## 使用本地模型运行

准备 JDK 25、Maven，以及已经启动、支持工具调用的模型服务。
本机配置使用 `gemma-4-26b-q4_k_m.gguf`，通过 OpenAI 兼容接口访问。
模型服务需另行启动，项目不会自动下载或启动 Gemma。

创建 `src/main/resources/application-local.yml`，或修改已有文件：

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8081
      api-key: not-needed
      chat:
        options:
          model: gemma-4-26b-q4_k_m.gguf
```

将地址和模型标识替换为模型服务实际提供的值。`8081` 只是示例端口，应用本身使用 `8080`。
如果服务要求认证，请替换 `not-needed`。配置字段名中的 `openai` 表示兼容协议，不要求使用 OpenAI 模型。
该本地配置文件已被 Git 忽略，首次克隆需要自行创建。

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

打开 [localhost:8080](http://localhost:8080)，输入「Kafka 消息为什么积压了？」即可试用。
排查工具读取自带样例，不需要连接数据库、Kafka 或监控系统。

Maven 会把 Node 安装到 `target/`，安装前端依赖并构建页面，无需为这一步单独安装 Node。
知识库使用独立的 embedding 模型，首次加载可能需要联网下载；无法下载时见下文的本地文件配置。

## 开发前端

先按上面的命令启动后端。在另一个终端执行以下命令；直接运行前端脚本需要本机有 Node 22 和 npm。

```bash
cd web
npm ci
npm run dev
```

开发页面默认在 `http://localhost:5173`，`/api` 请求会代理到后端的 `8080` 端口。
只改后端、且已有前端构建产物时，可以跳过 Maven 中的前端步骤：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dskip.installnodenpm -Dskip.npm
```

## 测试与打包

后端测试：

```bash
mvn test -Dskip.installnodenpm -Dskip.npm
```

前端检查和测试，在 `web/` 中执行：

```bash
npm run typecheck
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
```

Playwright 会构建前端并启动预览服务，测试中的 API 响应由预置数据模拟，不验证真实模型的诊断能力。
后端测试也不构成故障诊断准确率评估。

打包并运行：

```bash
mvn package
java -jar target/incident-copilot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

修改资源文件后需要重新运行 Maven；直接运行已有 jar 不会读取源码目录里的新内容。
`application-local.yml` 放在资源目录中时也可能进入本机构建的 jar，分享构建产物前应检查其中的配置。

## 配置其他模型

未启用 `local` 配置时，`application.yml` 使用下面的环境变量。
这些是基础配置中的回退值，与前面的本地 Gemma 配置不同；启用 `local` 时，本地文件中的同名配置会覆盖它们。

| 环境变量 | 基础配置的回退值 | 用途 |
|---|---|---|
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | 模型服务地址，按实际兼容服务填写 |
| `OPENAI_API_KEY` | 空 | 认证信息；无需认证的兼容服务可使用占位值 |
| `INCIDENT_COPILOT_MODEL` | `gpt-4o-mini` | 服务提供的模型标识，需支持工具调用 |
| `INCIDENT_COPILOT_TEMPERATURE` | `0.2` | 生成温度；降低温度不保证输出完全一致 |

例如，不启用本地配置，直接指定一个本机模型服务：

```bash
export OPENAI_BASE_URL=http://localhost:8081
export OPENAI_API_KEY=not-needed
export INCIDENT_COPILOT_MODEL=gemma-4-26b-q4_k_m.gguf
mvn spring-boot:run
```

## 应用配置

以下配置位于 `application.yml` 的 `incident-copilot` 节点下：

| 配置项 | 默认值 | 用途 |
|---|---|---|
| `system-prompt` | `classpath:/prompts/investigation-system.md` | 模型调查指令 |
| `knowledge-location` | `classpath:/knowledge/*.md` | 启动时读取的处理手册 |
| `top-k` | `5` | 每次调查最多检索的知识片段数 |
| `window-lookback` | `1h` | 指标、日志查询起点向前扩展的时长 |
| `default-zone` | `Asia/Shanghai` | 样例中不带时区的时间戳所使用的时区 |

## 无法下载 embedding 模型时

对话模型与 embedding 模型分别加载。即使 Gemma 已能正常访问，embedding 文件无法下载仍可能导致启动失败。
可以在网络可用时预先下载：

```bash
mkdir -p models
curl -fL -o models/tokenizer.json \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json
curl -fL -o models/model.onnx \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx
```

把下面的配置合并到 `application-local.yml` 已有的 `spring.ai` 节点下，不要再创建重复的 `spring` 节点：

```yaml
embedding:
  transformer:
    onnx:
      model-uri: file:./models/model.onnx
    tokenizer:
      uri: file:./models/tokenizer.json
```

`models/` 已被 Git 忽略。以上相对路径要求从仓库根目录启动应用。
文件准备好后，向量化不再依赖远程 embedding 服务；构建依赖下载和对话模型连接仍取决于各自的配置与缓存。
