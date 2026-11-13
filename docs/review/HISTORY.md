# Historical chart implementation — 13 November 2026

Status: implemented and validated from `develop`; organized into focused local commits on
`feat/market-history-and-hardening` with the owner's authorization.

The tracked universe retains the existing top-20 market-cap selection. Binance mappings only
resolve identities for these coins; neither archive discovery nor a history request adds assets.
Coins leaving that selection retain stored observations, but their queued imports stop running.

The connected web and desktop clients expose 24H, 7D, 1M, 1Y, and All available controls. Explicit
zoom buttons, browser pan/wheel zoom, and desktop wheel zoom request smaller windows. Queries
sample across the entire selected span, including observations older than the legacy 500-point
limit. Earliest/latest loaded dates, incomplete imports, source, quote currency, and detectable
gaps are displayed. Charts split at gaps instead of joining across missing observations.

Binance monthly/daily archives are discovered with paginated listings, checksummed, parsed without
extracting filesystem paths, and imported atomically with a durable file ledger. Daily history
loads in bounded background batches; hourly and five-minute files are requested for viewed
windows. Failed providers yield to other tracked coins, and database interruptions do not terminate
the worker. Unsupported pair identity lookups are limited to once daily.

CoinGecko USD observations stay separate from Binance USDT closing prices. The free Demo history
request is bounded to 365 days, and local snapshots grow that archive over time. The default
snapshot interval is 15 minutes. API keys remain server-side.

## Verification

- Full `check`, full web distribution, and desktop jar: passed.
- 83 test executions across backend, shared libraries, desktop, and browser: zero failures.
- Uncached market-service tests against isolated PostgreSQL 15: passed, including full-span
  sampling, source isolation, one-candle gaps, inactive assets, queue backoff, and schema setup.
- Real Binance BTCUSDT August 2017 archive: SHA-256 verified; all 15 daily candles imported into
  PostgreSQL; repeat import added no duplicates; earliest closing timestamp `1503014399`
  (17 August 2017 23:59:59 UTC).
- Chromium fixtures: range selection, zoom requests, wheel zoom, quote/source switching, gap
  segmentation, and zero page errors. A 390-pixel mobile viewport remained 390 pixels wide.
- Compose UI regression tests: range/source selection, zoom bounds, cancellation of obsolete
  requests, and unlisted-coin behavior.
- Backend-free demo distribution: built successfully.
- `git diff --check`: passed.

[Validation evidence](artifacts/orbital-history-evidence.zip) contains logs, fixture screenshots,
and local reproduction helpers. Browser screenshots use synthetic data; the real import is
recorded separately. Temporary PostgreSQL and browser fixture services were removed afterward.

## Operational limits

Run the backend and market ingestor for archive downloads and persistent coverage. The subsequent local app preview started real imports for the tracked coins; Bitcoin reached
17 August 2017. Services were stopped afterward and the imported data was preserved. Coverage
continues to fill progressively when the ingestor runs. The backend-free Pages demo retains its explicit seven-day overview.

Binance coverage starts at the available USDT pair listing, which can be later than a coin's
creation. Unlisted or unverified assets use available CoinGecko history. Public CoinGecko quotas
also apply to interactive requests; configure `COINGECKO_DEMO_API_KEY` in `.env`. The free API
cannot provide every historical date at arbitrary fine resolution.

Dataset terms and attribution are documented in the root README and displayed for Binance charts.
Existing security-review work and report edits were preserved. The owner authorized committing
all report files and exports in separate commits. No push or deployment was performed.
