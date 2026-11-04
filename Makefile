.DEFAULT_GOAL := help
COMPOSE := docker compose
PROD := docker compose -f docker-compose.yml

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start the dev stack (hot reload) — web :5173, api :8080
	$(COMPOSE) up --build

down: ## Stop everything, keep the database volume
	$(COMPOSE) down

reset: ## Stop everything and wipe the database volume
	$(COMPOSE) down -v

logs: ## Tail all service logs
	$(COMPOSE) logs -f --tail=100

api-logs: ## Tail backend logs only
	$(COMPOSE) logs -f --tail=100 api

prod: ## Run the production-shaped stack (nginx on :3000)
	$(PROD) up --build

psql: ## Open a psql shell on the dev database
	$(COMPOSE) exec postgres psql -U $${POSTGRES_USER:-hackathon} -d $${POSTGRES_DB:-hackathon}

test: test-api test-web ## Run every test

test-api: ## Run backend tests in Docker (no local JDK needed)
	$(COMPOSE) run --rm --no-deps api mvn -B test

test-web: ## Run frontend tests
	cd frontend && npm test

lint: ## Format backend + lint frontend
	$(COMPOSE) run --rm --no-deps api mvn -B com.spotify.fmt:fmt-maven-plugin:format
	cd frontend && npm run lint && npm run format:check

health: ## Hit both health endpoints
	@curl -fsS localhost:8080/api/health && echo "" && curl -fsS localhost:8080/actuator/health && echo ""

.PHONY: help up down reset logs api-logs prod psql test test-api test-web lint health
