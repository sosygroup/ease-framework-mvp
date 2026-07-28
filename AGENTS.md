# Development guide for automated contributors

## Scope

This repository is the executable MVP of the EASE ethics-aware adjustable
autonomy framework. Keep the implementation aligned with the documented Home
Hub/food-waste evaluator unless a change in scientific semantics has been
explicitly approved.

## Toolchain

- Use JDK 21.
- Keep the runtime dependency-free unless a new dependency is justified and
  approved.
- Compile with `./scripts/compile.sh`.
- Run all tests with `./scripts/test.sh`.
- Run the dashboard with `./scripts/run.sh`.
- Run reproducible batch experiments with `./scripts/batch.sh`.

## Architecture

- Preserve the explicit outer MAPE-K structure under `mapek/`.
- Preserve the explicit inner BDI structure under `bdi/`.
- Keep deployment parameters in the validated configuration model under
  `configuration/` and JSON resources under `src/main/resources/config/`.
- Dashboard, CLI and batch execution must call the same application logic.
- Keep LLM output proposal-only and subject to deterministic constraints,
  confidence, consent, governance and least-intrusive selection.
- Keep Knowledge traces and contestations append-only.
- Historical corrections recompute the challenged event; prospective consent
  changes must not rewrite past events.

## Implementation rules

- Do not add worked-example thresholds, weights or plan outcomes directly to
  Java or JavaScript code.
- Validate new configuration fields before execution and return actionable
  error messages.
- Preserve backward compatibility of the bundled worked example.
- Do not duplicate calculations in the web interface or exporters.
- Keep JSON and CSV result schemas stable; document intentional schema changes.
- Use existing code style: four-space Java indentation, small immutable records
  for data, explicit names and dependency-free executable tests.
- Do not commit generated `build/`, `output/` or `tmp/` content.

## Security and privacy

- Never commit API keys, `.env` files, credentials, private keys or provider
  responses containing sensitive data.
- Keep API keys out of public state, traces, logs and exported results.
- Do not enable runtime-evidence disclosure by default.
- Treat the local paper manuscript and experimental output as non-public until
  redistribution has been explicitly approved.
- Do not add remote operations, telemetry or network calls outside the governed
  LLM connector without approval.

## Required verification

For every behavioural change:

1. run `./scripts/test.sh`;
2. retain the exact worked-example regression;
3. add or update focused tests;
4. verify that batch errors remain isolated;
5. verify that JSON and CSV remain consistent;
6. check the dashboard manually when UI behaviour changes.

Before a release, inspect `git status --ignored`, review every untracked file,
and scan the candidate commit for secrets. Do not commit, tag, push or publish
without explicit maintainer approval.
