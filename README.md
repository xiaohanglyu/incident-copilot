# incident-copilot

**English** · [简体中文](README.zh-CN.md)

incident-copilot is an AI incident investigation assistant that helps on-call engineers
diagnose unfamiliar systems faster, with the goal of reducing mean time to recovery
(MTTR). It queries metrics, logs, and recent changes, consults runbooks, and returns
likely causes with evidence and suggested next steps. Engineers decide what to do next.

## Why this project

With many services and teams sharing on-call duties, engineers may be paged for systems
they don't know well. Before diagnosing the incident, they need to understand the service,
find its dashboards and logs, check recent changes, and consult experienced colleagues.
Meanwhile, the business remains affected.

This project brings scattered data and operational knowledge into the investigation to:

- **Shorten diagnosis time**: reduce time spent finding and comparing information across systems, helping engineers identify a supported cause and restore service sooner.
- **Reduce dependence on a few experts**: give engineers service context and investigation guidance without always needing a senior colleague's help.
- **Reuse operational knowledge**: make existing runbooks available during investigations, with resolved incidents feeding back into the knowledge base in the future.

Enterprise use also requires controls over production changes, auditable investigation
records, and protection of sensitive data. These needs inform the project's design:
read-only tools, human review, recorded investigations, and support for local models.

The initial focus is on deployment and configuration issues, and resource exhaustion in
backend services, such as slow APIs, exhausted connection pools, and Kafka consumer lag.
Infrastructure diagnosis, automatic remediation, and formal postmortem generation
are outside the current scope.

## Investigation flow

```text
Describe the symptom
    ↓
Look up services and identify where to investigate
    ↓
Gather metrics, logs, and recent changes; retrieve relevant runbooks
    ↓
Analyze the timeline and failure signals for likely causes
    ↓
Return causes, evidence, verification steps, and mitigation suggestions
    ↓
An engineer reviews the findings and decides what to do
```

For example, ask “Why are Kafka messages backing up?” In the bundled sample scenario,
the investigation can find:

- **Metrics**: consumer lag grows, memory usage rises, and pods restart repeatedly.
- **Logs**: out-of-memory errors and consumer group rebalances.
- **Deployment history**: the new version introduced an in-memory cache with no eviction.

These clues point to a failure chain: growing cache → memory exhaustion → consumer
restarts → message backlog. The report uses that evidence to explain the likely cause
and suggest verification steps and mitigations such as a rollback. The web UI displays
tool results during the investigation so engineers can check the basis for its findings.

## Design constraints and current status

- **Read-only tools**: tools retrieve data; they do not roll back deployments, restart services, or make other production changes. Engineers review and carry out any suggested actions.
- **Human review**: an explicit approval workflow for recommendations is planned but not implemented.
- **Investigation records**: the backend records tool calls and results so engineers can check the evidence. Persistent audit records are not yet implemented.
- **Data protection**: the local setup uses Gemma through an OpenAI-compatible endpoint. Data masking is not implemented. Whether data leaves your environment depends on model deployment and endpoint configuration.

The demo currently uses two bundled scenarios: connection pool exhaustion and Kafka
consumer lag. It does not yet connect to live monitoring systems, and improvements in
diagnosis time or MTTR have not been measured. Approval, audit, and data protection
features remain development goals, rather than a claim of enterprise compliance.
See the [roadmap](docs/en/roadmap.md) for planned work.

## Quick start

You need JDK 25, Maven, and a model server that supports tool calling. The local
configuration uses Gemma (`gemma-4-26b-q4_k_m.gguf`).

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Before running, configure the model endpoint, model name, and credentials in
`src/main/resources/application-local.yml`. This machine-specific file is gitignored;
a fresh clone needs its own copy. See [configuration instructions](docs/en/development.md).

Open [localhost:8080](http://localhost:8080) and try “Why are Kafka messages backing up?”

Maven installs Node and builds the frontend automatically. The first run also downloads
the embedding model. Diagnostic data comes from `src/main/resources/fixtures/`, so you
don't need a database or monitoring service. Model requests go to the configured chat API.

The model server uses the OpenAI-compatible protocol; it does not require an OpenAI
model. When running without the `local` profile, configure the service with
`OPENAI_BASE_URL`, `INCIDENT_COPILOT_MODEL`, and `OPENAI_API_KEY`. See
[development and configuration](docs/en/development.md) for details and restricted-network setup.

## Documentation

| What you want to do | Document |
|---|---|
| Configure a model, run the project, develop, and test | [Development and configuration](docs/en/development.md) |
| Understand request flow, data sources, and design choices | [Architecture and design](docs/en/architecture.md) |
| Look up request fields, response formats, and stream events | [API reference](docs/en/api.md) |
| Add runbooks and sample data | [Extension guide](docs/en/extending.md) |
| Check completed features and planned work | [Roadmap](docs/en/roadmap.md) |

## License

[MIT](LICENSE)
