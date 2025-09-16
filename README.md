# Basic Kotlin Exchange API (VALR Assessment)

A small, **in-memory limit order book** with a Kotlin HTTP API.  
It exposes endpoints to place **limit orders**, view the **order book**, and read **recent trades**.  
Built with **Kotlin + Vert.x**, tested with **JUnit 5**, and packaged via **Docker**.

> Status: Basic Orderbook + HMAC auth for POST /api/orders

---

## Table of Contents
- [Quick Start](#quick-start)
- [Project Goals](#project-goals)
- [Architecture](#architecture)
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)
- [Authentication](#authentication)
- [API Docs](#api-docs)
- [Roadmap / Extensions](#roadmap--extensions)
- [Notes / Assumptions](#notes--assumptions)

---

## Quick Start

### Run locally (WSL/Linux/macOS)
```bash
./gradlew :api:run
# in another shell
curl -s http://localhost:8080/healthz
# -> ok
```

### Run tests
```bash
./gradlew test
# or module-specific:
./gradlew :engine:test
./gradlew :api:test
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

### Multi-module Gradle Project:
```bash
basic-kotlin-exchange-api/
├── api/        # Runnable Vert.x server (HTTP routing, JSON, OpenAPI)
└── engine/     # Pure domain & matching logic (no HTTP)
```

- **API** depends on **engine**.
- **Engine** contains domain models (Order, Trade, etc.) and the OrderBook implementation.
- API DTOs live with the HTTP layer (in `api`).

### Why this shape?

- Engine stays **framework-agnostic** so its easy to test and reuse.
- HTTP layer is a thin adapter.

## Design Decisions & Trade-offs

### Where to put shared models?
- Considered a separate `common/` module; decided not to add it to avoid over-modularizing a small assessment.
- **Decision**: domain models live in `engine.model`; HTTP DTOs live with the API (currently `api`).

### Matching & priority
- **Price–time priority**: match best price first; within a price level, FIFO (oldest first).
- **Execution price**: trade executes at the maker’s resting price (realistic exchange behavior).
- **Partial fills**: supported; remaining quantity rests or continues matching.

### Why these data structures?
- **TreeMap** per side (bids/asks) for **O(log M)** best-price lookup (M = price levels).
- **ArrayDeque** at each price level for **O(1)** FIFO dequeue of resting orders.
- This beats linear scans and avoids the complexity of custom heaps/skip lists for this scope.

### Precision & validation
- Internally we’ll use **BigDecimal** for price/quantity (no float/double) to avoid rounding errors.
- Basic validation: non-negative price/quantity; symbol known; simple scales.

## Authentication

- Mutating endpoints (currently `POST /api/orders/:symbol`) require VALR‑style HMAC auth headers:
  - `X-VALR-API-KEY`
  - `X-VALR-TIMESTAMP` (Unix millis as string)
  - `X-VALR-SIGNATURE` (hex of HMAC‑SHA512 over `timestamp + verb + path + body` using your API secret)
- Content type enforcement: `content-type: application/json` is required for `POST/PUT/PATCH` or the API returns `403`.
- Key management (dev):
  - By default the server enables a dev key: `API_KEY=test-key`, `API_SECRET=test-secret`.
  - You can override via environment variables `API_KEY` and `API_SECRET`.

### Example: sign and place an order (bash)

Set variables and compute the signature with `openssl`:

```bash
API_KEY=${API_KEY:-test-key}
API_SECRET=${API_SECRET:-test-secret}
TS=$(date +%s%3N)                 # unix millis
VERB=POST
PATH=/api/orders/BTCZAR           # path only (no host, no query)
BODY='{"side":"BUY","price":"100","quantity":"0.01"}'

SIG=$(printf "%s" "$TS$VERB$PATH$BODY" \
  | openssl dgst -sha512 -hmac "$API_SECRET" -binary \
  | xxd -p -c 256)

curl -sS -X POST "http://localhost:8080$PATH" \
  -H "content-type: application/json" \
  -H "X-VALR-API-KEY: $API_KEY" \
  -H "X-VALR-TIMESTAMP: $TS" \
  -H "X-VALR-SIGNATURE: $SIG" \
  -d "$BODY"
```

Expected response: JSON object containing the `order` and any resulting `trades`.

### Docker with custom keys

```bash
docker run -p 8080:8080 \
  -e API_KEY=my-key -e API_SECRET=my-secret \
  exchange-api
```

## API Docs

The API serves `openapi.yaml` and a Swagger UI page under `/docs` using Vert.x Web and the OpenAPI components.

## Roadmap / Extensions

- **Cancel/Amend**: add cancel and replace flows.
- **Order time-in-force**: IOC/FOK support.
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
