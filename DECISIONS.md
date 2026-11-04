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

---

### D-14 — Testcontainers added for the concurrency and schema tests

**Decision.** `*Test` runs on H2 under surefire; `*IT` runs on real Postgres via Testcontainers
under failsafe. CI runs both.

**Why.** D-07 named its own reversal trigger: *"reverse if we write a native Postgres query."*
`SELECT … FOR UPDATE` and two partial unique indexes are exactly that, and neither exists in H2 —
so the double-booking backstop would otherwise be untested infrastructure. `SchemaMigrationIT` also
closes a real hole: production runs `ddl-auto=validate` against a Flyway schema while tests generate
theirs from the entities, so nothing else checks the two agree.

**Cost.** ~40s on the CI backend job. The local inner loop is unchanged at ~10s.

**Honest limitation.** The ITs **cannot run on this machine**: all backend tests execute in a Maven
container with no usable Docker socket (bind-mounting it, at either macOS path, still fails).
CI is the oracle. The *behaviour* they assert was verified locally a different way — 13 genuinely
concurrent HTTP accepts against the running stack.

---

### D-15 — Demo seed and test fixtures are separate on purpose

**Decision.** `V2__seed.sql` exists for `make up` and the walkthrough. Tests never run it; they build
fixtures through `TestFixtures`.

**Why.** The test profile disables Flyway because H2 cannot express the partial unique indexes the
schema depends on. Given that, hand-writing `@Sql` against a Hibernate-generated schema would mean
maintaining SQL for a schema we never see — worst of both worlds. Type-safe builders make an entity
rename a compile error instead of a 3am runtime surprise.

**Cost.** Two sources of truth for example data. Mitigated by `SchemaMigrationIT`, which asserts the
seed against real Postgres, including the demo precondition that PRV-001 holds exactly two confirmed
appointments.

---

### D-16 — The engine does not offer one slot to two patients

**Decision.** `SchedulingEngine` excludes slots already named by a PENDING suggestion.

**Why.** Found by running the demo, not by review: the first working fan-out offered both patients
the same slot. The accept transaction kept the data correct — but one coordinator would have phoned
a patient, agreed a time, and hit a 409. Correct data, bad product.

**Cost.** One extra query per suggestion. A slot held by an un-actioned suggestion is unavailable to
others until that suggestion is rejected; with a rejection flow in place, that is the right default.

---

### D-17 — An offline provider's slots keep their own status

**Decision.** Marking a provider OFFLINE does not rewrite their slots. BOOKED slots stay BOOKED;
OPEN slots stay OPEN in the database and are shown in the UI as "provider offline".

**Why.** Two separate questions were being conflated.

*Why not free the BOOKED slots immediately?* Because the patient still holds that time until they
agree to move. Releasing it at trigger time would let someone else book it while the original
patient is still being called, and would leave the appointment pointing at an OPEN slot. The slot is
freed at the moment of acceptance, inside the same transaction that books the new one.

*Why not mark the OPEN slots CANCELLED?* Because they are not cancelled — the doctor is away.
CANCELLED means a slot was deliberately killed and must never be resurrected (the accept path
depends on exactly that distinction). Encoding provider availability into slot status is
denormalisation that would then have to be reversed when the provider comes back online.

Availability is therefore **derived**: `slot.status == OPEN && provider.status == OFFLINE` means not
bookable. The engine already excludes those slots from candidates, and `POST /api/appointments`
refuses them with 409 "Provider is offline".

**The bug this did surface.** The UI was still rendering them as "open", which is the interface
promising something the API would refuse. Fixed in `SlotList`, with a test asserting the slot is not
advertised as bookable when its provider is offline.

**Cost.** Availability is computed in two places (engine and UI) rather than stored once. Acceptable
while the rule is one line; if a third consumer appears it should move to a derived field on the
slot response.

---

### D-18 — "Suggest another option" is one call, two transactions

**Decision.** `POST /api/suggestions/{id}/alternative` rejects a suggestion and proposes a different
slot. The engine never re-offers a slot this appointment has already refused.

**Why one endpoint.** It matches the real-world action — a patient says "not that time" and the
coordinator needs the next idea immediately. Leaving it as reject-then-hunt made the UI do two
things the user experiences as one, and forced the client to know the original trigger reason in
order to ask the right question again.

**Why two transactions, not one.** The rejection is a fast local write that must commit and stay
committed. Generating the replacement may call an LLM over the network; holding row locks open
across that would turn a slow gateway into a database problem.

**Why the trigger reason is carried over.** Re-suggesting as INITIAL would ask the AI the wrong
question ("a coordinator wants options" rather than "this patient's provider is gone") and make the
UI badge lie about why the patient is being moved.

**Cost.** A refused slot is excluded for that appointment permanently, with no way to un-refuse it
short of a new suggestion cycle. Correct for a single sitting; a longer-lived system would want the
exclusion to expire.

**Honest edge case.** When nothing is left, the rejection still succeeds and the response carries a
null alternative rather than an error — the rejection did not fail. The UI says so explicitly,
because a card silently vanishing gives the coordinator no way to tell whether the system is still
working on it.

---

### D-19 — The agent resolves LIVE appointments, not just CONFIRMED ones

**Decision.** `AffectedAppointmentResolver` matches every appointment for the provider whose status
is not `COMPLETED`, rather than only `CONFIRMED`.

**The bug it fixes.** A patient moved ONTO a provider by an accepted suggestion sits in
`RESCHEDULED`, holding a real booked slot. When that provider then went offline, the agent logged
`affects 0 appointment(s)` and the patient was silently stranded on a slot with an absent doctor —
the worst possible failure for this system, because it looks like success.

**Why it was missed.** The state machine already permitted `RESCHEDULED -> RESCHEDULE_PENDING`, with
the comment "a rescheduled appointment can be disrupted again before it is seen". The domain model
anticipated the case; the query forgot it. No test covered a *second* disruption because every test
started from seed data where nothing had been rescheduled yet.

**Why `status <> COMPLETED`** rather than an explicit list: it matches the `uq_appointments_live_slot`
index, so "live appointment" means one thing across the schema and the agent. Appointments already
awaiting a decision are filtered downstream by the per-trigger dedupe guard, not here.

**Cost.** A provider going offline now fans out over slightly more appointments. Bounded by the
existing fan-out cap.
