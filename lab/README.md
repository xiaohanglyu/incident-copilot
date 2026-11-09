# Fault lab

**English** · [简体中文](README.zh-CN.md)

A small payment service that really breaks, used to make evaluation data. The failures
actually run, so the log format, the error text and the order in which metrics move are
real rather than hand-written.

## What's here

- `demo-service/`: a small Spring Boot payment service (create, history, refund) on
  PostgreSQL.
- `docker-compose.yml`: runs the service, PostgreSQL, Prometheus and steady k6 traffic.
- `scenarios/`: scripts that "deploy" a few versions in order, one of them with a bug. The
  `.md` file of the same name is the answer key.
- `export/export.py`: turns one run's log, change record and Prometheus metrics into the
  main project's fixtures format.

## The three failures

| Scenario | What the buggy release does | What happens |
|---|---|---|
| `slow-query` | Lookup becomes `lower(customer_ref) = lower(?)`, so the index no longer applies | Every lookup scans the table; the connection pool fills |
| `connection-leak` | Refunds take their own connection and don't close it when a refund is rejected | Connections leak one by one until the pool is empty |
| `unbounded-cache` | Every payment response is cached by idempotency key and never removed | Heap grows, GC runs more and more, then OutOfMemoryError |

Each scenario also has a harmless deploy close to the incident, as a decoy. In
`unbounded-cache` the decoy comes after the cause, to test whether the model just blames
the latest deploy.

## Running one

You need Docker, JDK 25, Maven and Python 3.

```bash
cd lab
./scenarios/slow-query.sh
```

A run takes 45–50 minutes: 15 minutes of normal traffic, the decoy deploy, then the buggy
one. To check the pipeline works, shorten every wait:

```bash
HOLD_SCALE=0.2 ./scenarios/slow-query.sh
```

The first start loads 3M rows into the database; allow a minute or two.

Results land in `out/<scenario>-<time>/`:

```text
app.log            the service's whole log, every restart included
changes.tsv        one line per deploy
expected.md        the answer key for this scenario
payment-service/   exported fixtures: metrics.txt, logs.txt, changes.txt, profile.md
```

Copy `payment-service/` into `src/main/resources/fixtures/` and the main project can
investigate it. Only one scenario fits at a time, because the directory name is the
service name.

`out/` is ignored by Git. Copy data you want to keep into fixtures and commit it there.

To stop:

```bash
docker compose down        # keeps the database, so the next start is quick
docker compose down -v     # deletes the data too
```

## How a deploy works

Each bug has a switch, e.g. `LAB_FAULTS=slow-query`. Deploying a version recreates the
container with a new version string and switches:

```bash
LAB_VERSION=v1.4.0 LAB_FAULTS=slow-query docker compose up -d payment-service
```

So every deploy really restarts the service. The restart leaves a gap in the metrics and a
`payment-service v1.4.0 ready` line in the log, as a real rollout does.

The switch names never appear in the log. A real service doesn't log "I have a bug".

## Adding a failure

1. Add a value to `LabProperties.Fault` and write the bug. Make it read like a real PR:
   well meant, passes tests, only fails under production traffic.
2. Copy a scenario script and change the deploys and PR descriptions. Descriptions should
   read like real commit messages, not state the consequence.
3. Write the answer key with the same name: the cause, the evidence a report should cite,
   explanations the data rules out, and the usual wrong conclusions.
4. Run it, then read the exported data yourself and check the cause can be found without
   the answer key.
