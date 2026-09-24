.PHONY: help up down build pull restart logs ps clean status health env \
        gradle-build test lint format app-desktop app-web docs psql-market psql-news hooks urls

SERVICES := gateway market-service news-service postgres-market postgres-news redis prometheus grafana pgadmin

# Default target
help:
	@echo "Orbital Platform - Makefile"
	@echo ""
	@echo "Docker stack:"
	@echo "  up                 Start all services (build first)"
	@echo "  up-<service>       Start one service, e.g. make up-gateway"
	@echo "  down               Stop all services"
	@echo "  down-<service>     Stop one service, e.g. make down-redis"
	@echo "  restart            Restart all services"
	@echo "  restart-<service>  Restart one service"
	@echo "  build              Rebuild all images (no start)"
	@echo "  build-<service>    Rebuild one image, e.g. make build-market-service"
	@echo "  pull               Pull non-built images (postgres, redis, prometheus, grafana, pgadmin)"
	@echo "  logs               Follow logs for all services"
	@echo "  logs-<service>     Follow logs for one service"
	@echo "  ps                 Show container status"
	@echo "  clean              Stop and remove volumes (destructive)"
	@echo "  status             Quick gateway health check"
	@echo "  health             Detailed health check across gateway/postgres/redis"
	@echo "  env                Create .env from .env.example if missing"
	@echo "  urls               Print local URLs for every service"
	@echo ""
	@echo "  Valid <service> values: $(SERVICES)"
	@echo ""
	@echo "Gradle / backend:"
	@echo "  gradle-build       ./gradlew build"
	@echo "  test               ./gradlew test"
	@echo "  lint               ./gradlew ktfmtFormat detekt"
	@echo "  format             ./gradlew ktfmtFormat"
	@echo "  docs               Generate API docs (Dokka) to build/dokka/htmlMultiModule"
	@echo ""
	@echo "orbital-app:"
	@echo "  app-desktop        Run the desktop client (needs 'make up' first)"
	@echo "  app-web            Run the web client in a dev server (needs 'make up' first)"
	@echo ""
	@echo "Database:"
	@echo "  psql-market        Open a psql shell into postgres-market"
	@echo "  psql-news          Open a psql shell into postgres-news"
	@echo ""
	@echo "Tooling:"
	@echo "  hooks              npm install (sets up the local commitlint git hook)"
	@echo ""

# --- Environment ---
env:
	@if [ -f .env ]; then \
		echo ".env already exists, leaving it alone"; \
	else \
		cp .env.example .env && echo "Created .env from .env.example"; \
	fi

# --- Docker stack: whole-stack targets ---
up:
	docker compose up -d --build

down:
	docker compose down

restart:
	docker compose restart

build:
	docker compose build

pull:
	docker compose pull postgres-market postgres-news redis prometheus grafana pgadmin

logs:
	docker compose logs -f

ps:
	docker compose ps

clean:
	docker compose down -v

# --- Docker stack: per-service targets (make up-gateway, make logs-redis, ...) ---
up-%:
	docker compose up -d --build $*

down-%:
	docker compose stop $*

restart-%:
	docker compose restart $*

build-%:
	docker compose build $*

logs-%:
	docker compose logs -f $*

# --- Status ---
status:
	@echo "=== Container Status ==="
	docker compose ps
	@echo ""
	@echo "=== Gateway Health ==="
	@curl -s http://localhost:8080/health || echo "Gateway not running"

health:
	@echo "=== Detailed Health Check ==="
	@echo "Gateway:    $$(curl -s http://localhost:8080/health || echo 'DOWN')"
	@echo "Market:     $$(curl -s http://localhost:8081/health || echo 'DOWN')"
	@echo "News:       $$(curl -s http://localhost:8082/health || echo 'DOWN')"
	@echo "Postgres M: $$(docker exec postgres-market pg_isready -U market_user || echo 'DOWN')"
	@echo "Postgres N: $$(docker exec postgres-news pg_isready -U news_user || echo 'DOWN')"
	@echo "Redis:      $$(docker exec redis-cache redis-cli ping || echo 'DOWN')"

urls:
	@echo "Gateway:     http://localhost:8080"
	@echo "Market:      http://localhost:8081"
	@echo "News:        http://localhost:8082"
	@echo "Prometheus:  http://localhost:9090"
	@echo "Grafana:     http://localhost:3000 (admin/admin)"
	@echo "pgAdmin:     http://localhost:5050"

# --- Gradle / backend ---
gradle-build:
	./gradlew build

test:
	./gradlew test

lint:
	./gradlew ktfmtFormat detekt

format:
	./gradlew ktfmtFormat

docs:
	./gradlew dokkaHtmlMultiModule
	@echo "Docs generated at build/dokka/htmlMultiModule/index.html"

# --- orbital-app ---
app-desktop:
	./gradlew :orbital-app:run

app-web:
	./gradlew :orbital-app:wasmJsBrowserDevelopmentRun

# --- Database ---
psql-market:
	docker exec -it postgres-market psql -U market_user -d market_data

psql-news:
	docker exec -it postgres-news psql -U news_user -d news_data

# --- Tooling ---
hooks:
	npm install
