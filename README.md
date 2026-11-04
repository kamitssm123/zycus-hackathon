# SlotMind — AI Appointment Re-Scheduling Engine

When a provider goes OFFLINE or a slot is CANCELLED, SlotMind finds every affected appointment by
itself, asks an LLM for the best rebooking, and queues a suggestion for the front desk. A human
confirms with the patient; the system never books silently.

**Docs:** [**ADR.md**](ADR.md) · [ARCHITECTURE.md](ARCHITECTURE.md) · [DECISIONS.md](DECISIONS.md) · [CLAUDE.md](CLAUDE.md)

## The demo, in one command and one click

```bash
cp .env.example .env     # add LLM_API_KEY
make up
```

Then, at <http://localhost:5173>:

1. **Take Dr. Sarah Chen offline.** One click. That is the only human action.
2. **Watch.** Within a second, two suggestions appear — for John Martinez and Emily Watson — with
   the AI's reasoning written to be read aloud to a patient. Nobody clicked anything.
3. **Accept one.** The appointment moves, the old slot reopens and the new one books, atomically.

Or entirely from the terminal:

```bash
curl -X PATCH localhost:8080/api/providers/PRV-001/status \
     -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}'   # returns immediately
sleep 2
curl 'localhost:8080/api/suggestions?status=PENDING'                 # 2 suggestions, unprompted
curl -X PATCH localhost:8080/api/suggestions/SGT-001 \
     -H 'Content-Type: application/json' -d '{"status":"ACCEPTED"}'
curl localhost:8080/api/slots                                        # old OPEN, new BOOKED
```

> If another project on your machine is using port 5173, set `WEB_DEV_PORT` in `.env`.

## Endpoints

| | |
|---|---|
| `POST /api/appointments` | book onto an open slot |
| `GET /api/appointments?status=` | filterable list |
| `PATCH /api/providers/{id}/status` | **OFFLINE fires the agentic loop** |
| `PATCH /api/slots/{id}/status` | **CANCELLED fires the agentic loop** |
| `POST /api/appointments/{id}/suggest?strategy=` | on-demand suggestion |
| `GET /api/suggestions?status=` | the front-desk queue |
| `PATCH /api/suggestions/{id}` | accept / reject — the human checkpoint |
| `GET` `PATCH /api/admin/scheduling` | switch `rule` ⇄ `ai` at runtime, no restart |

## How it works

```
PATCH /providers/PRV-001/status {OFFLINE}
  → ProviderService publishes a fact and returns (HTTP 200 in ~40ms)
  → RescheduleAgent, @Async @TransactionalEventListener(AFTER_COMMIT)
      resolve affected appointments   (offline → N, cancelled → 1)
      skip any with a PENDING suggestion for the same trigger
      SchedulingEngine → strategy (ai | rule) → validate against real slots
      persist PENDING suggestion, appointment → RESCHEDULE_PENDING
      STOP — the agent cannot book
  → coordinator calls the patient, clicks Accept
  → one transaction: lock, re-read, swap both slots, move the appointment
```

The AI is one strategy behind an interface. If the gateway is slow, down, or hallucinates a slot
that does not exist, the engine validates the output, drops what is invalid, and falls back to the
rule-based strategy — `fallbackUsed` is surfaced to the UI rather than hidden.

---

## Quick start

Requires Docker only — no local JDK, Maven or Node needed.

```bash
cp .env.example .env     # already done if .env exists
make up                  # builds and starts postgres, redis, api, web
```

| | URL |
|---|---|
| Frontend (dev, HMR) | http://localhost:5173 |
| API | http://localhost:8080/api/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Deep health (db + redis) | http://localhost:8080/actuator/health |

The header badge turns green when the API is reachable — that is the end-to-end smoke test.

```bash
make help     # every available target
make test     # backend + frontend tests
make logs     # tail everything
make reset    # wipe the database volume and start clean
make prod     # production-shaped stack: nginx on :3000
```

## Original scaffold docs

## Layout

```
backend/     Spring Boot 3.3 · Java 21 · Postgres · Flyway · Redis
  src/main/java/com/slotmind/
    common/    error contract, exception handler, request-id + logging filters
    config/    CORS, Jackson, JPA auditing, OpenAPI
    health/    GET /api/health
    example/   ← reference CRUD slice. COPY ITS SHAPE, THEN DELETE IT.
frontend/    React 18 · TypeScript · Vite · React Query · Tailwind v4
  src/
    api/       client.ts is the only place fetch is called
    hooks/     React Query hooks
    features/  example/ ← delete alongside the backend example
    lib/env.ts the only reader of import.meta.env
```

## First 10 minutes of the hackathon

1. `make up`, confirm the badge is green.
2. Read [CLAUDE.md](CLAUDE.md) — it is the contract both you and the AI follow.
3. Rename the feature: `example/` → your domain, on both sides.
4. Write `V2__<feature>.sql`. Never edit `V1__init.sql`.
5. Build one vertical slice end to end, with its tests, and commit it.

## Adding an endpoint (the loop)

```
backend/src/main/resources/db/migration/V2__thing.sql
backend/src/main/java/com/slotmind/thing/{domain,service,api}/…
backend/src/test/java/com/slotmind/thing/ThingApiTest.java   ← not optional
frontend/src/api/things.ts  →  hooks/useThings.ts  →  features/thing/ThingList.tsx
```

## Stack

| Layer | Choice | Why |
|---|---|---|
| API | Spring Boot 3.3, Java 21 | records, pattern matching, virtual-thread ready |
| DB | Postgres 16 + Flyway | schema in version control, `ddl-auto=validate` (D-05) |
| Cache | Redis 7 | wired, unused until needed (D-11) |
| UI | React 18 + TS + Vite | fastest edit loop available |
| Server state | React Query | caching + loading/error states for free (D-08) |
| Styling | Tailwind v4 | no CSS files to invent at hour two |
| Tests | JUnit + MockMvc · Vitest | ~4s backend, <1s frontend (D-07) |
| CI | GitHub Actions | three jobs, green `main` (D-13) |

## Known gaps (deliberate)

- **No authentication** — every endpoint is public. Reasoning and the 10-minute retrofit: D-06.
- **Tests run on H2, not Postgres** — native Postgres SQL is untested by the suite. D-07.
- **Redis is idle** — running but unused. D-11.

## Troubleshooting

| Symptom | Fix |
|---|---|
| API won't start, `Schema validation` error | Entity and migration disagree → fix the migration, `make reset` |
| Badge red, API logs clean | Frontend proxy target wrong → check `VITE_PROXY_TARGET` in the override file |
| Port already in use | Change the port in `.env`, `make down && make up` |
| Backend changes not picked up | Devtools restarts on class change; for `pom.xml` changes `make down && make up` |
| An error in the UI | Copy its `request-id`, then `make api-logs \| grep <id>` |
