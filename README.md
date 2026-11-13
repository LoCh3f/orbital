# Orbital
<div align="center">
  <img src="orbital_logo.jpeg" alt="Orbital Logo" width="400" height="400"/>
</div>

Project overview, architecture, and features: see [OVERVIEW.md](OVERVIEW.md).

## 🚦 Getting Started

**Prerequisites:** JDK 21, Docker (with Compose).

```bash
cp .env.example .env
make up        # builds and starts gateway, market-service, news-service, Postgres x2, Redis, Prometheus, Grafana, pgAdmin
make status     # quick gateway health check
make health     # detailed health check across all services
make down       # stop everything
```

Verify it's working end-to-end:

```bash
curl localhost:8080/health
curl localhost:8080/api/v1/market/prices   # live CoinGecko data, proxied + cached by the gateway
curl localhost:8080/api/v1/news            # news-service data
```

> **Note:** `news-service` serves a synthetic crypto feed without making external requests. Other category filters currently return no articles; it does not send an API key to NewsAPI, so real news integration is on the roadmap rather than wired up today. This is expected behavior, not a bug.

## ⚙️ Gateway Configuration

The gateway reads a few optional environment variables, which are forwarded explicitly by Compose where supported. Compose uses Sentinel-backed Redis and trusts its private Nginx ingress; directly running the gateway defaults to untrusted proxy headers:

| Variable | Standalone default | Purpose |
|---|---|---|
| `CORS_ALLOWED_HOST` | unset (any origin allowed) | Restrict CORS to a single HTTPS origin in production, e.g. `loch3f.github.io`. |
| `REDIS_URL` | unset (in-process fallback cache) | Point the gateway's proxy cache at Redis instead of its bounded in-memory fallback. |
| `TRUST_PROXY_HEADERS` | unset / `false` (rate limiter keys on the real TCP peer) | Set to `true` **only** when the gateway sits behind infrastructure you control that overwrites any client-supplied `X-Forwarded-For` header — otherwise a direct client could spoof it to bypass rate limiting entirely. When enabled, the rate limiter keys on the forwarded client address instead of the proxy's. |

## 🖥️ Desktop & Web App

`orbital-app` is a Kotlin Multiplatform client (desktop + `wasmJs` web) with a typed market/news UI — search, category filters, live auto-refresh. Needs the backend running (`make up`) first.

```bash
./gradlew :orbital-app:run                          # desktop window
./gradlew :orbital-app:wasmJsBrowserDevelopmentRun   # web, dev server + browser tab
```

A **market-only demo** build is deployed automatically on every release to **https://loch3f.github.io/orbital/** — it fetches live data directly from CoinGecko's public API (no key, no backend) via a separate build flavor of the same wasmJs target (`-PorbitalDemoBuild=true`, see `.github/workflows/deploy-web.yml`). There's no News tab in this build, since there's no backend to serve it from. Running `./gradlew :orbital-app:wasmJsBrowserDevelopmentRun` locally (no flag) still builds the full app — Market + News, backend-connected via `make up` — exactly as before.

## 📖 API Docs

Kotlin API reference (Dokka), generated from all backend modules, published alongside the web app: **https://loch3f.github.io/orbital/docs/**.

## 📊 Monitoring

Prometheus (`localhost:9090`) scrapes all three backend services; Grafana (`localhost:3000`, enabled with the `admin` Compose profile) auto-provisions an **Orbital Overview** dashboard — request rate, latency, JVM heap, CPU, and service up/down — no manual setup needed.

## 🛠️ Development

```bash
./gradlew build                 # build all modules
./gradlew test                  # run all tests
./gradlew ktfmtFormat           # apply Kotlin formatting
./gradlew detekt                # static analysis
```

Dependency versions are centralized in `gradle/libs.versions.toml`. Commits must follow [Conventional Commits](https://www.conventionalcommits.org/) (`type(scope): description`), enforced locally via a git hook (`npm install` once to set it up) and in CI. Releases (version bump + `CHANGELOG.md`) are automated via [release-please](https://github.com/googleapis/release-please) from that commit history.

## 🚀 Deployment / Release Process

Releases are cut automatically by [release-please](https://github.com/googleapis/release-please): every push to `main` updates a standing release PR (version bump + `CHANGELOG.md`, derived from Conventional Commit history). Merging that PR creates a GitHub Release + tag, which triggers `.github/workflows/deploy-web.yml` to build the web app and API docs and publish both to GitHub Pages.

One-time setup this repo needs (not automated, has to be done once in GitHub's UI): **Settings → Pages → Source: "GitHub Actions"**.

## Security and operational defaults

Published development ports bind to `127.0.0.1`. Keep them private; before network deployment configure unique database/admin credentials, TLS, and an explicit CORS origin. Start optional admin consoles with `docker compose --profile admin up -d`. Nginx overwrites client forwarding headers, and only its private gateway path may enable `TRUST_PROXY_HEADERS=true`.

`/health` is liveness; `/ready` checks required dependencies. Neither consumes the public request budget. Redis-backed rate limiting fails open during Redis loss, so monitor Redis availability. Sentinel uses separate persistent runtime volumes seeded from `redis/*.conf`; do not edit runtime state in the repository.

The market ingestor owns snapshot writes. Backfill completion is recorded separately from live data and retries incomplete work on restart. Persistence batches are atomic: any invalid row rejects the batch. The legacy history endpoints return the most recent 500 observations. The range endpoint described below samples the complete selected window instead. `/api/v1/market/history-points/{coinId}` adds timestamped points (`timestamp` in Unix seconds, `priceUsd` in USD, ascending time); the original `/history/{coinId}` price-array endpoint remains available.

The public demo calls CoinGecko directly. The full web/desktop client calls the gateway. Timestamped stored history uses UTC; upstream sparklines are labeled as relative trends because they omit observation times. Synthetic news is labeled in the UI.

Validation is read-only: `./gradlew check dokkaHtmlMultiModule`. Run `./gradlew ktfmtFormat` explicitly to change formatting. Browser tests require Chromium/Chrome; set `CHROME_BIN` if it is not on PATH. Optional PostgreSQL repository tests use `TEST_POSTGRES_URL` (JDBC URL including user/password query parameters), `TEST_DB_USER`, and `TEST_DB_PASSWORD`; run against a disposable database, as each test creates a schema.

Sentinel runtime volumes retain numeric topology addresses so a stopped Docker hostname cannot block elections. Restarting Sentinels preserves the elected master. If you destroy and recreate the entire Docker network, treat Redis as a disposable cache cluster: stop the stack and reset its Redis/Sentinel volumes together before bootstrapping a new topology. Do not reset PostgreSQL volumes. The local Compose stack does not promise automatic PostgreSQL promotion or durable Redis topology across a full cluster rebuild.

For PostgreSQL repository runs, use `--no-build-cache --rerun-tasks` to ensure H2 results cannot be reused. CI applies these flags to its PostgreSQL job.

## Historical charts and archive imports

The connected desktop/web app offers 24H, 7D, 1M, 1Y, and All available views. Range queries select bounded-resolution samples across the whole time span, rather than discarding observations older than the latest 500. Zooming or panning requests a finer window; desktop also offers wheel zoom and both clients have explicit zoom buttons. Missing coverage is drawn as separate segments and reported alongside earliest/latest loaded dates.

Only the existing top-20 market universe is tracked. Binance mappings use explicit CoinGecko IDs for known pairs and identity-checked CoinGecko Binance tickers for additional tracked coins. An unlisted or unverified coin falls back to the separate CoinGecko series; it does not create a new tracked asset.

Run the market ingestor to populate the durable archive jobs. Daily Binance archives provide all available exchange history; finer 1h/5m files are fetched on demand for viewed windows. Imports resume from checksummed, transactionally recorded files and prioritize user-selected ranges. The UI reports incomplete imports. It cannot show pre-listing Binance prices or manufacture missing candles.

Binance series are labeled with their actual USDT pair; they are not merged into CoinGecko's aggregated USD prices. CoinGecko public history is requested within its 365-day limit and retained locally as collection continues. Set `COINGECKO_DEMO_API_KEY` in `.env` for authenticated free-plan collection. The default polling interval is 15 minutes (`INGEST_INTERVAL_MS=900000`); provider quotas also cover interactive price requests.

The public backend-free Pages demo retains its 7-day overview. Full archive controls require the backend-connected app, because imports and persistent storage run on the server.

Binance-derived chart data is provided under the Binance Vision dataset terms, including attribution, non-commercial use and share-alike requirements. This is separate from the application's code license. See https://github.com/binance/binance-public-data/blob/master/TERMS_AND_CONDITIONS.md. CoinGecko source: https://www.coingecko.com/en/api.
