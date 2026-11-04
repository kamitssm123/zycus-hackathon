# ARCHITECTURE.md

## 1. System shape

Four containers, one Docker network, one command to start.

```
                 ┌──────────────────────────────────────────────┐
  browser  ────► │  web   nginx :80  (prod)  /  Vite :5173 (dev)│
                 │        serves SPA, proxies /api ──────────┐  │
                 └──────────────────────────────────────────┼──┘
                                                            │ same-origin
                 ┌──────────────────────────────────────────▼──┐
                 │  api   Spring Boot :8080                    │
                 │        api → service → domain               │
                 └───────┬──────────────────────────┬──────────┘
                         │ JDBC                     │ RESP
                 ┌───────▼────────┐        ┌────────▼────────┐
                 │ postgres :5432 │        │  redis :6379    │
                 │ pgdata volume  │        │  cache/ephemeral│
                 └────────────────┘        └─────────────────┘
```

The browser only ever talks to `web`. `/api/*` is proxied to the `api` container — by Vite's dev
proxy in development, by nginx in production. Consequence: **the frontend never needs an absolute
API URL and never hits CORS in the demo path.** The backend's CORS config exists only for the case
where someone runs `npm run dev` outside Docker against a containerised API.

Service discovery is Docker DNS: `postgres`, `redis`, `api` resolve by service name. Nothing is
hardcoded to localhost inside a container.

## 2. Request lifecycle

A `POST /api/items` from the browser:

1. **nginx / Vite proxy** forwards to `api:8080`, preserving the path.
2. **`RequestIdFilter`** (order 1) stamps `X-Request-Id` — reused if the client sent one, else
   generated — into SLF4J MDC and onto the response.
3. **`RequestLoggingFilter`** (order 2) times the request and logs `METHOD PATH -> STATUS (Nms)`.
   `/actuator/*` is excluded so health polling doesn't drown the log.
4. **`ItemController`** — `@Valid` triggers Bean Validation on the request record. Failures throw
   before any business code runs.
5. **`ItemService`** — opens the transaction, applies rules, calls the repository, maps to a response
   record. `open-in-view: false`, so nothing lazy escapes this boundary.
6. **`GlobalExceptionHandler`** catches anything thrown and emits the single `ApiError` shape.
7. **`api/client.ts`** in the browser throws `ApiError` on non-2xx; React Query surfaces it;
   `ErrorBanner` renders message + `requestId`.

That last link is the point: a user-visible error carries the id that appears in the server log line.
`make api-logs | grep a1b2c3d4` goes straight from symptom to cause — the single highest-leverage
debugging affordance in a 3-hour build.

## 3. Backend layering

```
com.zycus.hackathon
├── Application.java
├── config/           CorsConfig, JacksonConfig, JpaConfig, OpenApiConfig   (beans only)
├── common/
│   ├── dto/          ApiError, PageResponse                 (the wire contract)
│   ├── exception/    ApiException + subclasses, GlobalExceptionHandler
│   └── web/          RequestIdFilter, RequestLoggingFilter
├── health/           HealthController   (cheap liveness for the UI badge)
└── example/          reference vertical slice — api / service / domain
```

Dependencies flow strictly `api → service → domain`. Packages are organised **by feature, then by
layer** — a feature is one folder, deletable in one move. The alternative (top-level `controllers/`,
`services/`, `repositories/`) scatters a feature across the tree and makes parallel work on two
features collide constantly. At hackathon speed, that matters more than it does normally.

`example/` is a working CRUD slice that exists to be copied and then deleted.

## 4. Frontend layering

```
src/
├── api/        client.ts (the only fetch), types.ts (mirrors backend DTOs), items.ts
├── hooks/      React Query hooks — the only consumers of api/*
├── features/   feature-scoped components
├── components/ Layout, ErrorBanner, Spinner
├── pages/      route components, composition only
└── lib/        env.ts — the only reader of import.meta.env
```

Two chokepoints by design: **all HTTP goes through `api/client.ts`**, **all config through
`lib/env.ts`**. Changing auth headers, base URL, or error handling is a one-file edit, and a missing
env var fails in one obvious place instead of as an `undefined` inside a fetch.

React Query owns server state (caching, refetch, loading/error flags, invalidation after mutations).
`useState` is for form input only. There is no Redux and no global store — in a 3-hour build, server
state is nearly all the state there is.

## 5. Data

Postgres 16, schema owned entirely by **Flyway**. `spring.jpa.hibernate.ddl-auto=validate` — Hibernate
is never allowed to mutate the schema, so the app refuses to start on a drift rather than silently
corrupting data mid-demo. Migrations are append-only (`V1__init.sql`, `V2__…`).

Entity auditing (`@CreatedDate`, `@LastModifiedDate`) is on via `JpaConfig`.

Redis is wired but unused by application code — present for caching, rate limiting, or ephemeral
state if the problem statement calls for it. It costs one container and zero code; retrofitting it
under time pressure costs much more.

## 6. Environments

| | dev (`make up`) | prod-shaped (`make prod`) |
|---|---|---|
| frontend | Vite dev server :5173, HMR over bind mount | nginx :3000 serving static bundle |
| backend | `mvn spring-boot:run` + devtools, bind-mounted `src/` | fat jar on JRE-alpine, non-root |
| compose | `docker-compose.yml` + `docker-compose.override.yml` | `-f docker-compose.yml` only |

The override file is the *only* difference. Both run the same images from the same Dockerfiles;
dev and prod differ by build target, not by a parallel configuration universe. Deleting the override
file yields production.

Dockerfiles are multi-stage with a shared `deps` layer, so a source change rebuilds without
re-resolving dependencies — the difference between a 10-second and a 3-minute edit loop.

Compose `depends_on` uses `condition: service_healthy`, so the API never starts against a Postgres
that isn't accepting connections — removing the most common "it crashed on first boot" demo failure.

## 7. Observability

- `GET /api/health` — cheap, no dependency checks, drives the green/red badge in the UI header.
- `GET /actuator/health` — deep: Postgres and Redis. `/actuator/health/readiness` backs the
  container healthcheck.
- `/actuator/metrics`, `/actuator/info` exposed; everything else closed.
- `GET /swagger-ui.html` — live API docs, useful for showing endpoints during a demo without a
  frontend for them yet.
- Logs: `HH:mm:ss.SSS LEVEL [requestId] logger - message` on stdout, Docker-captured.

## 8. Security posture (and its limits)

Present: non-root container user, no stack traces to clients, env-driven secrets with `.env`
gitignored, parameterised queries via JPA, explicit CORS allow-list, actuator surface minimised.

**Deliberately absent: authentication and authorisation.** Every endpoint is public. This is a
3-hour demo on a local network; adding Spring Security costs ~30 minutes and would block the UI with
login flows that demonstrate nothing. If auth is actually required by the problem statement, the
intended cut is a single `OncePerRequestFilter` resolving a header to a user — not full Spring
Security. See DECISIONS.md D-06.

## 9. Scaling path (what we'd do with more than 3 hours)

- Testcontainers instead of H2, so tests run on real Postgres (D-07).
- Spring Security + JWT.
- Redis actually used: cache `@Cacheable` reads, rate-limit writes.
- Structured JSON logging + OpenTelemetry traces keyed on the existing `requestId`.
- Split `web` and `api` deployments; today they ship together.
