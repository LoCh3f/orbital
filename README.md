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

> **Note:** `news-service` currently serves a mocked fallback feed — it doesn't send an API key to NewsAPI, so real news integration is on the roadmap rather than wired up today. This is expected behavior, not a bug.

## ⚙️ Gateway Configuration

The gateway reads a few optional environment variables, none of which `docker-compose.yml` sets by default (safe, permissive-by-default behavior for local dev):

| Variable | Default | Purpose |
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

A build of the web app is deployed automatically on every release to **https://loch3f.github.io/orbital/** — since no backend is hosted publicly, it'll only show live data if you have `make up` running locally on the machine you're viewing it from.

## 📖 API Docs

Kotlin API reference (Dokka), generated from all backend modules, published alongside the web app: **https://loch3f.github.io/orbital/docs/**.

## 📊 Monitoring

Prometheus (`localhost:9090`) scrapes all three backend services; Grafana (`localhost:3000`, `admin`/`admin`) auto-provisions an **Orbital Overview** dashboard — request rate, latency, JVM heap, CPU, and service up/down — no manual setup needed.

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
