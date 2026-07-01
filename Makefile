.DEFAULT_GOAL := help

.PHONY: help install dev run-backend run-frontend \
	format format-backend format-frontend \
	format-check format-check-backend format-check-frontend \
	lint lint-fix test test-backend test-frontend \
	build build-backend build-frontend verify clean health

help: ## Show available commands
	@echo ""
	@echo "SnapMemoria local commands"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""

install: ## Install root tooling and frontend dependencies
	@if [ -f package-lock.json ]; then \
		npm ci; \
	else \
		npm install; \
	fi
	@if [ -f frontend/package-lock.json ]; then \
		npm --prefix frontend ci; \
	else \
		npm --prefix frontend install; \
	fi

dev: ## Start backend and frontend together
	@set -e; \
	./mvnw spring-boot:run & backend_pid=$$!; \
	npm --prefix frontend run dev & frontend_pid=$$!; \
	trap 'kill $$backend_pid $$frontend_pid 2>/dev/null || true' INT TERM EXIT; \
	wait $$backend_pid $$frontend_pid

run-backend: ## Start only the Spring Boot backend
	./mvnw spring-boot:run

run-frontend: ## Start only the React frontend
	npm --prefix frontend run dev

format: ## Automatically format Java and frontend code
	$(MAKE) format-backend
	$(MAKE) format-frontend

format-backend: ## Format Java code with Spotless
	./mvnw spotless:apply

format-frontend: ## Format frontend files with Prettier
	npm --prefix frontend run format

format-check: ## Check formatting without changing files
	$(MAKE) format-check-backend
	$(MAKE) format-check-frontend

format-check-backend: ## Check Java formatting with Spotless
	./mvnw spotless:check

format-check-frontend: ## Check frontend formatting with Prettier
	npm --prefix frontend run format:check

lint: ## Run frontend linting
	npm --prefix frontend run lint

lint-fix: ## Automatically fix frontend lint issues where possible
	npm --prefix frontend run lint:fix

test: ## Run backend and frontend tests
	$(MAKE) test-backend
	$(MAKE) test-frontend

test-backend: ## Run Spring Boot tests
	./mvnw test

test-frontend: ## Run React and TypeScript tests
	npm --prefix frontend run test

build: ## Build backend and frontend production artifacts
	$(MAKE) build-backend
	$(MAKE) build-frontend

build-backend: ## Build the Spring Boot JAR without rerunning tests
	./mvnw -DskipTests package

build-frontend: ## Build the frontend production bundle
	npm --prefix frontend run build

verify: ## Run all formatting checks, linting, tests, and builds
	$(MAKE) format-check
	$(MAKE) lint
	$(MAKE) test
	$(MAKE) build

clean: ## Remove generated build and test files
	./mvnw clean
	rm -rf frontend/dist frontend/coverage

health: ## Check whether the local backend is running
	curl --fail http://127.0.0.1:8080/actuator/health
