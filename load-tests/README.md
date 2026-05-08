# Smart Event Bar — Gatling Load Tests

Load tests for the Smart Event Bar backend, validating performance under realistic event-day traffic patterns.

## Prerequisites

- Java 17+
- Maven 3.8+
- A running Smart Event Bar backend instance with seeded test data
- PostgreSQL database with test stations and menu items

## Test Data Setup

Before running load tests, ensure the target environment has:

- **10 stations** (IDs 1–10) with menu items and access codes
- **5 spirit items, 5 mixer items, 5 premade items** per station (IDs 1–5)
- Default vendor access code: `ABC123` (configurable via `-DaccessCode=...`)

## Running Tests

### Run a specific simulation

```bash
# Baseline profile (50 users)
mvn gatling:test -Dgatling.simulationClass=com.smarteventbar.loadtest.scenarios.CustomerOrderFlowSimulation

# Peak profile (200 users)
mvn gatling:test -Dgatling.simulationClass=com.smarteventbar.loadtest.scenarios.CustomerOrderFlowSimulation -DloadProfile=peak

# Stress profile (500 users)
mvn gatling:test -Dgatling.simulationClass=com.smarteventbar.loadtest.scenarios.CustomerOrderFlowSimulation -DloadProfile=stress
```

### Available simulations

| Simulation | Description |
|-----------|-------------|
| `CustomerOrderFlowSimulation` | Full customer journey: scan → browse → order → pay → track |
| `ConcurrentPaymentsSimulation` | Multiple customers paying simultaneously at the same station |
| `WebSocketFanOutSimulation` | Many WebSocket subscribers receiving vendor state transitions |
| `MenuBrowsingSpikeSimulation` | Sudden burst of menu browsing requests (event start) |
| `MixedStationLoadSimulation` | Mixed traffic across 5–10 stations with customers, browsers, and vendors |

### Configuration

Override defaults via system properties:

| Property | Default | Description |
|----------|---------|-------------|
| `baseUrl` | `http://localhost:8080` | Backend base URL |
| `wsUrl` | `ws://localhost:8080/ws` | WebSocket URL |
| `accessCode` | `ABC123` | Default vendor access code |
| `loadProfile` | `baseline` | Load profile: `baseline`, `peak`, or `stress` |

Example:

```bash
mvn gatling:test \
  -DbaseUrl=http://staging.example.com:8080 \
  -DloadProfile=peak \
  -Dgatling.simulationClass=com.smarteventbar.loadtest.scenarios.MixedStationLoadSimulation
```

## Load Profiles

| Profile | Concurrent Users | Ramp-Up | Duration |
|---------|-----------------|---------|----------|
| Baseline | 50 | 30s | 5 min |
| Peak | 200 | 60s | 5 min |
| Stress | 500 | 120s | 5 min |

## Assertions

All simulations enforce these assertions:

- **p95 response time < 500ms** (300ms for menu browsing)
- **Error rate < 1%**
- **WebSocket delivery < 1s** (fan-out scenario)

## Reports

After each run, Gatling generates an HTML report in `target/gatling/`. Open the `index.html` file in a browser to view detailed results including response time distributions, throughput graphs, and error analysis.

## Requirements Validated

- **Req 4.4**: Queue positions assigned based on chronological PAID order sequence (under load)
- **Req 5.1**: WebSocket delivers state changes within 1 second (under load)
- **Req 20.2**: Operational metrics (response times, error rates)
