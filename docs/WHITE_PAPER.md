# DrinkSync: Eliminating Queue Chaos at Event Bars

**A White Paper on Mobile-First Order Management for High-Volume Beverage Service**

*Version 1.0 — May 2026*

---

## Executive Summary

DrinkSync is a mobile-first ordering and queue management platform purpose-built for event bars. By replacing verbal ordering, cash fumbling, and physical queues with a scan-order-pay-collect digital flow, DrinkSync reduces average service time per customer by an estimated 60–70%, eliminates walk-away revenue loss, and gives vendors real-time operational visibility.

This white paper outlines the problem space, the DrinkSync solution architecture, the technical decisions behind the platform, and the roadmap toward a production-ready commercial product.

---

## 1. The Problem

### 1.1 Queue Chaos at Events

Event bars — at festivals, concerts, sports venues, and corporate functions — face a unique operational challenge: extremely high demand concentrated into short time windows (intermissions, set breaks, half-time). The result is long, disorganised queues that frustrate customers and overwhelm staff.

### 1.2 Revenue Loss from Walk-Aways

Industry estimates suggest that 15–30% of potential customers abandon a bar queue when wait times exceed 10 minutes. At a 5,000-person event with R50 average spend, even a 15% walk-away rate represents R37,500 in lost revenue per event.

### 1.3 Operational Bottlenecks

Traditional event bar service involves three sequential blocking steps:

1. **Verbal ordering** — Customer communicates their drink choice (noisy environment, misunderstandings)
2. **Payment processing** — Cash handling or card terminal wait
3. **Drink preparation** — Only begins after payment completes

Each step blocks the next customer. DrinkSync decouples these steps entirely.

### 1.4 No Existing Solution Fits

General-purpose food delivery apps (Uber Eats, Mr D) are designed for restaurant-to-home delivery, not on-site event service. They lack:
- Station-specific QR entry (no address input needed)
- Real-time queue position tracking
- Pickup window enforcement
- Vendor dashboard optimised for high-throughput bar service

---

## 2. The DrinkSync Solution

### 2.1 Core Flow

```
Scan QR → Browse Menu → Build Order → Pay → Queue Position → Collect
```

1. **Scan** — Customer scans a station-specific QR code. No app download required (PWA).
2. **Browse** — Full drink menu with spirits, mixers, and pre-made options.
3. **Build** — Custom drink builder: select spirit(s), mixer(s), and cup option (reuse or new).
4. **Pay** — Simulated payment in Phase 1; real payment integration in Phase 2.
5. **Queue** — Assigned a queue position. Real-time status updates via WebSocket.
6. **Collect** — Order number displayed on vendor screen. Time-limited pickup window.

### 2.2 Vendor Experience

Vendors access a tablet-friendly dashboard showing:
- Incoming paid orders in preparation queue
- One-tap state transitions (Preparing → Ready → Collected)
- Real-time order count and throughput metrics
- Menu management (add items, toggle availability)

### 2.3 Key Design Principles

| Principle | Implementation |
|-----------|---------------|
| Zero friction entry | QR scan opens PWA — no install, no signup |
| Payment before preparation | Orders only enter the queue after payment confirmation |
| Real-time feedback | WebSocket pushes state changes within 1 second |
| Idempotent payments | UUID-based deduplication prevents double charges |
| Soft location control | Station-based QR codes guide customers without GPS tracking |
| Offline resilience | Service worker provides graceful degradation |

---

## 3. Technical Architecture

### 3.1 System Overview

DrinkSync is a full-stack application with clear separation between a REST + WebSocket backend and a Progressive Web App frontend.

```
┌─────────────────┐         ┌─────────────────────┐         ┌──────────────┐
│   Customer      │  HTTP   │                     │  JDBC   │              │
│   (Mobile PWA)  │────────▶│   Spring Boot API   │────────▶│  PostgreSQL  │
│                 │◀────────│   Port 8080         │         │  Port 5432   │
│                 │   WS    │                     │         │              │
└─────────────────┘         └──────────┬──────────┘         └──────────────┘
                                       │
┌─────────────────┐                    │ OTLP/gRPC
│   Vendor        │                    ▼
│   (Tablet PWA)  │         ┌─────────────────────┐         ┌──────────────┐
│                 │         │   OTel Collector    │────────▶│ Elasticsearch│
└─────────────────┘         │   Port 4317         │         │ + Kibana     │
                            └─────────────────────┘         └──────────────┘
```

### 3.2 Backend

- **Language & Framework**: Java 17, Spring Boot 3.4.1
- **Database**: PostgreSQL 16 with Flyway-managed migrations
- **Real-time**: STOMP over SockJS WebSocket for bidirectional communication
- **Authentication**: JWT tokens (HS256, 12-hour expiry) for vendor endpoints
- **Validation**: Jakarta Bean Validation on all request DTOs
- **Observability**: OpenTelemetry Java agent exporting traces, logs, and metrics

### 3.3 Frontend

- **Framework**: React 19 with TypeScript
- **Build Tool**: Vite (fast HMR, optimised production builds)
- **PWA**: Service worker for offline support and instant load on repeat visits
- **Real-time**: @stomp/stompjs + sockjs-client for WebSocket subscriptions
- **Styling**: CSS custom properties with dark/light theme support (no CSS framework)
- **Routing**: react-router-dom v7 with animated page transitions

### 3.4 Infrastructure

All services are orchestrated via Docker Compose:

| Service | Image | Purpose |
|---------|-------|---------|
| postgres | postgres:16-alpine | Primary data store |
| backend | Custom (Spring Boot) | API + WebSocket server |
| frontend | Custom (Vite dev server) | PWA serving |
| elasticsearch | elastic/elasticsearch:8.15.0 | Log/trace/metric storage |
| otel-collector | otel/opentelemetry-collector-contrib | Telemetry pipeline |
| kibana | elastic/kibana:8.15.0 | Observability dashboards |

### 3.5 Data Model

The schema is designed around the **Station** as the central entity:

- **Station** — A physical bar location with access code, cup pricing, and pickup window config
- **Spirit/Mixer/Premade Items** — Menu items belonging to a station
- **Session** — Anonymous customer session tied to a station (no account required)
- **Order** — Tracks state, queue position, total price, and pickup window
- **Order Item** — Line items with spirit/mixer/premade references, cup option, and quantity
- **Order State History** — Full audit trail of every state transition with timestamps

### 3.6 Order State Machine

```
                    ┌──────────────┐
                    │    DRAFT     │ ← Customer building order
                    └──────┬───────┘
                           │ checkout
                    ┌──────▼───────┐
                    │  AWAITING    │ ← Ready for payment
                    │  PAYMENT     │
                    └──────┬───────┘
                           │ pay (idempotent)
                    ┌──────▼───────┐
                    │    PAID      │ ← Queue position assigned
                    └──────┬───────┘
                           │ vendor action
                    ┌──────▼───────┐
                    │  PREPARING   │ ← Vendor making drink
                    └──────┬───────┘
                           │ vendor action
                    ┌──────▼───────┐
                    │    READY     │ ← Pickup window starts
                    └──────┬───────┘
                           │ vendor action
                    ┌──────▼───────┐
                    │  COLLECTED   │ ← Terminal state
                    └──────────────┘

    Side transitions:
    DRAFT/AWAITING_PAYMENT/PAID → CANCELLED (customer or vendor)
    READY → EXPIRED (pickup window exceeded)
```

State transitions are:
- **Strict** — No state can be skipped
- **Audited** — Every transition is recorded with timestamp and trigger source
- **Broadcast** — Pushed to subscribed clients via WebSocket within 1 second

---

## 4. Testing Strategy

DrinkSync employs a multi-layered testing approach:

### 4.1 Unit Tests
- Backend: JUnit 5 with Mockito for service-layer logic
- Frontend: Vitest with React Testing Library for component behaviour

### 4.2 Integration Tests
- Testcontainers spin up real PostgreSQL instances for repository and controller tests
- No mocked databases — tests run against the actual schema and migrations

### 4.3 Property-Based Tests
- Backend: jqwik for order state machine invariants
- Frontend: fast-check for type system and filter logic verification
- Ensures correctness across thousands of randomised inputs

### 4.4 End-to-End Tests
- Playwright tests covering full customer and vendor flows
- Runs against the real application stack

### 4.5 Load Tests
- Gatling simulations modelling concurrent order placement and WebSocket subscriptions
- Validates system behaviour under event-scale load

---

## 5. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| Payment duplication | UUID-based idempotency keys with database-level uniqueness |
| Vendor impersonation | JWT authentication with HS256 signing and 12-hour expiry |
| Session hijacking | UUIDv4 session identifiers (unguessable) |
| Brute-force login | Auth attempts tracked in database for rate limiting |
| Data integrity | PostgreSQL CHECK constraints + application-level validation |
| CORS | Configurable allowed origins (locked down in production) |
| Input validation | Jakarta Bean Validation on all inbound DTOs |

---

## 6. Observability

Full-stack observability is built in from day one:

- **Traces**: Distributed tracing across HTTP requests and WebSocket messages
- **Logs**: Structured JSON logging with trace correlation IDs
- **Metrics**: JVM metrics, HTTP request metrics, and custom business metrics
- **Pipeline**: OpenTelemetry Collector receives all telemetry and exports to Elasticsearch
- **Dashboards**: Kibana provides pre-configured data views for logs, traces, and metrics

This enables:
- Real-time monitoring of order throughput during events
- Latency analysis for WebSocket delivery guarantees
- Error tracking and alerting
- Post-event performance analysis

---

## 7. Market Opportunity

### 7.1 Target Market

- Music festivals and concerts
- Sports venues (stadiums, racecourses)
- Corporate events and conferences
- Food and drink markets
- University events

### 7.2 Revenue Model (Planned)

| Model | Description |
|-------|-------------|
| Per-transaction fee | Small percentage or flat fee per completed order |
| Subscription | Monthly fee per station for unlimited orders |
| Event package | Flat rate for single-event deployments |

### 7.3 Competitive Advantage

- **Zero customer friction** — No app download, no account creation
- **Purpose-built** — Designed specifically for high-volume event bar service
- **Real-time** — Sub-second order status updates keep customers informed
- **Vendor-friendly** — One-tap order management designed for speed
- **South African market** — Built with local payment methods in mind (PayShap, Ozow, Yoco)

---

## 8. Roadmap

### Phase 1 — MVP (Current)
- ✅ QR scan → menu → order → simulated payment → queue → collect
- ✅ Real-time WebSocket updates
- ✅ Vendor dashboard with order management
- ✅ Full observability stack
- ✅ Comprehensive test coverage

### Phase 2 — Payment Integration
- Real payment processing (PayShap Request-to-Pay, Ozow, Yoco)
- Payment confirmation webhooks
- Refund handling for cancelled orders

### Phase 3 — Enhanced Experience
- Push notifications (order ready, pickup reminder)
- Customer order history (optional account creation)
- Drink favourites and reorder
- Multi-language support

### Phase 4 — Scale & Analytics
- Multi-vendor marketplace (multiple bars at one event)
- Event analytics dashboard (peak times, popular items, revenue)
- Smart queue optimisation (estimated wait times, load balancing)
- Inventory management integration

### Phase 5 — Enterprise
- White-label deployment for venue operators
- API for third-party integrations
- Advanced reporting and export
- SLA-backed uptime guarantees

---

## 9. Conclusion

DrinkSync addresses a clear, painful problem in the event industry: the inefficiency of traditional bar service at scale. By digitising the entire order-to-pickup flow and providing real-time visibility to both customers and vendors, DrinkSync transforms event bar operations from chaotic queues into a smooth, data-driven service.

The platform is built on proven, production-grade technology (Spring Boot, PostgreSQL, React) with observability, testing, and security considered from the start — not bolted on later. The PWA approach eliminates the biggest adoption barrier (app installation), making it viable for one-time event attendees.

With simulated payment in place and the full order lifecycle operational, DrinkSync is ready for real-world pilot testing and payment gateway integration.

---

*DrinkSync — Scan. Order. Collect.*

---

**Contact**: [Your contact information]
**Repository**: Private
**Status**: MVP Complete — Seeking pilot event partners
