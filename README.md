# Basic Kotlin Exchange API (VALR Assessment)

A small, **in-memory limit order book** with a Kotlin HTTP API.  
It exposes endpoints to place **limit orders**, view the **order book**, and read **recent trades**.  
Built with **Kotlin + Vert.x**, tested with **JUnit 5**, and packaged via **Docker**.

> Status: Engine + API stable. Multi‑symbol in‑memory order books, HMAC auth for POST /api/orders, order book snapshot and recent trades endpoints, CI and JMH benchmarks in place.

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
./gradlew :domain:test
./gradlew :api:test
```

### Build & Run with Docker
```bash
docker build -t exchange-api .
docker run -p 8080:8080 exchange-api
curl -s http://localhost:8080/healthz
```

---

## Project Goals

- Implement a working in-memory order book with price–time priority and order matching.
- Provide an HTTP API to:
    - Get order book (aggregated price levels).
    - Submit limit orders (GTC/IOC/FOK).
    - Get recent trades.
- Include unit tests for the engine and light integration tests for the API.
- Favor clarity and extensibility over premature micro-optimizations.
- Ship a Dockerized runnable service.

## Architecture

### Multi-module Gradle Project (DDD):
```bash
basic-kotlin-exchange-api/
├── api/                         # HTTP adapter (Vert.x), auth, DTOs, OpenAPI
├── domain/                      # Pure domain model + core aggregate (OrderBook)
├── application/                 # Use-cases/services orchestrating domain
├── adapters/
│   └── persistence-inmemory/    # In-memory store implementing application ports
└── perf/                        # JMH microbenchmarks
```

- api -> application -> domain
- adapters:* -> application (+ domain)
- perf -> application (+ domain)

### Why this shape?

- Domain stays **framework-agnostic** so it’s easy to test and reuse.
- Application orchestrates use-cases via ports; adapters are replaceable.
- HTTP layer is a thin adapter.

## Design Decisions & Trade-offs

### Where to put shared models?
- Considered a separate `common/` module; decided not to add it to avoid over-modularizing a small assessment.
- **Decision**: domain models live in `com.valr.domain.model`; HTTP DTOs live with the API (currently `api`).

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

### Time In Force

- Supported values: `GTC` (default), `IOC`, `FOK`.
- Request field: add `timeInForce` to the order body.
- Semantics:
  - `GTC`: match immediately, rest remainder on the book.
  - `IOC`: match immediately, cancel any remainder (do not rest).
  - `FOK`: if full quantity cannot be matched immediately, cancel the entire order (no trades).

Example body with IOC:

```json
{ "side": "BUY", "price": "100", "quantity": "0.5", "timeInForce": "IOC" }
```

### Docker with custom keys

```bash
docker run -p 8080:8080 \
  -e API_KEY=my-key -e API_SECRET=my-secret \
  exchange-api
```

## API Docs

The API serves `openapi.yaml` and a Swagger UI page under `/docs` using Vert.x Web and the OpenAPI components.

## Roadmap / Extensions

- Cancel/Replace: order cancel and amend flows.
- Metrics/Readiness: `/metrics`, `/readyz` with Micrometer/Prometheus.
- Idempotency/Rate limits: headers and basic throttling on POST.
- Pagination/Filters: trade history pagination, symbol filters, limits.
- Persistence adapter: pluggable storage (e.g., Postgres/Redis) for orders/trades.
- OpenAPI polish: richer schemas, examples, and error models.
- Alternate HTTP runtime (optional): e.g., Ktor to demonstrate adapter swap.

### Performance (JMH)

- Run: `./gradlew :perf:jmh` (or `-PprodJmh` if configured)
- Reports: `perf/build/reports/jmh/`

## Notes / Assumptions
- In-memory only; no persistence across restarts.
- Multi-symbol supported (per-symbol books via in-memory store).
- BigDecimal for price/qty; input validated at API boundary; engine enforces invariants.
- Default port `8080`.
- HMAC signature uses `timestamp + verb + path + body` (path excludes host and query).
