# 故障实验室

[English](README.md) · **简体中文**

这里有一个真的会出故障的支付服务，用来造评估数据。故障是真跑出来的，所以日志格式、报错原文、指标之间的先后关系都是真实的，不是手写的。

## 里面有什么

- `demo-service/`：一个小的 Spring Boot 支付服务，提供下单、查历史、退款三个接口，连 PostgreSQL。
- `docker-compose.yml`：把服务、PostgreSQL、Prometheus 和压测流量（k6）一起跑起来。
- `scenarios/`：故障剧本。每个剧本按顺序「发布」几个版本，其中一个版本带 bug。同目录的 `.md` 文件是标准答案。
- `export/export.py`：把一次运行的日志、发布记录和 Prometheus 指标，转成主项目 fixtures 的格式。

## 三个故障

| 剧本 | 带 bug 的版本做了什么 | 结果 |
|---|---|---|
| `slow-query` | 查询改成 `lower(customer_ref) = lower(?)`，索引用不上了 | 每次查询扫全表，连接池被占满 |
| `connection-leak` | 退款时自己拿连接，拒绝退款时忘了关 | 连接一个个漏掉，连接池用光 |
| `unbounded-cache` | 按幂等键缓存每笔支付的响应，从不清理 | 堆内存一直涨，GC 越来越频繁，最后内存溢出 |

每个剧本里都有一次「无害发布」作干扰：时间离故障很近，但什么都没坏。`unbounded-cache` 的干扰发布在故障之后，用来测模型会不会直接怪罪最近一次发布。

## 怎么跑

需要 Docker、JDK 25、Maven 和 Python 3。

```bash
cd lab
./scenarios/slow-query.sh
```

一个剧本大约跑 45 到 50 分钟：先跑 15 分钟正常流量，再发布干扰版本，最后发布带 bug 的版本。只想确认流程能跑通，可以把时间缩短：

```bash
HOLD_SCALE=0.2 ./scenarios/slow-query.sh
```

第一次启动会往数据库里灌 300 万行数据，要多等一两分钟。

跑完后，结果在 `out/<剧本名>-<时间>/`：

```text
app.log            服务的完整日志，包括每次重启
changes.tsv        每次发布的记录
expected.md        这个剧本的标准答案
payment-service/   导出的 fixtures：metrics.txt、logs.txt、changes.txt、profile.md
```

把 `payment-service/` 复制到 `src/main/resources/fixtures/` 下，主项目就能查它了。一次只能放一个剧本的结果，因为目录名就是服务名。

`out/` 被 Git 忽略。想留下来的数据，复制到 fixtures 再提交。

用完关掉：

```bash
docker compose down        # 保留数据库，下次启动快
docker compose down -v     # 连数据一起删
```

## 「发布」是怎么做的

每个 bug 对应一个开关，比如 `LAB_FAULTS=slow-query`。发布一个版本，就是带着新的版本号和开关重建一次容器：

```bash
LAB_VERSION=v1.4.0 LAB_FAULTS=slow-query docker compose up -d payment-service
```

所以每次发布都会真的重启服务。重启在指标上会留下一个缺口，日志里会多一行 `payment-service v1.4.0 ready`，和真实发布一样。

日志里不会出现开关的名字。真实系统的日志不会写「我有个 bug」。

## 加一个新故障

1. 在 `LabProperties.Fault` 里加一个值，在代码里写出这个 bug。写法要像一个真实的 PR：本意是好的，测试能过，只在线上流量下才出问题。
2. 在 `scenarios/` 下复制一个剧本脚本，改发布顺序和 PR 描述。PR 描述要像真的提交信息，不能直接说出后果。
3. 写同名的 `.md` 标准答案：原因、报告该引用的证据、能被数据排除的解释、常见的错误结论。
4. 跑一遍，再自己看一遍导出的数据，确认不看答案也能查出原因。
