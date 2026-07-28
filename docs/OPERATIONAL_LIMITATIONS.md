# Operational limitations and feasibility boundary

The MVP turns the operational specification in the paper into running software. It provides evidence of **implementability and internal operational consistency**, not empirical evidence of ethical adequacy or practical effectiveness.

## What the MVP establishes

- The EASE runtime artefacts can be represented and versioned explicitly.
- Hard and soft constraints can be processed without compensating away hard violations.
- Per-constraint mismatch, confidence, aggregate governance state, and threshold regions can remain inspectable.
- BDI-style candidates can pair plans with autonomy modes and retain rejected alternatives.
- Least-intrusive selection can be implemented as a deterministic, reviewable procedure.
- Confirmation, execution feedback, contestation, recomputation, and rollback can be integrated into one cycle.
- The paper's complete Charlie/Home Hub trace can be reproduced by executable code.

## What remains outside the MVP

### 1. Sensors are simulated

Inventory removals, bin observations, interaction events, consent, and data-egress attempts are entered through the UI or CLI. There is no physical refrigerator, bin sensor, retailer interface, or identity provider.

An empirical deployment needs authenticated monitoring adapters, clock synchronisation, freshness policies, contradictory-evidence handling, and measured sensor error models.

### 2. The value-to-attribute mapping is illustrative

The sustainability mapping is explicit but selected to reproduce the analytical trace. It has not been elicited from stakeholders, validated by domain experts, or tested as a faithful proxy for sustainability.

The constants, normalisation window, definition of avoidability, and treatment of ignored advice all require domain study and governance approval.

### 3. Thresholds are not calibrated

`T1` uses the numerical values in the paper. No historical data, pilot, false-escalation rate, missed-escalation rate, or user-burden measure supports them.

The engine correctly versions and reports the policy, but calibration and authorised policy revision remain deployment responsibilities.

### 4. Outcome prediction is deterministic

Without the optional LLM connector, residual mismatch values for `I1`-`I4` are transparent scenario functions, not predictions learned from Home Hub outcomes. With the connector enabled, an LLM may propose updated residual mismatch and confidence values for those authorised templates. These are still uncalibrated model assessments, not validated causal predictions, and do not establish predictive accuracy.

A production implementation needs validated models, uncertainty bounds, drift monitoring, and safe behaviour when prediction quality degrades.

### 5. The plan library is small and domain-specific

The library covers the plans required by the paper scenario, a privacy block, safe human review, and rollback. It is not a general BDI plan language, planner, or ethical theorem prover.

### 6. Persistence is process-local

The knowledge state, traces, and execution records are versioned and append-only in memory. Restarting the process clears them. There is no durable database, tamper-evident ledger, retention policy, encryption, backup, or selective-disclosure mechanism.

This is sufficient to demonstrate semantics, but not regulatory-grade accountability.

### 7. Security and authorisation are not production-ready

The server binds to loopback and has no authentication. The API does not verify that a caller is Charlie, the operator, or a regulator. Roles and permissions are evaluated as scenario artefacts rather than cryptographic identities.

Do not expose this MVP to an untrusted network.

The optional LLM API key is stored only in process memory and redacted from all public state and traces. It is nevertheless submitted from the local browser to the loopback server over HTTP and is not protected by a production secret manager, operating-system keychain, rotation policy, or workload identity. A configurable endpoint also introduces data-egress and server-side request forgery risks if untrusted users can modify it. The MVP requires explicit consent before transmitting runtime evidence and suppresses the call during a `Cprivacy` violation, but production deployments must additionally restrict allowed provider hosts, enforce TLS, authenticate configuration changes, minimise disclosed evidence, and use managed credentials.

### 8. LLM output is fallible and provider-dependent

The connector requests strict structured output and validates identifiers, ranges, and lengths, but schema validity does not establish factual, ethical, or predictive correctness. Prompt injection can also be embedded in sensor provenance or prior text. The system treats all context as untrusted data, constrains the model to a proposal-only role, limits it to the authorised plan templates, and applies deterministic policy afterward; these controls reduce but do not eliminate model risk.

The connector currently supports OpenAI Responses and OpenAI-compatible Chat Completions payloads. Provider-specific authentication, schema dialects, rate limits, retention behaviour, regional processing, and refusal/error shapes may differ. The MVP records failures and falls back locally rather than retrying silently or bypassing governance.

### 9. Generalisation remains within the Home Hub evaluator

Thresholds, weights, plan characteristics, evidence defaults, contestation
permissions and BDI defaults are configurable, and batch sensitivity studies
are supported. The executable evaluator still has the paper's Home Hub
semantics: privacy, food-waste sustainability and confirmation autonomy. It is
not a plug-in framework for arbitrary ethical domains or constraint languages.

Contestation covers discarded-item evidence, confidence and both consent
values, with distinct historical and prospective temporal semantics. Changing
constraint principles, precedence, thresholds, learned beliefs or plan effects
through a stakeholder adjudication workflow remains outside the MVP.

### 10. No empirical human evaluation

The dashboard has been functionally and visually checked, but there is no usability, accessibility, interaction-burden, trust, acceptance, or contestability study with participants.

### 11. No performance or scalability claim

The MVP is a single Java process for one household. Runtime traces, active
configuration and asynchronous batch jobs are memory-resident; batch files
are durable only when downloaded or written by `BatchCli`. There is no
distributed coordination, multi-tenant isolation, high-availability mechanism,
throughput benchmark, deadline analysis, or large-scale trace evaluation.

## Priority path to an empirical prototype

1. Connect real or high-fidelity simulated Home Hub monitoring and actuator adapters.
2. Elicit and validate the sustainability proxy and its uncertainty model.
3. Calibrate thresholds and outcome predictors on historical or pilot observations.
4. Replace process-local state with encrypted, versioned, policy-controlled persistence.
5. Add authenticated stakeholder roles and authorised contestation workflows.
6. Measure false/missed escalation, latency, resource use, user burden, reversibility, and contestation outcomes.
7. Conduct scenario-based safety analysis followed by controlled user evaluation.
