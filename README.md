# DrinkSync

Mobile-first ordering and queue management platform for event bars. Customers scan a station-specific QR code, browse a drink menu, build custom drinks, place orders, complete payment, and receive real-time queue updates. Vendors manage incoming orders through a tablet-friendly dashboard.

## Problem

Event bars suffer from queue chaos, lost revenue from walk-aways, and slow service caused by verbal ordering and payment delays. DrinkSync eliminates these friction points by digitising the entire order-to-pickup flow.

## Features

- **QR Code Entry** — Customers scan a station QR code to access the menu instantly (no app install required)
- **Custom Drink Builder** — Spirit + Mixer + Cup Option (reuse or new)
- **Pre-made Items** — Ready-to-serve cans, bottles, and cocktails
- **Real-time Queue** — Live order status via WebSocket (STOMP over SockJS)
- **Vendor Dashboard** — Tablet-friendly interface for managing incoming orders
- **Pickup Window** — Time-limited collection with order number verification
- **Dark/Light Theme** — Automatic theme support with manual toggle
- **Offline Support** — PWA with service worker for graceful offline handling

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.4.1 |
| Database | PostgreSQL 16, Flyway migrations |
| Real-time | WebSocket (STOMP/SockJS) |
| Auth | JWT (jjwt 0.12.6) |
| Frontend | React 19, TypeScript, Vite |
| Styling | CSS custom properties (no framework) |
| Routing | react-router-dom v7 |
| Testing | JUnit 5, Testcontainers, jqwik, Vitest, Playwright, fast-check, Gatling |
| Observability | OpenTelemetry, Elasticsearch, Kibana |
| Infrastructure | Docker Compose |

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌────────────┐
│  React PWA  │────▶│  Spring Boot API │────▶│ PostgreSQL │
│  (Vite)     │◀────│  + WebSocket     │     │            │
└─────────────┘     └──────────────────┘     └────────────┘
                           │
                    ┌──────┴──────┐
                    │ OTel Agent  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐     ┌─────────┐
                    │  OTel       │────▶│ Elastic │
                    │  Collector  │     │ + Kibana│
                    └─────────────┘     └─────────┘
```

## Order Lifecycle

```
DRAFT → AWAITING_PAYMENT → PAID → PREPARING → READY → COLLECTED
  │                          │
  └──── CANCELLED ◀──────────┘
                               EXPIRED (pickup window exceeded)
```

- Orders remain in DRAFT until checkout
- Payment confirmation assigns a queue position
- Only paid orders enter the preparation queue
- All state transitions are persisted with timestamps and pushed via WebSocket within 1 second

## Project Structure

```
DrinkSync/
├── backend/                 # Spring Boot application
│   ├── src/main/java/       # Controllers, services, models, config
│   ├── src/main/resources/  # application.yml, Flyway migrations
│   └── src/test/            # Unit, integration, property-based tests
├── frontend/                # React PWA
│   ├── src/components/      # Shared UI components
│   ├── src/pages/           # Customer & vendor page routes
│   ├── src/hooks/           # Custom React hooks
│   ├── src/services/        # API clients & WebSocket
│   ├── src/types/           # TypeScript type definitions
│   └── e2e/                 # Playwright end-to-end tests
├── load-tests/              # Gatling performance tests
├── observability/           # OTel collector config, Kibana setup
├── docker-compose.yml       # Full stack orchestration
└── Makefile                 # Developer shortcuts
```

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local backend development)
- Node.js 18+ (for local frontend development)

### Quick Start (Docker)

```bash
# Start the full stack (app + observability)
make up

# Or start only the app (faster, no Elasticsearch/Kibana)
make up-app
```

Services will be available at:
- **Frontend**: http://localhost:5173
- **Backend API**: http://localhost:8080
- **Kibana**: http://localhost:5601 (if observability is running)

### Local Development

**Backend:**
```bash
cd backend
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

### Running Tests

```bash
# Backend tests (unit + integration with Testcontainers)
cd backend && ./mvnw test

# Frontend unit tests
cd frontend && npm run test

# End-to-end tests
cd frontend && npm run test:e2e

# Load tests
cd load-tests && mvn gatling:test
```

## API Reference

Base URL: `http://localhost:8080/api`

### Stations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/stations/{id}` | Get station details |
| GET | `/stations/{id}/menu` | Get station menu (spirits, mixers, premade items) |

### Orders
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/stations/{stationId}/orders` | Create a new order |
| GET | `/orders/{orderId}` | Get order details |
| PUT | `/orders/{orderId}/items` | Update order items (DRAFT only) |
| POST | `/orders/{orderId}/checkout` | Move to AWAITING_PAYMENT |
| POST | `/orders/{orderId}/pay` | Confirm payment (idempotent) |
| POST | `/orders/{orderId}/cancel` | Cancel an order |
| PATCH | `/orders/{orderId}/state` | Vendor state transition (auth required) |
| GET | `/stations/{stationId}/orders` | List station orders (optional `?state=` filter) |

### Sessions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/sessions` | Create a customer session |

### Auth (Vendor)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/login` | Vendor login (returns JWT) |

### Menu Management (Vendor, auth required)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/stations/{id}/spirits` | Add spirit item |
| POST | `/stations/{id}/mixers` | Add mixer item |
| POST | `/stations/{id}/premade-items` | Add premade item |
| PATCH | `/spirits/{id}/availability` | Toggle spirit availability |
| PATCH | `/mixers/{id}/availability` | Toggle mixer availability |
| PATCH | `/premade-items/{id}/availability` | Toggle premade item availability |

### Headers
- `X-Session-Id` — Customer session identifier (required for customer endpoints)
- `Idempotency-Key` — UUID for payment deduplication
- `Authorization: Bearer <token>` — JWT for vendor endpoints

### WebSocket

Connect via SockJS at `http://localhost:8080/ws`

Subscribe topics:
- `/topic/orders/{orderId}` — Individual order status updates
- `/topic/stations/{stationId}/orders` — All order updates for a station (vendor dashboard)

## Makefile Commands

| Command | Description |
|---------|-------------|
| `make up` | Start all services |
| `make up-app` | Start app only (no observability) |
| `make up-build` | Start all with rebuild |
| `make down` | Stop all services |
| `make clean` | Stop and remove volumes |
| `make logs` | Tail all logs |
| `make backend-logs` | Tail backend logs |
| `make db-shell` | Open psql shell |
| `make test-backend` | Run backend tests in container |
| `make test-frontend` | Run frontend tests in container |
| `make kibana-setup` | Configure Kibana data views |

## Environment Variables

### Backend (set via Docker Compose)
| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/smarteventbar` | Database URL |
| `SPRING_PROFILES_ACTIVE` | `docker` | Active Spring profile |
| `OTEL_SERVICE_NAME` | `drinksync-backend` | OpenTelemetry service name |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317` | OTel collector endpoint |

### Frontend
| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend API URL |
| `VITE_WS_URL` | `ws://localhost:8080/ws` | WebSocket URL |

## Database Schema

Key tables: `station`, `spirit_item`, `mixer_item`, `premade_item`, `session`, `orders`, `order_item`, `order_state_history`, `auth_attempt`, `idempotency_key`

Migrations are managed by Flyway and run automatically on startup.

## License

Private — All rights reserved.
