# DrinkSync: A Mobile-First Ordering and Queue Management Platform for Event Bars

**White Paper**
**Version 1.0 — May 2026**
**Author: Mangaliso Mtembu**

---

## Abstract

DrinkSync is a mobile-first Progressive Web Application (PWA) that digitises the entire order-to-pickup flow at event bars. By replacing verbal ordering, manual queuing, and cash-based payment with a scan-to-order digital experience, DrinkSync eliminates queue chaos, reduces walk-away revenue loss, and accelerates service throughput. This white paper presents the system architecture, technical design decisions, feature set, and operational model of the DrinkSync platform.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Problem Statement](#2-problem-statement)
3. [Solution Overview](#3-solution-overview)
4. [System Architecture](#4-system-architecture)
5. [Core Features](#5-core-features)
6. [Order Lifecycle Management](#6-order-lifecycle-management)
7. [Real-Time Communication](#7-real-time-communication)
8. [WhatsApp Notification System](#8-whatsapp-notification-system)
9. [Observability and Monitoring](#9-observability-and-monitoring)
10. [Security Model](#10-security-model)
11. [Technology Stack](#11-technology-stack)
12. [Deployment Architecture](#12-deployment-architecture)
13. [Testing Strategy](#13-testing-strategy)
14. [Performance Considerations](#14-performance-considerations)
15. [Future Roadmap](#15-future-roadmap)
16. [Conclusion](#16-conclusion)

---

## 1. Introduction

The live events industry generates billions in beverage revenue annually, yet the point-of-sale experience at event bars remains largely analogue. Long queues, miscommunicated orders, and slow payment processing create friction that directly impacts customer satisfaction and vendor revenue.

DrinkSync addresses this gap by providing a complete digital ordering platform purpose-built for high-volume, temporary bar environments. Customers access the system by scanning a station-specific QR code — no app installation required — and proceed through a streamlined order flow from menu browsing to pickup notification.

---

## 2. Problem Statement

Event bars face three interconnected operational challenges:

### 2.1 Queue Chaos
Traditional bar service relies on first-come-first-served verbal ordering. In high-volume environments (festivals, concerts, sporting events), this creates disorganised crowds, customer frustration, and disputes over queue position.

### 2.2 Revenue Loss from Walk-Aways
Industry estimates suggest that 15–30% of potential customers abandon queues before ordering when wait times exceed 10 minutes. Each walk-away represents direct revenue loss that compounds across multi-day events.

### 2.3 Service Bottlenecks
Verbal ordering introduces errors and repetition. Payment processing (especially cash handling) adds 30–60 seconds per transaction. These delays cascade during peak periods, creating a negative feedback loop of longer queues and more walk-aways.

### 2.4 Lack of Operational Visibility
Vendors have no real-time data on queue depth, order throughput, or peak demand patterns. This prevents proactive staffing adjustments and inventory management.

---

## 3. Solution Overview

DrinkSync eliminates these friction points through a fully digital order-to-pickup pipeline:

```
QR Scan → Menu Browse → Build Order → Checkout → Payment → Queue Position → Preparation → Pickup
```

**Key design principles:**

- **Zero-install access**: PWA accessed via QR code scan; works on any modern mobile browser
- **Station-scoped ordering**: Each physical bar station has a unique QR code, enabling distributed ordering across multiple stations at a single event
- **Asynchronous preparation**: Orders enter a digital queue upon payment, decoupling the ordering act from the preparation act
- **Real-time feedback**: Customers receive live status updates via WebSocket and optional WhatsApp notifications
- **Vendor empowerment**: Tablet-friendly dashboard gives vendors full visibility and control over their order queue

---

## 4. System Architecture

DrinkSync follows a three-tier architecture with real-time communication and observability layers:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT TIER                                   │
│                                                                       │
│  ┌──────────────────┐              ┌──────────────────────────┐      │
│  │  Customer PWA    │              │  Vendor Dashboard        │      │
│  │  (Mobile-first)  │              │  (Tablet-optimised)      │      │
│  │  React 19 + TS   │              │  React 19 + TS           │      │
│  └────────┬─────────┘              └────────────┬─────────────┘      │
│           │                                      │                    │
└───────────┼──────────────────────────────────────┼────────────────────┘
            │ REST + WebSocket                     │ REST + WebSocket
            │                                      │
┌───────────┼──────────────────────────────────────┼────────────────────┐
│           ▼            APPLICATION TIER           ▼                    │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │              Spring Boot 3.4.1 (Java 17)                    │      │
│  │                                                              │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │      │
│  │  │   REST   │  │WebSocket │  │  Order   │  │Notification│  │      │
│  │  │Controllers│  │  STOMP  │  │ Service  │  │  Service   │  │      │
│  │  └──────────┘  └──────────┘  └──────────┘  └───────────┘  │      │
│  │                                                              │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │      │
│  │  │   JWT    │  │  Flyway  │  │   JPA    │  │  WhatsApp  │  │      │
│  │  │   Auth   │  │Migrations│  │Hibernate │  │   Client   │  │      │
│  │  └──────────┘  └──────────┘  └──────────┘  └───────────┘  │      │
│  └────────────────────────────────────────────────────────────┘      │
│                              │                                         │
└──────────────────────────────┼─────────────────────────────────────────┘
                               │
┌──────────────────────────────┼─────────────────────────────────────────┐
│                    DATA TIER │                                          │
│                              ▼                                          │
│  ┌────────────────────────────────────────────────────────────┐       │
│  │              PostgreSQL 16                                   │       │
│  │                                                              │       │
│  │  stations │ orders │ order_items │ sessions │ notification_log│      │
│  │  spirits  │ mixers │ premade_items │ order_state_history     │       │
│  └────────────────────────────────────────────────────────────┘       │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                     OBSERVABILITY TIER                                  │
│                                                                        │
│  ┌──────────────┐     ┌──────────────────┐     ┌──────────────┐      │
│  │  OTel Agent  │────▶│  OTel Collector  │────▶│Elasticsearch │      │
│  │  (Java)      │     │  (gRPC/HTTP)     │     │  + Kibana    │      │
│  └──────────────┘     └──────────────────┘     └──────────────┘      │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Design Decisions

| Decision | Rationale |
|----------|-----------|
| Monolithic backend | Simplicity for MVP; event bars don't require microservice complexity |
| PWA over native app | Zero-install requirement; QR code must lead directly to usable interface |
| PostgreSQL | ACID compliance for financial transactions; robust JSON support for flexible schemas |
| WebSocket (STOMP/SockJS) | Sub-second state push; SockJS provides fallback for restrictive networks |
| Flyway migrations | Deterministic, version-controlled schema evolution |
| OpenTelemetry | Vendor-neutral observability; traces, logs, and metrics in a single pipeline |

---

## 5. Core Features

### 5.1 QR Code Station Entry

Each physical bar station is assigned a unique QR code that encodes the station identifier. Scanning the QR code opens the PWA directly in the customer's browser, pre-configured for that specific station's menu and queue.

**Benefits:**
- No app store download required
- Station-scoped menus allow different bars at the same event to offer different products
- Soft location control — customers are guided to order from their nearest station

### 5.2 Custom Drink Builder

Customers construct drinks by selecting:
1. **Spirit** — Base alcohol (e.g., vodka, gin, whiskey)
2. **Mixer** — Accompanying beverage (e.g., tonic, cola, juice)
3. **Cup Option** — Reusable cup or new disposable cup (supporting sustainability initiatives)

Each component has independent pricing and availability, managed by the vendor in real-time.

### 5.3 Pre-made Items

Ready-to-serve items (cans, bottles, pre-mixed cocktails) are available for customers who prefer speed over customisation. These items bypass the drink-building flow and go directly to cart.

### 5.4 Vendor Dashboard

A tablet-optimised interface providing:
- Real-time incoming order feed
- One-tap state transitions (PAID → PREPARING → READY)
- Menu availability toggles (mark items as sold out instantly)
- Order history and queue depth visibility
- Pickup verification with order number confirmation

### 5.5 Dark/Light Theme

Automatic theme detection with manual toggle, ensuring readability in both bright outdoor festival environments and dark indoor venues.

### 5.6 Offline Support

Service worker provides graceful offline handling — customers see a clear offline state rather than broken pages, and the app recovers automatically when connectivity returns.

---

## 6. Order Lifecycle Management

The order state machine is the core domain model of DrinkSync. All transitions are strict, sequential, and persisted with timestamps.

```
                    ┌──────────────┐
                    │    DRAFT     │
                    │ (building)   │
                    └──────┬───────┘
                           │ checkout
                    ┌──────▼───────┐
                    │  AWAITING    │
                    │  PAYMENT     │
                    └──────┬───────┘
                           │ pay
                    ┌──────▼───────┐
                    │     PAID     │──────────────┐
                    │(queue assigned)│             │
                    └──────┬───────┘              │
                           │ prepare              │ cancel
                    ┌──────▼───────┐              │
                    │  PREPARING   │              │
                    └──────┬───────┘              │
                           │ ready                │
                    ┌──────▼───────┐              │
                    │    READY     │              │
                    │(pickup window)│             │
                    └──────┬───────┘     ┌───────▼───────┐
                           │ collect     │   CANCELLED   │
                    ┌──────▼───────┐     └───────────────┘
                    │  COLLECTED   │
                    └──────────────┘
                           │ timeout
                    ┌──────▼───────┐
                    │   EXPIRED    │
                    └──────────────┘
```

### 6.1 State Transition Rules

| Transition | Trigger | Side Effects |
|-----------|---------|--------------|
| DRAFT → AWAITING_PAYMENT | Customer checkout | Order locked; items cannot be modified |
| AWAITING_PAYMENT → PAID | Payment confirmation | Queue position assigned; WebSocket push; WhatsApp notification |
| PAID → PREPARING | Vendor action | WebSocket push to customer |
| PREPARING → READY | Vendor action | Pickup window timer starts; WhatsApp notification |
| READY → COLLECTED | Vendor verification | Order complete; receipt notification |
| READY → EXPIRED | Timeout | Pickup window exceeded |
| Any pre-PREPARING → CANCELLED | Customer/vendor action | Refund initiated (future) |

### 6.2 Idempotency

Payment confirmation is idempotent — duplicate payment requests (caused by network retries or double-taps) are deduplicated using a client-provided `Idempotency-Key` header. The system returns the same response for repeated requests with the same key.

### 6.3 State History

Every state transition is recorded in the `order_state_history` table with:
- Previous state
- New state
- Timestamp
- Actor (customer session or vendor ID)

This provides a complete audit trail for dispute resolution and operational analytics.

---

## 7. Real-Time Communication

### 7.1 WebSocket Architecture

DrinkSync uses STOMP (Simple Text Oriented Messaging Protocol) over SockJS for real-time bidirectional communication:

```
Client ──── SockJS ──── STOMP ──── Spring WebSocket ──── Message Broker
```

**SockJS** provides automatic fallback to HTTP long-polling when WebSocket connections are blocked by corporate firewalls or restrictive mobile networks.

### 7.2 Topic Structure

| Topic | Subscriber | Purpose |
|-------|-----------|---------|
| `/topic/orders/{orderId}` | Customer | Individual order status updates |
| `/topic/stations/{stationId}/orders` | Vendor | All order updates for a station |

### 7.3 Delivery Guarantees

- State changes are pushed within **1 second** of persistence
- If a WebSocket connection drops, the client reconnects automatically and fetches current state via REST
- The system is designed for eventual consistency — WebSocket is an optimisation, not the source of truth

---

## 8. WhatsApp Notification System

DrinkSync integrates with the WhatsApp Business Cloud API to provide proactive order notifications via the world's most widely used messaging platform.

### 8.1 Architecture

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Order State  │────▶│  Notification    │────▶│  WhatsApp Cloud  │
│ Transition   │     │  Service (async) │     │  API (Meta)      │
└──────────────┘     └──────────────────┘     └──────────────────┘
                              │                         │
                              ▼                         ▼
                     ┌──────────────────┐     ┌──────────────────┐
                     │ Notification Log │◀────│ Webhook Callback │
                     │ (PostgreSQL)     │     │ (delivery status)│
                     └──────────────────┘     └──────────────────┘
```

### 8.2 Opt-In Flow

WhatsApp notifications are strictly opt-in. During checkout, customers may:
1. Provide their phone number
2. Explicitly consent to receive order notifications

No messages are sent without explicit consent, complying with WhatsApp Business Policy and data protection regulations.

### 8.3 Notification Triggers

| Order State | Message Type | Content |
|-------------|-------------|---------|
| PAID | `order_confirmed` | Order confirmation with queue position and estimated wait |
| READY | `order_ready` | Pickup notification with order number and collection point |
| COLLECTED | `order_receipt` | Digital receipt with order summary |

### 8.4 Reliability Features

- **Asynchronous dispatch**: Notifications are sent on a dedicated thread pool (`notificationExecutor`) and never block the order flow
- **Fire-and-forget semantics**: Notification failures are logged but never propagate to the customer's order experience
- **Deduplication**: Each order + message type combination is sent at most once (checked against `sent`, `delivered`, `read` statuses)
- **Rate limiting**: Configurable send rate (default: 50 messages/second) via `RateLimitedSendQueue`
- **Delivery tracking**: Webhook callbacks update notification status (sent → delivered → read → failed)
- **Signature verification**: Inbound webhooks are validated using HMAC-SHA256 with the app secret

### 8.5 Inbound Message Handling

When a customer sends a message to the WhatsApp Business number, the system:
1. Looks up active orders (PAID, PREPARING, READY) by phone number
2. Sends an auto-reply with the status of up to 5 most recent orders
3. Provides a self-service support experience without human intervention

---

## 9. Observability and Monitoring

### 9.1 OpenTelemetry Integration

DrinkSync uses OpenTelemetry (OTel) for unified observability across three signal types:

| Signal | Purpose | Storage |
|--------|---------|---------|
| **Traces** | Request flow across services; latency breakdown | Elasticsearch (`drinksync-traces`) |
| **Logs** | Structured application logs with trace correlation | Elasticsearch (`drinksync-logs`) |
| **Metrics** | JVM stats, HTTP request rates, queue depth | Elasticsearch (`drinksync-metrics`) |

### 9.2 Pipeline

```
Application (OTel Java Agent)
    │
    │ gRPC (port 4317)
    ▼
OTel Collector
    │ batch processing + resource enrichment
    ▼
Elasticsearch 8.15
    │
    ▼
Kibana (visualisation + alerting)
```

### 9.3 Key Dashboards

- **Order throughput**: Orders per minute by station, with state distribution
- **Latency percentiles**: P50/P95/P99 for order creation, payment, and state transitions
- **Error rates**: Failed payments, WebSocket disconnections, notification failures
- **Queue health**: Average time in each state, pickup window compliance

---

## 10. Security Model

### 10.1 Authentication

| Actor | Mechanism | Details |
|-------|-----------|---------|
| Customer | Session-based | `X-Session-Id` header; anonymous session created on QR scan |
| Vendor | JWT Bearer token | HS256-signed; 12-hour expiry; issued on login |

### 10.2 Authorisation

- Customer endpoints require a valid session ID
- Vendor endpoints require a valid JWT with appropriate claims
- State transitions are role-restricted (customers can cancel; only vendors can advance to PREPARING/READY)

### 10.3 Data Protection

- Phone numbers are stored only with explicit consent
- WhatsApp API credentials are injected via environment variables (never committed to source)
- Webhook signatures are verified using HMAC-SHA256 before processing
- Database credentials are managed through Docker secrets in production

### 10.4 Input Validation

- Phone numbers are validated against E.164 format before storage
- All request bodies are validated with strict Jackson deserialization (`fail-on-unknown-properties: true`)
- Idempotency keys must be valid UUIDs

---

## 11. Technology Stack

| Layer | Technology | Version | Justification |
|-------|-----------|---------|---------------|
| **Runtime** | Java | 17 (LTS) | Long-term support; modern language features (records, sealed classes, pattern matching) |
| **Framework** | Spring Boot | 3.4.1 | Production-ready; extensive ecosystem; WebSocket support |
| **Database** | PostgreSQL | 16 | ACID compliance; excellent JSON support; proven at scale |
| **Migrations** | Flyway | — | Version-controlled, deterministic schema evolution |
| **Auth** | jjwt | 0.12.6 | Industry-standard JWT implementation |
| **Frontend** | React | 19 | Component model; concurrent features; large ecosystem |
| **Type Safety** | TypeScript | — | Compile-time error detection; improved developer experience |
| **Build Tool** | Vite | — | Fast HMR; optimised production builds |
| **Styling** | CSS Custom Properties | — | No framework dependency; full control; theme support via variables |
| **Routing** | react-router-dom | 7 | Declarative routing; nested layouts |
| **Real-time** | STOMP/SockJS | — | Protocol-level messaging with automatic fallback |
| **Observability** | OpenTelemetry | — | Vendor-neutral; unified traces/logs/metrics |
| **Search/Analytics** | Elasticsearch + Kibana | 8.15 | Full-text search; powerful visualisation |
| **Containerisation** | Docker Compose | — | Reproducible environments; single-command deployment |

---

## 12. Deployment Architecture

### 12.1 Container Topology

```
┌─────────────────────────────────────────────────────────┐
│                    Docker Compose                         │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │ Frontend │  │ Backend  │  │     PostgreSQL        │  │
│  │  :5173   │  │  :8080   │  │      :5432           │  │
│  └──────────┘  └──────────┘  └──────────────────────┘  │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │  OTel    │  │  Elastic │  │      Kibana          │  │
│  │Collector │  │  search  │  │      :5601           │  │
│  │:4317/4318│  │  :9200   │  │                      │  │
│  └──────────┘  └──────────┘  └──────────────────────┘  │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 12.2 Startup Order

1. **PostgreSQL** starts first (health check: `pg_isready`)
2. **Backend** starts after PostgreSQL is healthy (runs Flyway migrations on boot)
3. **Frontend** starts independently (no backend dependency for dev server)
4. **Elasticsearch** starts independently (health check: cluster health endpoint)
5. **OTel Collector** starts after Elasticsearch is healthy
6. **Kibana** starts after Elasticsearch is healthy

### 12.3 Development Workflow

Two operational modes support different development needs:

| Command | Services | Use Case |
|---------|----------|----------|
| `make up-app` | PostgreSQL, Backend, Frontend | Fast iteration; no observability overhead |
| `make up` | All services | Full-stack with monitoring |

Hot-reload is enabled for both frontend (Vite HMR) and backend (Spring DevTools with volume mounts).

---

## 13. Testing Strategy

DrinkSync employs a multi-layered testing approach:

### 13.1 Test Pyramid

```
         ┌───────────┐
         │    E2E    │  Playwright (critical user journeys)
         │  (few)    │
         ├───────────┤
         │Integration│  Testcontainers (real PostgreSQL)
         │ (moderate)│
         ├───────────┤
         │   Unit    │  JUnit 5 / Vitest (fast, isolated)
         │  (many)   │
         └───────────┘
```

### 13.2 Backend Testing

| Type | Framework | Scope |
|------|-----------|-------|
| Unit tests | JUnit 5 | Service logic, validators, serializers |
| Property-based tests | jqwik | Phone validation, template serialization, state transitions |
| Integration tests | Testcontainers | Repository queries, Flyway migrations, full notification flow |

### 13.3 Frontend Testing

| Type | Framework | Scope |
|------|-----------|-------|
| Unit tests | Vitest | Component rendering, hooks, API clients |
| Component tests | Vitest + Testing Library | User interactions, form validation |
| E2E tests | Playwright | Full order flow, vendor dashboard operations |

### 13.4 Performance Testing

**Gatling** load tests simulate concurrent ordering scenarios:
- Peak load simulation (hundreds of simultaneous orders)
- WebSocket connection scaling
- Payment endpoint throughput under contention

### 13.5 Property-Based Testing

Critical invariants are verified using property-based testing (jqwik for Java, fast-check for TypeScript):
- Phone number validation accepts all valid E.164 formats and rejects invalid ones
- Template serialization round-trips correctly for arbitrary order data
- State transitions never skip states regardless of input sequence
- Webhook signature verification rejects all tampered payloads

---

## 14. Performance Considerations

### 14.1 Database

- **Connection pooling**: HikariCP with 10 max connections, 5 minimum idle
- **Indexing**: Orders indexed by station + state for vendor dashboard queries
- **Pagination**: Station order lists support cursor-based pagination

### 14.2 Real-Time

- **WebSocket state push**: < 1 second from persistence to client delivery
- **SockJS fallback**: Automatic degradation to long-polling on restricted networks
- **Connection management**: Heartbeat-based keepalive with automatic reconnection

### 14.3 Notifications

- **Async dispatch**: Dedicated thread pool prevents notification latency from affecting order flow
- **Rate limiting**: Configurable send rate (50 msg/s default) prevents API throttling
- **Batch processing**: OTel collector batches telemetry (5s window, 1024 batch size) to reduce network overhead

### 14.4 Frontend

- **PWA caching**: Service worker caches static assets for instant subsequent loads
- **Code splitting**: Vite produces optimised chunks; vendor and customer routes are lazy-loaded
- **CSS custom properties**: Zero-runtime theme switching without CSS-in-JS overhead

---

## 15. Future Roadmap

### Phase 2: Real Payments
- Integration with South African payment providers (PayShap Request, Ozow, Yoco)
- PCI DSS compliance considerations
- Refund automation for cancelled orders

### Phase 3: Push Notifications
- Web Push API integration for customers who decline WhatsApp opt-in
- Vendor push notifications for high-priority events (queue overflow, low stock)

### Phase 4: Multi-Vendor Support
- Multiple vendors per event with independent stations
- Event-level administration and reporting
- Revenue splitting and settlement

### Phase 5: Analytics and Optimisation
- Demand forecasting based on historical order patterns
- Smart queue optimisation (reordering based on preparation complexity)
- Customer behaviour analytics (popular combinations, peak times, conversion rates)
- A/B testing framework for menu layouts and pricing

### Phase 6: Scale and Resilience
- Horizontal scaling with container orchestration (Kubernetes)
- Event-driven architecture with message queues for peak load absorption
- Multi-region deployment for large-scale events
- Offline-first vendor dashboard with sync-on-reconnect

---

## 16. Conclusion

DrinkSync transforms the event bar experience by replacing analogue queuing with a digital-first ordering platform. The system's architecture — built on proven technologies (Spring Boot, PostgreSQL, React) with modern operational practices (OpenTelemetry, Docker, property-based testing) — provides a solid foundation for scaling from single-station deployments to multi-venue event operations.

The recent addition of WhatsApp notifications extends the platform's reach beyond the browser, meeting customers on their preferred communication channel while maintaining strict opt-in compliance and operational resilience.

By digitising the order-to-pickup flow, DrinkSync delivers measurable value to both sides of the bar:
- **Customers** gain predictable wait times, order accuracy, and real-time visibility
- **Vendors** gain increased throughput, reduced walk-aways, and operational data for continuous improvement

---

## Appendix A: API Surface Summary

| Category | Endpoints | Auth |
|----------|-----------|------|
| Stations & Menu | 2 | None (public) |
| Orders (Customer) | 6 | Session ID |
| Orders (Vendor) | 2 | JWT |
| Menu Management | 6 | JWT |
| Auth | 1 | None |
| Sessions | 1 | None |
| Webhooks | 1 | Signature verification |

## Appendix B: Database Schema (Key Tables)

| Table | Purpose |
|-------|---------|
| `station` | Physical bar locations with QR code identifiers |
| `spirit_item` | Spirit menu items with pricing and availability |
| `mixer_item` | Mixer menu items with pricing and availability |
| `premade_item` | Pre-made drink items with pricing and availability |
| `session` | Anonymous customer sessions with optional phone number |
| `orders` | Order records with state, queue position, and WhatsApp opt-in |
| `order_item` | Individual items within an order |
| `order_state_history` | Audit trail of all state transitions |
| `notification_log` | WhatsApp notification delivery tracking |
| `idempotency_key` | Payment deduplication records |
| `auth_attempt` | Vendor login attempt tracking |

## Appendix C: Environment Configuration

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection string |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (docker/test) |
| `WHATSAPP_ENABLED` | Enable/disable WhatsApp notifications |
| `WHATSAPP_PHONE_NUMBER_ID` | Meta Business phone number ID |
| `WHATSAPP_ACCESS_TOKEN` | WhatsApp Cloud API access token |
| `WHATSAPP_VERIFY_TOKEN` | Webhook verification token |
| `WHATSAPP_APP_SECRET` | HMAC signature verification secret |
| `OTEL_SERVICE_NAME` | OpenTelemetry service identifier |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTel collector gRPC endpoint |
| `VITE_API_BASE_URL` | Frontend → Backend API URL |
| `VITE_WS_URL` | Frontend → WebSocket URL |

---

*© 2026 Mangaliso Mtembu. All rights reserved.*
