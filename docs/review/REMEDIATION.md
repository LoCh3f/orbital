# Orbital remediation status

The review baseline is `develop` at `2d58bd1d9e51fba8955d3b6cd582b7e04bbdb9ca`. The owner authorized the changes to be organized into focused Conventional Commits on `feat/market-history-and-hardening`. Academic report changes are recorded separately in the same series.

## Implemented changes

| Finding | Result |
| --- | --- |
| F01 | Published development ports bind to loopback; Grafana and pgAdmin require the admin profile. |
| F02 | Nginx overwrites forwarded identity, the private gateway trusts that ingress, and probes bypass user quotas. |
| F03 | Sentinel has separate persistent writable directories and seeds numeric monitor addresses to avoid Docker DNS blocking during master loss. |
| F04 | HTTP calls and connection acquisition have deadlines; upstream failures return safe envelopes; `/ready` checks dependencies separately from `/health`. |
| F05 | The additive history-points endpoint preserves Unix-second observation timestamps. Web charts use them directly; desktop charts use actual elapsed spacing and UTC hover labels. Relative sparklines are labeled separately. |
| F06 | Invalid persistence batches roll back and propagate failure. Repository suites can run against PostgreSQL; its CI job disables cache reuse and reruns tests. |
| F07 | Only the worker writes market observations. Backfill completion has a separate table; repeated provider observations are skipped and new snapshot IDs are deterministic. Legacy rows remain preserved. |
| F08 | Market rows and expansion state use coin identity. |
| F09 | Refresh errors retain content with a stale warning and retry action; refreshes serialize and cancellation propagates. |
| F10 | Sub-cent prices retain precision; smaller values display an explicit precision threshold and nonfinite values are unavailable. |
| F11 | Image publication depends on checks, includes shared build inputs and the worker, and handles release tags. PR commitlint checks the complete range; PostgreSQL CI and dependency review are added. Hosted required-check enforcement remains pending below. |
| F12 | Lint/check tasks no longer invoke formatters, and checks plus documentation work together. |
| F13 | Backend Ktor is 2.3.13, Netty is constrained to 4.1.140.Final, PostgreSQL JDBC is 42.7.14, and Logback is 1.6.6. Client/backend Ktor versions remain separate. Dependabot and dependency review provide ongoing checks. |
| F14 | Docker excludes credentials, reports, Git metadata, and generated outputs. Runtime Java uses a non-root UID and honors JAVA_TOOL_OPTIONS. Builds cache Gradle separately from source layers. |
| F15 | News-service cache keys have their own namespace, separate from gateway envelopes. |
| F16 | Search fields have labels; empty results, expansion state, chart summaries, refresh retry, and HTTPS article actions are explicit. |
| F17 | Setup, demo/full architecture, configuration, mocked news categories, admin profiles, and history semantics are documented. |

HTTP clients owned by composition, gateway clients, Redis pools, and application database pools close on shutdown. Blocking cache operations run on the IO dispatcher.

## Validation

The evidence is collected under `/tmp/orbital-fix-evidence` and in the adjacent `/tmp/orbital-fix-*.log` files. The complete backend/docs build and final client checks passed. Both web variants built successfully, and browser assertions verified the exported search names and error/recovery behavior.

Verified results:

- Combined checks, API documentation, and the full web/desktop artifacts build successfully.
- 64 JVM/desktop/browser test executions pass; this includes common tests run on multiple platforms.
- Six market and three news repository tests execute against real PostgreSQL, including rollback, history, and upgrade behavior.
- All four service images build. Runtime checks confirm UID 10001, independent client budgets despite forged forwarding headers, health after quota exhaustion, structured 502 errors, readiness degradation, and exact timestamp serialization.
- Database loss changes readiness to 503 in approximately three seconds in the isolated test.
- Redis promotion succeeds and restarting all three Sentinels preserves the promoted master.
- Chromium assertions and screenshots confirm corrected precision, stable row state, filters, and stale-data feedback in the full client.
- An intentionally malformed file fails formatting validation without its hash changing.

## Hosted settings and manual verification

The connected GitHub tool exposes no ruleset mutation capability, and its legacy branch-protection read returned 403 during the review. The proposed required-check rule is [github-required-checks.json](github-required-checks.json). After the new workflows have run, verify their actual check-context names and replace only the empty required-status-check rule in the existing main-protection ruleset, preserving its other rules and conditions. Do not replace the entire ruleset with this fragment. Required contexts are `test`, `postgres-integration`, `commitlint`, and `dependency-review`.

Hosted enforcement and the new workflows have not been exercised remotely; this local commit series has not been pushed. Manual screen-reader testing and a native desktop-window walkthrough remain unverified; no accessibility-conformance claim is made.

## Compatibility and operational limits

The old price-array history API remains available. The new endpoint returns `{data:[{timestamp:1700000000,priceUsd:10.0}]}` in ascending time; history remains bounded to the latest 500 stored rows. The backfill marker table is added without dropping existing observations. Preexisting duplicate rows are retained; timestamped responses collapse duplicate timestamps.

Keep one ingestion worker. Redis rate limiting deliberately fails open while Redis is unavailable. The local Compose deployment remains a development/demo topology: persistent numeric Sentinel addresses assume the Docker network survives; a full network rebuild requires reinitializing the disposable Redis/Sentinel cluster together. PostgreSQL automatic promotion, public TLS/secrets distribution, and native installers are outside this academic remediation.

The owner explicitly authorized local commits after reviewing the running app. No push, deployment, or hosted settings change is included in that authorization.
