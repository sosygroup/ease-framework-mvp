# EASE MVP

This repository contains an executable Minimal Viable Product of the
**Ethics-aware Adjustable Autonomy Framework (EASE)** described in the companion
research paper, *Ethics-aware Adjustable Autonomous Systems*.

The MVP instantiates the Charlie/Home Hub running example and implements the paper's end-to-end runtime cycle: monitored evidence, contextual ethical constraints, mismatch indicators, governance thresholds, BDI-style candidate intentions, least-intrusive selection, execution, confirmation, explanation, and contestation.

It is deliberately dependency-free at runtime. An optional, governed LLM
connector can continuously enrich runtime Knowledge and BDI state through an
OpenAI Responses API or OpenAI-compatible Chat Completions endpoint.

## Requirements

- JDK 21, including `java` and `javac`;
- a POSIX-compatible shell for the supplied scripts;
- a modern browser for the dashboard.

No Maven, Gradle, Node.js package installation, database or external service is
required for the deterministic MVP. The optional LLM path requires a compatible
HTTPS endpoint and credentials supplied at runtime.

## Installation

```bash
git clone https://github.com/sosygroup/ease-framework-mvp.git
cd ease-framework-mvp
./scripts/test.sh
```

The scripts compile sources into the ignored `build/` directory. They do not
install system-wide software or download dependencies.

## Quick start

```bash
./scripts/run.sh
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080), then select **Run worked example**.

The interface exposes:

- the four-mode autonomy ladder;
- editable Home Hub evidence and confidence;
- per-constraint values `d`, `s`, `m`, hard violations, aggregate `M(t)` and `Q(t)`;
- the applicable threshold region and policy version;
- all generated intentions, including rejected alternatives and their reasons;
- the selected intention and least-intrusive rationale;
- the human-confirmation gate;
- historical evidence correction with recomputation and rollback;
- prospective consent changes that do not rewrite the source event;
- an append-only decision/revision trace;
- a central, validated JSON deployment configuration editor;
- automatic multi-scenario execution with progress, comparison, filtering and sorting;
- aggregate JSON and CSV downloads;
- an in-memory LLM connector configuration (API endpoint/handle, protocol, model, API key, authentication header, explicit evidence-disclosure consent, and timeout);
- per-cycle structured LLM updates to beliefs, desires, plan-outcome assessments, Knowledge notes, and uncertainty;
- explicit LLM failure fallback and provider request/usage metadata without exposing the API key.

Use another port if needed:

```bash
./scripts/run.sh --port=8765
```

## Reproduce the worked trace

The paper scenario starts with:

- 3 discarded items out of 12 perishables;
- 4 ignored or rejected suggestions out of 5;
- sustainability evidence confidence `0.90`;
- no external disclosure;
- no standing consent for automatic list changes;
- current mode `ADVISORY`.

The executable result is:

| Artefact | Result |
|---|---:|
| `m_sustainability(t)` | `0.620` |
| `m_autonomy(t)` | `0.000` |
| `H(t)` | `∅` |
| `M(t)` | `0.403` |
| `Q(t)` | `0.935` |
| Governance region | assistive may be considered |
| Selected intention | `I2` |
| Selected mode | `ASSISTIVE` |
| Execution | pending Charlie's confirmation |

The rejected alternatives match the analytical trace in the paper:

- `I1` is insufficient because its predicted residual mismatch is `0.47`;
- `I3` is inadmissible because it bypasses Charlie's confirmation preference;
- `I4` is outside the threshold-authorised governance region and unnecessarily intrusive.

The same scenario can be run without the web interface:

```bash
./scripts/demo.sh demo
```

The worked-example button always reloads the bundled reference configuration. Use
**Run active configuration** to evaluate the default evidence of a configuration
that you edited or loaded in the dashboard.

Additional CLI scenarios are available:

```bash
./scripts/demo.sh privacy
./scripts/demo.sh low-confidence
./scripts/demo.sh contest
```

## Configurable deployments

All thresholds, aggregation weights, evaluator constants, default evidence,
stakeholders, constraints, authorised plans, contestation permissions and
baseline BDI desires are defined in one validated model. The exact paper values
remain the default:

```text
src/main/resources/config/default-deployment.json
```

Load or edit a complete `ease-deployment/v1` document in the dashboard, or use
the same configuration from the CLI:

```bash
./scripts/demo.sh demo \
  --config=src/main/resources/config/examples/advisory-first.json
```

Compact `ease-deployment-overlay/v1` documents can extend a bundled
configuration and patch nested objects or individual list items by `id`. Two
examples are included:

- `advisory-first.json`: changes weights and the predicted value/cost/burden of
  `I1`, so the advisory plan becomes sufficient;
- `conservative-governance.json`: raises governance/confidence thresholds and
  lowers the input confidence, so the safe advisory state is retained.

Invalid or incomplete configurations are rejected before execution with
JSON-path-oriented messages. The dashboard can download the currently active
configuration as a complete JSON document. The full schema and validation rules
are documented in [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Contestation semantics

The contestation panel preserves the source evidence and records corrected
values separately. It supports:

- discarded perishables;
- sustainability confidence in `[0,1]`;
- external-disclosure consent;
- automatic-list-change consent;
- a required reason.

`HISTORICAL_FACT_CORRECTION` means that a value describing the source event was
wrong. The engine recomputes mismatch, confidence, governance and deliberation
from corrected evidence and links the revision trace to the source.

`PROSPECTIVE_CONSENT_CHANGE` changes one or both consents only from the new
cycle onward. It does not relabel a past disclosure as authorised or modify the
other historical facts. Confidence and discarded-item edits are therefore
rejected for this correction type.

## Automatic scenario execution

The dashboard can run the bundled comparison or any uploaded
`ease-scenario-batch/v1` document. Each scenario executes in an isolated engine;
one invalid scenario is recorded as `ERROR` without stopping the others.
Progress, errors and partial results are visible while the job runs.

The same implementation is available from a terminal:

```bash
./scripts/batch.sh
./scripts/batch.sh path/to/batch.json path/to/output-directory
```

The default command uses `src/main/resources/config/example-batch.json` and
writes timestamped files to `output/batch/`. JSON uses
`ease-scenario-batch-result/v1`; every row in the CSV uses
`ease-scenario-csv/v1`. Both contain configuration parameters, evidence before
and after correction, metrics, final plan/mode, status, errors and execution
time. Their stable fields are documented in
[docs/CONFIGURATION.md](docs/CONFIGURATION.md#result-and-export-schemas).

## Optional governed LLM updater

Open the dashboard and configure **Knowledge · BDI cognitive updater**. The default profile targets the OpenAI Responses API:

```text
Endpoint  https://api.openai.com/v1/responses
Protocol  RESPONSES
Model     gpt-5.6-luna
Auth      Authorization: Bearer <API key>
```

The endpoint, protocol, model, authentication header/scheme, API key, explicit evidence-disclosure consent, and timeout are editable. `CHAT_COMPLETIONS` can be selected for compatible providers. The key is retained only in the Java process memory: it is not returned by `/api/state`, included in a decision trace, logged, or written to disk. Embedded URL credentials and secret-like query parameters are rejected; authentication belongs in the dedicated key/header fields. The server is loopback-only; the dashboard should still be treated as an MVP rather than a production secret-management interface.

The same configuration can be supplied before startup:

```bash
export EASE_LLM_ENABLED=true
export EASE_LLM_ENDPOINT=https://api.openai.com/v1/responses
export EASE_LLM_PROTOCOL=RESPONSES
export EASE_LLM_MODEL=gpt-5.6-luna
export EASE_LLM_API_KEY=your-key
export EASE_LLM_DATA_DISCLOSURE_CONSENT=true
./scripts/run.sh
```

A complete non-secret template is provided in `.env.example`. The scripts do
not load `.env` automatically:

```bash
cp .env.example .env
# Edit .env and set EASE_LLM_API_KEY only in this ignored local file.
set -a
source .env
set +a
./scripts/run.sh
```

Every MAPE-K cycle then considers the updater once. A provider call occurs only while the connector is enabled, explicit evidence-disclosure consent is active, and `Cprivacy` is not violated. It receives current evidence, deterministic beliefs, mismatch indicators, governance state, constraints, thresholds, the finite authorised plan library, and the previous cognitive update. Its strict JSON output may propose:

- confidence-qualified, provenance-linked, contestable beliefs;
- ethically admissible desires;
- predicted residual mismatch and confidence for authorised templates `I1`-`I4`;
- Knowledge notes and explicit uncertainty.

The LLM is not an actuator or policy authority. It cannot create executable plan identifiers, alter hard constraints, change thresholds, grant consent, expand autonomy boundaries, or execute an intention. Its proposals enter the ordinary BDI deliberation and are independently filtered by confidence, hard constraints, consent, governance thresholds, authorised capabilities, and least-intrusive selection. Missing disclosure consent and hard privacy violations are recorded as skipped updates without a network call. Provider failures are recorded as `FAILED_LOCAL_FALLBACK`; the deterministic MVP completes the cycle.

## Test

```bash
./scripts/test.sh
```

The 20 executable tests cover:

1. exact reproduction of the paper's Home Hub mismatch and selection result;
2. explicit confirmation before assistive execution;
3. rejection of duplicate execution for the same confirmation trace;
4. append-only contestation and recomputation;
5. rollback to advisory mode after contesting an already executed shift;
6. lexicographic hard-constraint precedence for privacy;
7. low-confidence referral to human review;
8. inventory-only mismatch detection when suggestion history is absent;
9. validation of malformed evidence.
10. governed merging of structured LLM beliefs, desires, and plan assessments;
11. deterministic continuation after LLM failure and complete API-key redaction;
12. prevention of LLM evidence transmission without explicit disclosure consent;
13. prevention of LLM evidence transmission while `Cprivacy` is violated;
14. an HTTP-level Responses API contract test covering the configured endpoint, authentication header, strict JSON schema, non-persistent provider request, and response traceability;
15. complete and overlay configuration loading, JSON round-trip and validation;
16. configurable threshold, weight and plan outcomes;
17. historical multi-field contestation and recomputation;
18. prospective consent semantics and invalid-field rejection;
19. multi-scenario execution with per-scenario error isolation;
20. consistency between comparison data, JSON export and CSV export.

## Implementation structure

```text
src/main/java/org/ease/mvp/
├── app/                         HTTP server, scenario CLI and batch CLI
├── configuration/               validated deployment model and JSON codecs
├── runtime/EaseEngine.java      facade that wires the architecture
├── mapek/
│   ├── MapeKLoop.java           explicit outer governance loop
│   ├── monitor/                 evidence acquisition and belief revision input
│   ├── analyse/                 mismatch indicators and governance state
│   ├── plan/                    autonomy planning entry point
│   ├── execute/                 confirmation, enactment, and rollback
│   └── knowledge/               versioned state, traces, and contestation
├── bdi/
│   ├── belief/                  belief base and belief revision
│   ├── desire/                  admissible objectives and desire generation
│   ├── intention/               candidate intentions and Home Hub plan library
│   └── deliberation/            filtering and least-intrusive selection
├── llm/
│   ├── config/                  endpoint, protocol, model, authentication, secret-safe public view
│   ├── client/                  dependency-free Responses / Chat Completions HTTP connector
│   ├── model/                   cognitive assertions, plan assessments, update trace
│   ├── runtime/                 in-memory configuration and connection test
│   └── update/                  governed Knowledge and BDI update orchestration
├── domain/                      shared ethical and autonomy artefacts
├── homehub/                     domain configuration and constraint package
├── scenario/                    batch runner, results, jobs and CSV export
└── support/                     dependency-free JSON serialisation

src/main/resources/config/       default, examples and bundled batch definition
src/main/resources/web/          inspectable stakeholder-facing dashboard
src/test/java/org/ease/mvp/      dependency-free executable test suite
docs/                            architecture and feasibility boundary
scripts/                         compile, run, CLI demo, and test commands
```

The folder structure intentionally mirrors the paper: the outer MAPE-K loop calls an inner BDI deliberation during `Plan`. When enabled, the LLM updater enriches the shared Knowledge and BDI representations but remains inside an explicit deterministic governance boundary. All evidence mappings, policy parameters, authorised plans, filters, provider-derived proposals, and tie-break criteria remain visible in source and in the runtime trace.

## API

The UI uses a small form-encoded HTTP API:

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/health` | Runtime health |
| `GET` | `/api/state` | Knowledge, configuration, traces, and execution records |
| `POST` | `/api/cycle` | Run a cycle with supplied evidence |
| `POST` | `/api/scenarios/worked` | Load defaults and reproduce the worked trace |
| `POST` | `/api/scenarios/paper` | Run default evidence under the active configuration |
| `POST` | `/api/scenarios/privacy` | Trigger a hard privacy violation |
| `POST` | `/api/confirm` | Confirm the selected intention (`traceId`) |
| `POST` | `/api/contest` | Apply a historical correction or prospective consent change |
| `POST` | `/api/reset` | Reset the in-memory runtime |
| `GET`, `POST` | `/api/configuration` | Read or validate/apply a deployment configuration |
| `GET` | `/api/configuration/download` | Download the active complete JSON configuration |
| `GET` | `/api/configurations/examples` | List bundled configurations |
| `GET` | `/api/batch/example` | Download the bundled batch definition |
| `POST` | `/api/batch/start` | Start an asynchronous scenario batch |
| `GET` | `/api/batch/status?jobId=…` | Poll progress and partial/final results |
| `GET` | `/api/batch/export?jobId=…&format=json\|csv` | Download aggregate results |
| `POST` | `/api/llm/configure` | Configure endpoint, protocol, model, authentication, disclosure consent, and in-memory API key |
| `POST` | `/api/llm/test` | Verify authenticated strict structured output without changing Knowledge |
| `POST` | `/api/llm/clear-key` | Erase the API key from process memory |

## Evidence status

This MVP demonstrates that the EASE elements in the paper can be implemented as a complete, deterministic, inspectable control flow. It does **not** by itself validate the ethical proxies, thresholds, outcome predictions, usability, effectiveness, or scalability. Those boundaries are explicit in [docs/OPERATIONAL_LIMITATIONS.md](docs/OPERATIONAL_LIMITATIONS.md).

For the detailed mapping from the paper's operational specification to the code, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Citation

The initial software release is version `1.0.1`. Citation metadata for Marco
Autili, Amel Bennaceur, Patrizio Migliarini and Patrizio Pelliccione is available
in [CITATION.cff](CITATION.cff). The final bibliographic reference for the
companion paper will be added when its publication metadata is stable.

## License

This software is released under the
[Apache License 2.0](LICENSE). The companion paper and other scholarly
artefacts may be distributed under separate terms.

## Security and publication status

The server binds to loopback and is a research MVP, not a hardened multi-user
service. Never commit `.env`, API keys, provider responses containing sensitive
evidence, generated runtime output or private-key material. See
[docs/OPERATIONAL_LIMITATIONS.md](docs/OPERATIONAL_LIMITATIONS.md) for the full
boundary and [docs/PUBLICATION.md](docs/PUBLICATION.md) for publication
metadata decisions.
