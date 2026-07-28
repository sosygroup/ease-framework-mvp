# Configuration, scenarios and export schemas

The MVP uses the same configuration and application logic from the dashboard,
single-scenario CLI, batch CLI and tests. No UI-specific copy of the evaluator
or deliberation logic exists.

## Deployment configuration

A complete deployment document uses `schemaVersion: "ease-deployment/v1"`.
The canonical reference is
`src/main/resources/config/default-deployment.json`.

| Object | Purpose and principal fields |
|---|---|
| root | `id`, `name`, `description`, `context` |
| `thresholds` | ordered `tauA`, `tauAS`, `tauD`, `tauR`; `qMin`; version/owner/calibration metadata |
| `weights` | `sustainability`, `autonomy` in `[0,1]`, not both zero |
| `evaluator` | target and full-deviation waste ratios plus evaluator version |
| `defaultEvidence` | purchase/discard/advice counts, confidence, both consents, disclosure attempt and provenance |
| `stakeholders` | identity, role, authority and ethical concerns |
| `constraints` | hard/soft classification, authority, precedence, weight and revision metadata |
| `plans` | kind, mode, reference/residual mismatch, confidence, confirmation, reversibility, cost, burden and consent requirement |
| `contestationPolicy` | authorised stakeholder and permissions for each correctable field |
| `bdi` | baseline desires and hard-violation desire |

The current evaluator requires `Cprivacy`, `Csustainability` and `Cautonomy`,
and the finite plan library requires `I0` through `I5` plus `I-review`.
`Cprivacy` must remain hard; the other two must remain soft. These requirements
preserve the scientific semantics of the Home Hub evaluator while allowing its
experimental parameters to vary.

Validation also enforces:

- safe unique identifiers;
- ordered thresholds in `[0,1]`;
- confidence, weights, costs, burden and residual values in `[0,1]`;
- non-negative evidence counts and logical subset constraints;
- `fullDeviationWasteRatio > targetWasteRatio`;
- unique stakeholder, constraint and plan identifiers;
- an advisory human-review plan and a restrictive privacy-block plan;
- non-empty metadata and BDI objectives.

Errors identify the invalid path where possible, for example
`$.thresholds is required` or
`evaluator.fullDeviationWasteRatio must be greater than evaluator.targetWasteRatio`.

## Overlay documents

An overlay avoids duplicating the full reference:

```json
{
  "schemaVersion": "ease-deployment-overlay/v1",
  "extends": "bundled:default",
  "patch": {
    "id": "my-experiment",
    "name": "My experiment",
    "weights": {
      "sustainability": 0.7,
      "autonomy": 0.3
    },
    "plans": [
      {
        "id": "I1",
        "residualMismatchAtReference": 0.3
      }
    ]
  }
}
```

Nested objects are merged recursively. Lists whose elements have `id` fields
are merged by identifier, so the example changes only `I1`. An overlay may
extend `bundled:default`, `bundled:advisory-first`, or
`bundled:conservative-governance`. The resulting active configuration is always
materialised and downloadable as a complete `ease-deployment/v1` document.

## Scenario batch input

A batch uses `schemaVersion: "ease-scenario-batch/v1"`:

```json
{
  "schemaVersion": "ease-scenario-batch/v1",
  "batchId": "experiment-1",
  "name": "Threshold sensitivity",
  "configurations": {
    "reference": "bundled:default",
    "variant": {
      "schemaVersion": "ease-deployment-overlay/v1",
      "extends": "bundled:default",
      "patch": {
        "id": "variant",
        "name": "Variant",
        "thresholds": {"qMin": 0.9}
      }
    }
  },
  "scenarios": [
    {
      "id": "reference",
      "name": "Reference",
      "referenceScenario": true,
      "configurationId": "reference"
    },
    {
      "id": "variant",
      "name": "Variant",
      "referenceScenario": false,
      "configurationId": "variant",
      "evidence": {
        "sustainabilityConfidence": 0.88
      },
      "contestation": {
        "correctionType": "HISTORICAL_FACT_CORRECTION",
        "correctedConfidence": 0.95,
        "reason": "Sensor calibration was corrected."
      }
    }
  ]
}
```

Each configuration value is either a bundled reference or a complete/overlay
JSON object. A scenario selects it through `configurationId`. Optional
`evidence` fields override configured default evidence. Optional `contestation`
accepts:

- `correctionType`: `HISTORICAL_FACT_CORRECTION` or
  `PROSPECTIVE_CONSENT_CHANGE`;
- `correctedDiscardedPerishables`;
- `correctedConfidence`;
- `correctedExternalDisclosureConsent`;
- `correctedAutomaticListChangeConsent`;
- `reason`.

At least one scenario should set `referenceScenario: true` for meaningful
parameter-difference reporting. Errors are captured per scenario.

## Result and export schemas

The aggregate JSON schema is `ease-scenario-batch-result/v1`; each item in
`results` is `ease-scenario-result/v1`. A result contains:

- batch/scenario/configuration identifiers and names;
- the complete effective configuration and original evidence;
- corrected evidence, when applicable;
- changed parameters relative to the reference configuration/evidence;
- original and final metrics;
- original and final plan, final mode and execution status;
- result status (`SUCCESS` or `ERROR`), notes and error;
- source/final trace identifiers and ISO-8601 execution time.

The CSV schema is `ease-scenario-csv/v1`, with one row per scenario and these
stable columns:

```text
schemaVersion,batchId,scenarioId,scenarioName,referenceScenario,
configurationId,configurationName,status,executedAt,
originalConfidence,correctedConfidence,
originalExternalDisclosureConsent,correctedExternalDisclosureConsent,
originalAutomaticListChangeConsent,correctedAutomaticListChangeConsent,
originalDiscardedPerishables,correctedDiscardedPerishables,
aggregateMismatch,aggregateConfidence,governanceRegion,
finalPlanId,finalMode,executionStatus,
tauA,tauAS,tauD,tauR,qMin,
sustainabilityWeight,autonomyWeight,
targetWasteRatio,fullDeviationWasteRatio,
planValuesJson,changedParametersJson,notes,error
```

Nested plan definitions and changed parameters are JSON-encoded inside their
CSV cells. The comparison table is rendered from the same scenario-result
objects used for JSON and CSV export.

## Commands and output locations

```bash
# Validate and execute one configuration
./scripts/demo.sh demo --config=path/to/config.json

# Run the bundled batch; writes output/batch/*.json and *.csv
./scripts/batch.sh

# Select input and output directory
./scripts/batch.sh path/to/batch.json path/to/output-directory
```

The dashboard offers the same batch definition, progress view and download
formats through `/api/batch/*`.
