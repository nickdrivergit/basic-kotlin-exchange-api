# Basic Kotlin Exchange API (VALR Assessment)

A small, **in-memory limit order book** with a Kotlin HTTP API.  
It exposes endpoints to place **limit orders**, view the **order book**, and read **recent trades**.  
Built with **Kotlin + Ktor**, tested with **JUnit 5**, and packaged via **Docker**.

> Status: scaffolding complete (`/healthz`), engine in progress.

---

## Table of Contents
- [Quick Start](#quick-start)
- [Project Goals](#project-goals)
- [Architecture](#architecture)
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)

- [Roadmap / Extensions](#roadmap--extensions)
- [Notes / Assumptions](#notes--assumptions)

---

## Quick Start

### Run locally (WSL/Linux/macOS)
```bash
./gradlew :app:run
# in another shell
curl -s http://localhost:8080/healthz
# -> ok
```

### Run tests
```bash
./gradlew test
# or module-specific:
./gradlew :engine:test
./gradlew :app:test
```

### Build & Run with Docker
```bash
docker build -t valr-orderbook .
docker run -p 8080:8080 valr-orderbook
curl -s http://localhost:8080/healthz
```

---

## Project Goals

- Implement a working in-memory order book with price–time priority and order matching.
- Provide an HTTP API to:
    - Get order book (aggregated price levels).
    - Submit limit orders.
    - Get recent trades.
- Include unit tests for the engine and light integration tests for the API.
- Favor clarity and extensibility over premature micro-optimizations.
- Ship a Dockerized runnable service.

## Architecture

### Multi-module Grade Project:
```bash
basic-kotlin-exchange-api/
├── app/        # Runnable Ktor server (HTTP, DI, config)
├── engine/     # Pure domain & matching logic (no HTTP)
└── (api/)      # [Optional] If split later: DTOs + route wiring
```

- **App** depends on **engine**.
- **Engine** contains domain models (Order, Trade, etc.) and the OrderBook implementation.
- API DTOs live with the HTTP layer (in app for now). If they grow, we’ll extract an **api** module.

### Why this shape?

- Engine stays **framework-agnostic** so its easy to test and reuse.
- HTTP layer is a thin adapter
- Keeps a path open to split further (e.g. api/ as shown above) without refactoring core logic.

## Design Decisions & Trade-offs

### Where to put shared models?
- Considered a separate `common/` module; decided not to add it to avoid over-modularizing a small assessment.
- **Decision**: domain models live in `engine.model`; HTTP DTOs live with the API (currently app).

### Matching & priority
- **Price–time priority**: match best price first; within a price level, FIFO (oldest first).
- **Execution price**: trade executes at the maker’s resting price (realistic exchange behavior).
- **Partial fills**: supported; remaining quantity rests or continues matching.

### Why these data structures?
- **TreeMap** per side (bids/asks) for **O(log M)** best-price lookup (M = price levels).
- **ArrayDeque** at each price level for **O(1)** FIFO dequeue of resting orders.
- This beats linear scans and avoids the complexity of custom heaps/skip lists for this scope.

### Precision & validation
- Internally we’ll use **BigDecimal** for price/quantity (no float/double) to avoid rounding error.
- Basic validation: non-negative price/quantity; symbol known; simple scales.

## API Docs (planned)

We’ll expose auto-generated API docs via Ktor’s Swagger/OpenAPI plugin (e.g. /openapi) once endpoints are in place.

## Roadmap / Extensions

- **Auth**: simple HMAC/JWT for POST endpoints.
- **Cancel/Amend**: add cancel and replace flows.
- **Order time-in-force**: IOC/FOK support (document assumptions).
- **Depth parameter**: GET /orderbook?depth=20.
- **Metrics/Health**: /metrics, /readyz (Micrometer/Prometheus).
- **Alternate HTTP runtime**: Vert.x adapter to showcase flexibility.
- **Persistence (out of scope)**: optional store for trades/orders.

## Notes / Assumptions
- In-memory only; no persistence across restarts.
- Single symbol to start (BTCZAR), but structure supports many.
- BigDecimal for price/qty; simple validation; optional normalization for display.


Sections to add (maybe)
- [Data Structures](#data-structures)
- [API (Planned Shape)](#api-planned-shape)
- [Testing Strategy](#testing-strategy)
- [Local Development](#local-development)
- [Docker](#docker)