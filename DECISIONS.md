# DECISIONS.md

Architecture decision log. Each entry: the decision, what it costs, and what would make us reverse
it. Recorded at scaffold time (before the problem statement is known), so every entry here is a bet
about a 3-hour build, not a general-purpose recommendation.

New decisions taken during the hackathon get appended with a timestamp.

---

### D-01 — Monorepo, two services, one compose file

**Decision.** `backend/` and `frontend/` in one repository, started with a single `make up`.

**Why.** A 3-hour build has exactly one acceptable onboarding cost: one clone, one command. Split
repos mean version skew between the API contract and its client at the worst possible moment.

**Cost.** CI runs both jobs on every push even for a one-line frontend change. Irrelevant at this
scale.

**Reverse if.** Separate deploy cadences or separate teams — neither applies today.

---

### D-02 — Feature-first packages, strict `api → service → domain`

**Decision.** `example/{api,service,domain}` rather than top-level `controllers/`, `services/`,
`repositories/`.

**Why.** A feature is one folder: easy to copy for the next one, easy to delete when the direction
changes. Two people working on two features touch disjoint folders — merge conflicts drop to
near-zero, which matters far more at hour two than architectural purity.

**Cost.** Unfamiliar to developers who expect layer-first Spring layouts. Mitigated by `example/`
being a working reference and by CLAUDE.md §3.

**Reverse if.** Never, for a project this size.

---

### D-03 — One error shape, one exception handler

**Decision.** All failures become `ApiError { code, message, details, path, requestId, timestamp }`
via `GlobalExceptionHandler`. Services throw; nothing else builds an error body.

**Why.** The frontend writes error handling once, in `client.ts`, and never again. Ad-hoc error
shapes are a slow leak: each endpoint invents its own, the UI accumulates special cases, and by hour
two nobody knows what a failure looks like.

**Cost.** One indirection between throwing and the HTTP status.

**Reverse if.** Never.

---

### D-04 — Correlation id threaded through logs, API and UI

**Decision.** `RequestIdFilter` puts a request id in MDC, the `X-Request-Id` header, and the
`ApiError` body. `ErrorBanner` displays it.

**Why.** This is the debugging decision. During a demo, "it broke" becomes a specific id that
`grep`s directly to the server log line. Debugging speed is explicitly evaluated, and nothing else
in the scaffold buys as much of it for ~40 lines.

**Cost.** Negligible — one filter, one MDC key.

---

### D-05 — Flyway owns the schema; `ddl-auto=validate`

**Decision.** Hibernate may never create or alter tables. Migrations are append-only.

**Why.** `ddl-auto=update` is pleasant for twenty minutes and then quietly diverges across machines;
the failure surfaces during the demo on someone else's laptop. `validate` fails loudly at startup
instead, when there is still time to fix it.

**Cost.** Every schema change needs a migration file — about 30 seconds of typing.

**Reverse if.** Never. This is the single most common cause of hackathon demo failures.

---

### D-06 — No authentication

**Decision.** Every endpoint is public. No Spring Security on the classpath.

**Why.** ~30 minutes to wire properly, plus login UI that demonstrates nothing about the actual
problem being solved. A local demo over 3 hours has no threat model that justifies it.

**Cost.** Not production-deployable as-is. Stated openly in ARCHITECTURE.md §8 rather than hidden —
an undisclosed gap reads as an oversight; a disclosed one reads as a decision.

**Reverse if.** The problem statement requires per-user data. Cut in that case: a single
`OncePerRequestFilter` resolving an `X-User-Id` header into a request-scoped user. Spring Security
only if real credentials must be handled.

---

### D-07 — H2 for tests, not Testcontainers

**Decision.** Tests run on in-memory H2 in PostgreSQL mode (`application-test.yml`), Flyway disabled,
`ddl-auto=create-drop`.

**Why.** The suite runs in ~4 seconds with no Docker dependency, so tests stay in the inner loop
instead of being skipped under pressure. Testcontainers adds ~30s of startup per run and a Docker
dependency inside CI — correct for production, wrong for the hour-two edit loop.

**Cost — the real one.** Postgres-specific SQL (native queries, `jsonb`, `ON CONFLICT`, array types)
is *not* covered by the test suite. Migrations run against H2's approximation, not Postgres.

**Mitigation.** `make up` runs real Postgres with real Flyway migrations — a cold
`make reset && make up` before the demo exercises the real database path. Any native query must be
smoke-tested manually against the running stack.

**Reverse if.** We write a native Postgres query. At that point the trade-off flips and
Testcontainers is worth the 30 seconds.

---

### D-08 — React Query, no Redux or other global store

**Decision.** React Query owns all server state; `useState` handles form input only.

**Why.** Essentially all state in a CRUD demo is server state. React Query gives caching, loading and
error flags, and post-mutation invalidation for free — the three things that otherwise get
hand-rolled badly under time pressure. A Redux store would be ~100 lines of boilerplate reproducing
a worse version of it.

**Cost.** One dependency; a concept to learn if unfamiliar. `hooks/useItems.ts` is the template.

**Reverse if.** Significant client-only state appears (multi-step wizard, collaborative editing) —
then add `useReducer` or Zustand locally, still not Redux.

---

### D-09 — Same-origin `/api` via proxy, in both dev and prod

**Decision.** The browser always calls relative `/api/*`. Vite proxies in dev, nginx proxies in prod.
`VITE_API_BASE_URL` defaults to the empty string.

**Why.** CORS misconfiguration and wrong API base URLs are two of the most common and most
time-wasting demo failures, and this removes both from the demo path entirely. It also keeps dev and
prod URL handling identical, so "works in dev, breaks in the build" can't happen for this reason.

**Cost.** The frontend container must be able to reach the API — true by construction in compose.
CORS config is retained for the run-Vite-outside-Docker case.

---

### D-10 — Dev/prod differ only by compose override and build target

**Decision.** `docker-compose.yml` is production-shaped; `docker-compose.override.yml` (auto-applied)
adds bind mounts and hot reload. Multi-stage Dockerfiles expose `dev` and `runtime` targets from a
shared `deps` layer.

**Why.** Dev and prod build from the same Dockerfile, so "works on my machine" has one fewer place to
hide. The shared `deps` layer keeps the rebuild loop at seconds rather than minutes — directly
converting into implementation speed.

**Cost.** Two compose files to understand. Documented in ARCHITECTURE.md §6.

---

### D-11 — Redis in the stack, unused by code

**Decision.** Redis runs and is wired in Spring config, but no application code touches it.

**Why.** Caching or rate limiting is plausible for an unknown problem statement, and adding
infrastructure under time pressure is far more expensive than leaving a running container idle. One
`@Cacheable` annotation is all that stands between "present" and "used".

**Cost.** ~10MB of RAM and one container in `docker ps`.

**Reverse if.** Hour two arrives with no use for it and the demo machine is struggling — drop the
service from compose, nothing else breaks.

---

### D-12 — A deletable `example/` CRUD slice ships in the scaffold

**Decision.** A complete, tested Item slice (migration → entity → repository → service → controller →
tests → API client → hook → component) is included, and both READMEs say to delete it.

**Why.** It proves the whole stack end-to-end before the problem statement exists — so the first
hour is spent on the problem, not on discovering that Flyway can't reach Postgres. It also gives the
AI a concrete in-repo pattern to copy, which produces far more consistent generated code than prose
conventions alone.

**Cost.** Dead code if not deleted. Explicitly flagged in `example/README.md`, CLAUDE.md §3 and the
root README.

---

### D-13 — CI on GitHub Actions from commit one

**Decision.** Three jobs — backend `mvn verify`, frontend lint/test/build, and a compose build —
running on every push and PR.

**Why.** Git discipline is explicitly evaluated, and a red build on `main` is visible evidence of
process. It also catches the "works locally, missing from the commit" class of error before the demo.

**Cost.** None locally. Jobs run in ~2 minutes.
