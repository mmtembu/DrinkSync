.PHONY: up down build logs restart clean db-shell backend-logs frontend-logs

# Start all services (app + observability)
up:
	docker compose up -d

# Start only the app (no Elasticsearch/Kibana/OTel — much faster)
up-app:
	docker compose up -d postgres frontend backend

# Start all services with build
up-build:
	docker compose up -d --build

# Start only the app with build
up-app-build:
	docker compose up -d --build postgres frontend backend

# Start only observability services
up-observability:
	docker compose up -d elasticsearch otel-collector kibana

# Stop all services
down:
	docker compose down

# Stop all services and remove volumes (clean slate)
clean:
	docker compose down -v

# Rebuild and restart all services
rebuild:
	docker compose down
	docker compose up -d --build

# View logs for all services
logs:
	docker compose logs -f

# View backend logs
backend-logs:
	docker compose logs -f backend

# View frontend logs
frontend-logs:
	docker compose logs -f frontend

# View database logs
db-logs:
	docker compose logs -f postgres

# Restart a specific service
restart-backend:
	docker compose restart backend

restart-frontend:
	docker compose restart frontend

# Open a psql shell to the database
db-shell:
	docker compose exec postgres psql -U smarteventbar -d smarteventbar

# Run backend tests (in a separate container)
test-backend:
	docker compose exec backend mvn test

# Run frontend tests (in a separate container)
test-frontend:
	docker compose exec frontend npm run test

# --- Observability ---

# View Kibana
kibana:
	open http://localhost:5601

# View Elasticsearch cluster health
es-health:
	curl -s http://localhost:9200/_cluster/health | python3 -m json.tool

# Setup Kibana data views
kibana-setup:
	bash observability/kibana-setup.sh

# View OTel collector logs
otel-logs:
	docker compose logs -f otel-collector
