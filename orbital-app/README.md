# Orbital client

Compose Multiplatform targets JVM desktop and WebAssembly browsers. Run `./gradlew :orbital-app:run` for desktop or `./gradlew :orbital-app:wasmJsBrowserDevelopmentRun` for the full local web app after starting the backend. Desktop honors `GATEWAY_URL`; local web development uses `http://127.0.0.1:8080`.

Build the backend-free market demo with `./gradlew :orbital-app:wasmJsBrowserDistribution -PorbitalDemoBuild=true`. It calls CoinGecko directly and has no News tab. The full app serves clearly labeled synthetic news.

Desktop charts use native Canvas; browser charts use the bundled Lightweight Charts library. Historical points preserve UTC timestamps. Sparkline-only fallback charts show relative trends. Desktop distribution is native; the placeholder Dockerfile is not an application image.
