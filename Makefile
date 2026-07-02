.PHONY: help up down logs clean ps status health

# Default target
help:
	@echo "Orbital Platform - Docker Commands"
	@echo ""
	@echo "up           Start all services"
	@echo "up-gateway   Start only gateway"
	@echo "down         Stop all services"
	@echo "logs         Show logs (follow)"
	@echo "clean        Stop and remove volumes"
	@echo "ps           Show container status"
	@echo "status       Quick health check"
	@echo "health       Detailed health check"
	@echo ""

# Start services
up:
	docker compose up -d --build

up-gateway:
	docker compose up -d --build gateway

# Stop services
down:
	docker compose down

# Logs
logs:
	docker compose logs -f

# Cleanup
clean:
	docker compose down -v

# Status
ps:
	docker compose ps

status:
	@echo "=== Container Status ==="
	docker compose ps
	@echo ""
	@echo "=== Gateway Health ==="
	@curl -s http://localhost:8080/health || echo "Gateway not running"

health:
	@echo "=== Detailed Health Check ==="
	@echo "Gateway:    $$(curl -s http://localhost:8080/health || echo 'DOWN')"
	@echo "Postgres M: $$(docker exec postgres-market pg_isready -U market_user || echo 'DOWN')"
	@echo "Postgres N: $$(docker exec postgres-news pg_isready -U news_user || echo 'DOWN')"
	@echo "Redis:      $$(docker exec redis-cache redis-cli ping || echo 'DOWN')"