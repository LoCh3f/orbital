# Changelog

## [1.2.1](https://github.com/LoCh3f/orbital/compare/v1.2.0...v1.2.1) (2026-10-02)


### Bug Fixes

* **app:** decode coingecko as double ([9e794dc](https://github.com/LoCh3f/orbital/commit/9e794dcde62ea1119740d0e5500daadcbf6c5174))
* **app:** decode coingecko as double ([19dff7f](https://github.com/LoCh3f/orbital/commit/19dff7f2140bf92166150b678d5e1af7e1295ca6))

## [1.2.0](https://github.com/LoCh3f/orbital/compare/v1.1.0...v1.2.0) (2026-10-02)


### Features

* **app:** add a CoinGecko-direct market data client for the demo build ([c5dfa98](https://github.com/LoCh3f/orbital/commit/c5dfa98c6da98776adb4569835fe45f72c1b0527))
* **app:** add the market-only demo app composable ([0acf241](https://github.com/LoCh3f/orbital/commit/0acf2419897bd018e79fc3ccc14dfa8d7cdc68b1))
* **app:** wire a -PorbitalDemoBuild flag to select the demo app at compile time ([af55a01](https://github.com/LoCh3f/orbital/commit/af55a01a3c3efff3b69ce9647e8f70f1f46256bf))
* **gateway:** harden proxy cache with Redis-resilient reads and bounded in-memory fallback ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))
* **gateway:** make rate limiter proxy-aware and opt-in only ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))


### Bug Fixes

* address final whole-branch review findings ([0f7723d](https://github.com/LoCh3f/orbital/commit/0f7723d1e10fd0b62e7097e3d464b3b739f7855a))
* address final whole-branch review findings ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))
* **docker:** install curl in runtime stages for healthchecks ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))
* **market-service:** fetch requested coins by id instead of filtering top-N ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))
* **news-service:** guard Redis cache read with try/catch fallback ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))
* **orbital-app:** make coin-logo cache thread-safe ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))
* **persistence:** log swallowed per-row persistence failures ([a2a75d8](https://github.com/LoCh3f/orbital/commit/a2a75d82b0dad3a2e2401d7bcbc54a28da87010e))

## [1.1.0](https://github.com/LoCh3f/orbital/compare/v1.0.2...v1.1.0) (2026-10-01)


### Features

* **app:** add coin logo loading for desktop and web ([a0ca01f](https://github.com/LoCh3f/orbital/commit/a0ca01f4dd5435c7f090b6e5fd0c2c3569c81954))
* **app:** add price sparkline chart via Tradingview charts ([ff507a3](https://github.com/LoCh3f/orbital/commit/ff507a34808075242c5ba526f79e0b91b7ce9683))
* **app:** add sparkline and logo fields to marketprice ([5c8f0be](https://github.com/LoCh3f/orbital/commit/5c8f0be9d8ac83db0fc19b3a6d08645e9ba0c809))
* **market:** expose 7-day sparkline and logo URL from CoinGecko ([60bc5c3](https://github.com/LoCh3f/orbital/commit/60bc5c3c00fe37349781de1c959282fb6eee08c8))


### Bug Fixes

* **app:** missing nav icon file ([231e371](https://github.com/LoCh3f/orbital/commit/231e371a112ad6224b464f63ac2758f8e320c56b))

## [1.0.2](https://github.com/LoCh3f/orbital/compare/v1.0.1...v1.0.2) (2026-09-24)


### Bug Fixes

* **ci:** use PAT for release-please so it can trigger downstream workflows ([ccea7d8](https://github.com/LoCh3f/orbital/commit/ccea7d89ca9c5833eca948e9f0f68c54d7cdbbfb))
* optimized test workflow ([d589591](https://github.com/LoCh3f/orbital/commit/d5895918cdafe79eebb1dbd8f3f2012cc4111f5d))

## [1.0.1](https://github.com/LoCh3f/orbital/compare/v1.0.0...v1.0.1) (2026-09-24)


### Bug Fixes

* build.gradle.kts ([2f4284f](https://github.com/LoCh3f/orbital/commit/2f4284fed2cd0b5dd31bacd69c36c2df77e9e127))
* update deprecated dependencies ([2282237](https://github.com/LoCh3f/orbital/commit/2282237860f5093042eacb8ecba3b351bbe44008))
