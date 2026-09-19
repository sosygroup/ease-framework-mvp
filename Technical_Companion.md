# EASE MVP technical companion

Companion article: **Ethics-aware Adjustable Autonomous Systems**  
Authors: **Marco Autili, Amel Bennaceur, Patrizio Migliarini and Patrizio Pelliccione**  
Journal: *Philosophical Transactions of the Royal Society A*  
Manuscript: RSTA-2025-0219, second revision  
Document version: 15 September 2026

Electronic supplementary material **ESM2**. 

This companion contains the implementation and verification details extracted from the article: source locations, configuration rules, execution semantics, export formats and reproduction instructions. The framework, decision rules, worked example and autonomy-selection results remain in the article.

All source paths refer to version **1.0.1**, commit [`85b5521b14e7e86d29379efadd39a7d79b4627bc`](https://github.com/sosygroup/ease-framework-mvp/tree/85b5521b14e7e86d29379efadd39a7d79b4627bc) of the [EASE MVP repository](https://github.com/sosygroup/ease-framework-mvp). Electronic supplementary material **ESM1** contains the unchanged source snapshot, existing input configurations, two sets of raw batch outputs and reproduction instructions.

## 1. Executable components and state

The application uses Java 21 without third-party runtime dependencies. `EaseServer` serves a browser interface through the JDK HTTP server; `EaseCli` and `BatchCli` expose command-line entry points. The browser interface, command-line applications and scenario runner use the same configuration and decision services.

| Responsibility | Source location beneath `src/main/java/org/ease/mvp/` | Recorded or returned artefacts |
|---|---|---|
| Configuration | `configuration/` | Materialised deployment configuration and validation errors |
| Monitor | `mapek/monitor/`, `bdi/belief/` | Supplied evidence and deterministic belief statements |
| Analyse | `mapek/analyse/` | Per-constraint indicators, aggregate mismatch and confidence, hard violations and governance region |
| Plan | `mapek/plan/`, `bdi/desire/`, `bdi/intention/`, `bdi/deliberation/` | Desires, candidate assessments, rejection reasons, selected intention and execution status |
| Execute | `mapek/execute/` | Immediate or confirmed execution records and resulting effective mode |
| Knowledge | `mapek/knowledge/` | Current evidence and mode, version number, decision traces, execution records and cognitive updates |
| Optional cognitive update | `llm/` | Accepted proposals or a recorded disabled, skipped or failed update |
| Scenario execution | `scenario/` | Scenario results, parameter differences, batch results and CSV exports |

`MapeKLoop.run` reads the current effective mode, monitors evidence, performs numerical analysis, requests the optional cognitive update and then invokes Plan. It appends a decision trace before applying any immediately executable intention. The trace records the evidence, constraints, beliefs, desires, indicators, threshold policy, candidates, selection rationale, uncertainty and contestation links.

`RuntimeKnowledgeBase` stores state in memory. It retains original traces and appends revisions and execution records during a session. Reset clears these lists and restores the initial evidence and advisory mode. Versions and revision links record the session history; durable storage and tamper detection are not implemented.

The MVP uses supplied evidence to represent sensing and records execution as a change of effective mode. Its confirmation endpoint can execute a pending selected intention. The server also exposes contestation, configuration, batch and reset operations. It has no general human-override handler or connection to physical Home Hub actuators.

## 2. Deployment configuration and validation

The complete reference input is `src/main/resources/config/default-deployment.json`, with format identifier `ease-deployment/v1`. The loader decodes the configuration into Java records, whose constructors validate it before use. The identifier names the document format rather than an externally enforced JSON Schema.

| Configuration object | Fields and use | Implemented validation |
|---|---|---|
| Deployment identity | `id`, `name`, `description`, `context` | Required text; deployment and plan identifiers use the pattern `[A-Za-z0-9][A-Za-z0-9._-]{0,99}` |
| `thresholds` | `tauA`, `tauAS`, `tauD`, `tauR`, `qMin`, version, owner and calibration-status metadata | `0 <= tauA <= tauAS <= tauD <= tauR <= 1`; `qMin` is finite and in `[0,1]`; metadata is required |
| `weights` | Sustainability and autonomy aggregation weights | Each is finite and in `[0,1]`; their sum is positive |
| `evaluator` | `targetWasteRatio`, `fullDeviationWasteRatio`, evaluator version | Both ratios lie in `[0,1]`, with the full-deviation ratio strictly above the target |
| `defaultEvidence` | Purchase, discard and advice counts, evidence confidence, consents, disclosure attempt and provenance | Purchased count is positive; discarded count lies between zero and purchased count; ignored suggestions lie between zero and total suggestions; confidence lies in `[0,1]` |
| `stakeholders` and `constraints` | Identity, role, authority, ethical concerns, classification, precedence and revision metadata | Required collections and unique identifiers; the three Home Hub constraints must be present with their required classifications |
| `plans` | Identifier, kind, mode, reference mismatch, reference residual, prediction confidence, confirmation, consent, reversibility, cost and burden | Required plan identifiers; reference mismatch in `(0,1]`; residual, confidence, cost and burden in `[0,1]`; human-review plans use advisory mode and privacy-block plans use restrictive mode |
| `contestationPolicy` | Authorised stakeholder and per-field correction permissions | Required policy version and stakeholder identifier; permissions are checked when a correction is submitted |
| `bdi` | Baseline desires and hard-violation desire | Required non-empty objectives |

The Home Hub evaluator requires `Cprivacy`, `Csustainability` and `Cautonomy`, with privacy classified as hard and the other two as soft. The plan library requires `I0` through `I5` and `I-review`. Configuration changes these plans' parameters within the fixed Home Hub model.

`EthicalAnalyser` divides each weighted sum by the sum of the weights. The article uses the same normalisation. For the reference values `0.65` and `0.35`, the denominator is one.

An overlay uses `ease-deployment-overlay/v1` and names a bundled parent through `extends`. The shipped parent names are `bundled:default`, `bundled:advisory-first` and `bundled:conservative-governance`. Nested objects merge recursively; lists whose elements have identifiers merge by `id`. The loader materialises the resulting complete deployment before validation and execution. The shipped advisory-first and conservative-governance files illustrate this mechanism without introducing separate decision paths.

## 3. Numerical evaluation and selection details

The sustainability evaluator computes waste as discarded divided by purchased perishables. It scales deviation between the configured target and full-deviation ratios and clips the result to `[0,1]`. Severity is the fraction of ignored suggestions when suggestions exist. With no suggestion history, severity is the clipped ratio of waste to the full-deviation reference. Sustainability mismatch is deviation multiplied by severity.

The autonomy indicator is one when the effective mode is delegated or restrictive and standing consent for automatic list changes is absent; otherwise it is zero. This soft state indicator is distinct from a candidate's `requiresAutomaticListConsent` eligibility guard. A failed guard excludes that candidate, independently of its predicted mismatch. The privacy indicator detects a supplied external-disclosure attempt without the corresponding consent and has hard precedence.

For configured soft-response plans, `PlanDefinition.predictedResidual` computes `clip(M * residualMismatchAtReference / referenceMismatch)`. All four soft candidates in the reported configurations use reference mismatch `0.403`. These predictions are stipulated configuration values. The shipped advisory-first case sets the I1 reference residual to `0.30`; at current mismatch approximately `0.434`, its projected residual is approximately `0.323`.

The analyser gives a hard privacy violation precedence over the soft aggregate. When no hard violation is present, insufficient aggregate confidence requests human review. After these cases, the selector retains or restores advisory mode through `I0` when current mismatch is below `tauAS`. `tauA` labels the advisory-response region; it does not independently trigger soft-plan selection. Otherwise, soft candidates are checked in this order:

1. Required standing consent is present.
2. Candidate mode falls within the currently authorised governance region.
3. Prediction confidence is at least `qMin`.
4. Predicted residual mismatch is strictly below `tauAS`.

The trace records each candidate's first failed condition. The comparator orders sufficient candidates by intrusion rank, predicted residual mismatch and stakeholder burden, in that order. Cost and reversibility are recorded but do not enter the comparator. A selected plan with `requiresConfirmation=true` remains pending. The effective mode changes when an execution record is applied.

## 4. Correction, consent and execution history

`ContestationRequest` distinguishes `HISTORICAL_FACT_CORRECTION` from `PROSPECTIVE_CONSENT_CHANGE`. The engine checks the supplied stakeholder identifier against the configured authorised stakeholder and checks permissions for each requested field. It does not authenticate the person supplying that identifier.

A historical correction starts from the evidence stored in the challenged trace. Permitted fields include discarded count, sustainability confidence and the consents recorded for that event. The engine retains the source evidence, records original and corrected values and the reason, updates current evidence and executes a new decision cycle. The resulting trace links back through `revisesTraceId`. A prospective consent change starts from current evidence and is limited to consent fields. It records the prior event without changing its historical authority and starts a current decision cycle without a historical revision link.

In the reported correction, the new trace selects `I0`; the source trace still records pending `I2`. `IntentionExecutor.confirm` rejects a trace that is not pending or already has an execution record. It does not check whether a later trace supersedes the proposal. The old proposal therefore remains confirmable. Deployment requires a check that rejects superseded proposals before execution.

An existing test confirms an assistive proposal, submits a correction and checks restoration of advisory mode. This test concerns the simulated mode state. The historical-correction batch entry starts with a pending proposal; no assistive action has been executed when the correction arrives.

## 5. Optional LLM path and secret handling

The adapter supports configured Responses and Chat Completions protocols through `OpenAiCompatibleLlmClient`. `LlmSettings` holds the enabled flag, endpoint, protocol, model, API key, authentication-header name and scheme, disclosure permission and timeout. Settings can be supplied at runtime or through the corresponding `EASE_LLM_*` environment variables.

For a cognitive update, the adapter first checks that the connector is enabled, explicit permission to disclose runtime evidence is active and the current analysis contains no hard privacy violation. Its request includes evidence, current mode, deterministic beliefs, mismatch indicators, governance state, active constraints, thresholds, authorised templates and any previous cognitive update.

The proposed response contains a summary, confidence-qualified beliefs and desires, assessments of plans `I1`–`I4`, knowledge notes and uncertainties. The local parser checks expected value types, list lengths, known and unique plan identifiers, and finite numerical values in `[0,1]`. Long text is truncated to the configured limits. Unknown object properties are ignored, and knowledge-note and uncertainty entries are converted to strings. Responses that fail the local checks produce a failed-update record and the decision proceeds with deterministic proposals. The provider receives a stricter structured-output schema; local parsing does not enforce every schema condition.

Accepted beliefs and desires enter the cognitive record. Accepted plan assessments can replace residual predictions and their confidence, capped by aggregate evidence confidence. The deterministic selector then checks eligibility and sufficiency and orders the candidates. Numerical analysis has already occurred in the cycle; its evaluator reads evidence and effective mode, not LLM belief text. LLM assessments can therefore change the selected plan through its predicted outcome and confidence. They leave the calculated mismatch and governance region unchanged. Prior cognitive content can enter a later LLM request.

The public settings map reports whether an API key is configured and omits the key itself. Keys remain in process memory; a separate operation clears the stored key. Endpoint validation rejects embedded user credentials, URL fragments and recognised credential-bearing query parameters. Authentication-header checks prevent overriding `Host` and `Content-Length`; authentication values reject control characters. Timeouts are restricted to 2–120 seconds. A pattern-based filter redacts recognised credentials from recorded exceptions. The supplied tests check these behaviours. No comprehensive security assessment or production key-management service is included.

Every reported batch scenario used the deterministic path with `EASE_LLM_ENABLED=false`, an empty API key and disclosure consent disabled. The LLM tests use injected responses and a loopback HTTP server; no external model was called.

## 6. Scenario and export formats

The bundled batch input is `src/main/resources/config/example-batch.json`, using `ease-scenario-batch/v1`. Its configuration map accepts bundled references, complete deployments or overlays. Each scenario selects a configuration by `configurationId`, optionally overrides evidence and optionally specifies a contestation. A reference-scenario flag supports reporting parameter differences. Invalid input is captured for the affected scenario while the other entries continue.

The aggregate JSON format is `ease-scenario-batch-result/v1`, with `ease-scenario-result/v1` result items. Each item records scenario and configuration identity, complete effective configuration, original and corrected evidence, parameter differences, original and final metrics, selected plan and mode, execution status, error or notes, trace links and execution time. The browser comparison table and exporters consume these result objects.

The CSV format is `ease-scenario-csv/v1`. Its columns are grouped below in their emitted order.

| Group | Column names |
|---|---|
| Identity | `schemaVersion`, `batchId`, `scenarioId`, `scenarioName`, `referenceScenario`, `configurationId`, `configurationName`, `status`, `executedAt` |
| Evidence and corrections | `originalConfidence`, `correctedConfidence`, `originalExternalDisclosureConsent`, `correctedExternalDisclosureConsent`, `originalAutomaticListChangeConsent`, `correctedAutomaticListChangeConsent`, `originalDiscardedPerishables`, `correctedDiscardedPerishables` |
| Decision | `aggregateMismatch`, `aggregateConfidence`, `governanceRegion`, `finalPlanId`, `finalMode`, `executionStatus` |
| Thresholds and evaluator | `tauA`, `tauAS`, `tauD`, `tauR`, `qMin`, `sustainabilityWeight`, `autonomyWeight`, `targetWasteRatio`, `fullDeviationWasteRatio` |
| Further detail | `planValuesJson`, `changedParametersJson`, `notes`, `error` |

Raw CSV `originalConfidence` and `correctedConfidence` denote sustainability evidence confidence. The derived `scenario-summary.csv` instead uses `originalAggregateConfidence` and `finalAggregateConfidence` for the aggregate Q values, with similarly explicit aggregate-mismatch headers. Plan values and parameter differences are JSON-encoded in their CSV cells. CSV `finalMode` denotes the selected plan's mode, which can differ from the effective runtime mode while confirmation is pending. JSON's richer result structure and the execution status resolve that distinction.

## 7. Existing verification evidence

ESM1 records execution on 14 September 2026 using Eclipse Temurin JDK 21.0.12.1+1, Bash 5.2.21, Python 3.12.14 and Linux x86_64. Its provenance manifest records byte-for-byte agreement of 94 source files with the identified commit. The original repository was unchanged. The existing test script reports `PASS: 20 EASE MVP tests` in `results/logs/tests.txt`.

| Existing checks in `EaseEngineTest.java` | Count | What they check |
|---|---:|---|
| Worked example; confirmation; duplicate confirmation | 3 | Reference values, selected I2, pending status, subsequent mode change and duplicate-execution rejection |
| Historical correction; correction after execution; multi-field historical correction | 3 | Original and revised evidence, revision links, recomputation and simulated mode restoration |
| Prospective consent; rejection of prospective non-consent corrections | 2 | Temporal separation between consent changes and historical fact correction |
| Hard-constraint precedence; low confidence; absent suggestion history; invalid evidence | 4 | Implemented governance branches and evidence invariants |
| Configuration round trip and persistence; configurable plans and thresholds | 2 | Loading, validation, serialisation and propagation of supplied parameters |
| Batch execution and export agreement | 1 | Four successful entries, isolated invalid input and consistency of stable exported fields |
| LLM proposal; fallback; disclosure permission; hard privacy; Responses client contract | 5 | Governed plan assessments, error handling, disclosure gates and local HTTP request/secret handling |
| **Total** | **20** | **Existing functional, regression and integration checks** |

Each of two executions of the existing five-entry batch produced four valid results and one expected invalid result. The table summarises the valid results; values are rounded.

| Scenario | Aggregate mismatch | Aggregate confidence | Selected plan and execution status | Final effective mode |
|---|---:|---:|---|---|
| Worked example | 0.403 | 0.935 | I2, assistive, pending confirmation | Advisory |
| Advisory-first | 0.434 | 0.930 | I1, advisory, executed | Advisory |
| Conservative governance | 0.403 | 0.922 | I0, advisory, monitoring | Advisory |
| Historical correction | 0.000 | 0.974 | I0, advisory, revised decision monitors | Advisory |

The invalid fixture sets `discardedPerishables` to **−2**. Its recorded error is `discardedPerishables must be between zero and purchasedPerishables`. Some original repository documentation describes an excessive discarded count; the unchanged input file and raw results establish that the executed fixture is the negative value.

`results/verification.json` records agreement between the two JSON outputs after excluding execution timestamps and generated trace identifiers, including UUIDs embedded in historical-correction provenance. All remaining emitted JSON fields agree. The supplementary checker compares all 36 CSV columns with their JSON counterparts; the reproduction wrapper also compares newly generated outputs with both archived runs. The normalised JSON SHA-256 is `da80601ee087534e14904aa52a9898cb2a3e5975f1b2e0109c890610438a8add`.

Weights, thresholds, proxies and predicted outcomes are stipulated inputs. The results concern execution of those rules; benefits to users and performance in an operational Home Hub have not been measured.

## 8. Reproduction and retained data

Unpack ESM1 and run the supplied wrapper from its top-level directory with JDK 21 and Python 3 available:

```bash
bash reproduce.sh
```

The wrapper copies the source into `generated/work/`, restores execution permissions in that disposable copy, executes the original test script and the original batch script twice, and explicitly disables the external LLM path. It writes new outputs under `generated/` and preserves the supplied source and raw files in `results/run-1/` and `results/run-2/`. A subsequent run accepts a new empty output directory, for example `bash reproduce.sh generated-next`. Relative paths and paths containing spaces are supported. `check_outputs.py` compares outputs without altering the application's calculations. `provenance.json` identifies the supplied files and source snapshot.

For direct execution from the unchanged MVP source directory, restore script permissions if the ZIP extractor removed them, then use the original entry points:

```bash
chmod u+x scripts/compile.sh scripts/test.sh scripts/batch.sh
EASE_LLM_ENABLED=false EASE_LLM_API_KEY= EASE_LLM_DATA_DISCLOSURE_CONSENT=false ./scripts/test.sh
EASE_LLM_ENABLED=false EASE_LLM_API_KEY= EASE_LLM_DATA_DISCLOSURE_CONSENT=false ./scripts/batch.sh
```

The batch command writes timestamped JSON and CSV under `output/batch/`. Running the unchanged scripts regenerates the recorded values, apart from timestamps and generated trace identifiers.

## 9. Engineering considerations retained from the discussion

Applying EASE to another domain requires a mapping from stakeholder values to observable attributes, evidence sources and candidate outcomes. The Home Hub formulas and thresholds specify one such mapping. Other deployments require their own evaluators and plans, together with evidence for the chosen thresholds, prediction accuracy and stakeholder burden.

For longer operation, a deployment must specify when exceptions expire, how inferred preferences are attributed, and how threshold changes are versioned. Those policies determine which authority applies to each decision and whether a later revision changes the interpretation of an earlier event.

Trace storage requires access rules, retention periods and controls over which evidence is disclosed. The current local server uses in-memory state, identifier-based permissions and process-memory API keys. Multi-user deployment requires persistence, authentication, authorisation and secret management, together with the supersession check described in Section 4.

For the optional LLM, the parser rejects malformed proposals and the selector enforces eligibility. A validly formatted but inaccurate plan assessment can still change which permitted candidate is selected. Measuring prediction accuracy, stability and the resulting selection changes requires a separate model evaluation.


Source references for checking this document are `configuration/DeploymentConfiguration.java`, `configuration/ConfigurationCodec.java`, `mapek/MapeKLoop.java`, `mapek/analyse/EthicalAnalyser.java`, `bdi/deliberation/BdiDeliberator.java`, `bdi/intention/HomeHubPlanLibrary.java`, `runtime/EaseEngine.java`, `mapek/execute/IntentionExecutor.java`, `mapek/knowledge/RuntimeKnowledgeBase.java`, `llm/config/LlmSettings.java`, `llm/update/LlmKnowledgeBdiUpdater.java`, `scenario/ScenarioCsvExporter.java` and `src/test/java/org/ease/mvp/EaseEngineTest.java`. Java paths other than the last are relative to `src/main/java/org/ease/mvp/` in the exact commit linked above.
