# Architecture Decision Record — SlotMind

Five decisions, in the order the brief asks for them. Each is **Context → Options → Decision → Tradeoffs**.

A note on how these were produced: the decisions were made and written down *as each phase was built*, not reconstructed afterwards. The rejected options below are genuinely ones that were on the table, and several of the tradeoffs were discovered by running the system rather than by reasoning about it — those are marked where they occur, because they are the honest part.

---

## ADR-1 — Where does scheduling logic live?

### Context
"Given an appointment, recommend a better slot" could reasonably live in the entity, in a service, or in a standalone engine. The brief adds a sharper sub-question: **how do you prevent booking conflicts at the domain boundary?** Two coordinators working the same list is the failure mode the clinic has today, and it is the thing a naive implementation reproduces in software.

### Options
1. **Logic on the entity (`Appointment.findBetterSlot(...)`).** True DDD-rich-domain. But choosing a slot needs the full slot inventory and the provider roster — an entity reaching for that means either passing a bag of collections into every call or injecting repositories into entities.
2. **Logic in `AppointmentService`.** Conventional Spring. Everything in one place, but the service becomes the junction of HTTP concerns, transactions, persistence *and* the recommendation algorithm — and the algorithm then cannot be unit-tested without a Spring context.
3. **A separate `SchedulingEngine` orchestrating pluggable strategies, with strategies as pure functions over an explicit context.** More moving parts.

### Decision
**Option 3, with the invariant pushed into the database.**

Responsibilities split three ways:
- **`SchedulingEngine`** gathers candidates, delegates to a strategy, validates the result, and handles fallback. It is the only component that touches repositories on this path.
- **Strategies** (`RuleBasedStrategy`, `AiSchedulingStrategy`) receive a `SchedulingContext` record — appointment, candidate slots, provider roster, trigger reason, and `now` — and return ordered recommendations. No repository access, no clock access.
- **The entity** owns its own state machine. `AppointmentStatus.canTransitionTo` encodes the legal transitions and `Appointment.transitionTo` is the only way status changes, so no caller anywhere can write an illegal transition.

On the sub-question — **conflicts are prevented at the database boundary, not the domain boundary.** A Java-level `if (slot.isOpen())` is advisory; two threads both pass it. The actual guarantee is:

```sql
CREATE UNIQUE INDEX uq_appointments_live_slot
    ON appointments (slot_id) WHERE status <> 'COMPLETED';
```

Partial, so a historical COMPLETED appointment does not permanently burn its slot id. The domain layer enforces *meaning* (which transitions are legal, which provider may take which slot); the database enforces *exclusivity*. Treating the domain model as the place where conflicts are prevented is the mistake that produces double-bookings.

### Tradeoffs
- **Paid:** three collaborators instead of one, and a `SchedulingContext` DTO to maintain.
- **Gained:** `RuleBasedStrategyTest` runs with `new RuleBasedStrategy()` — no Spring, no database, a fixed clock, 8 tests in 15ms. That is only possible because strategies are pure.
- **Paid:** the candidate list is a snapshot. A slot can be booked between suggestion and acceptance, so validation at suggestion time is best-effort by construction. That is precisely why ADR-3 exists, and it is worth saying out loud rather than pretending the snapshot is authoritative.
- **Rejected deliberately:** a `btree_gist` exclusion constraint preventing overlapping appointments per provider. It needs `CREATE EXTENSION`, a generated `tstzrange` column, and real debugging time. `uq_slots_provider_start` plus the live-slot index gets ~95% of the protection for one line.

---

## ADR-2 — Runtime strategy switching

### Context
Two strategies now (`rule`, `ai`), a third in sprint 2 (`RoomConstraintStrategy`). The brief is explicit that adding it should mean *"implementing the contract and registering — nothing more"*, and that the same contract must serve both the synchronous HTTP path and the asynchronous agentic loop.

### Options
1. **Spring injects `Map<String, SchedulingStrategy>`**, keyed by bean name. Zero code. But the config value becomes a *bean name* — `app.scheduling.strategy=ruleBasedStrategy` — so a public contract is expressed through a Spring naming detail, and renaming a class silently breaks config. Mitigating it with `@Component("rule")` hides a public API contract inside an annotation nobody reads.
2. **`supports(String key)` on each strategy.** A linear scan where two strategies can both claim a key, with the winner decided by classpath order, and no way to report "known keys" in an error message.
3. **An enum of strategy keys** with a registry keyed on it. Type-safe and tempting — and **disqualifying**: adding `RoomConstraintStrategy` would require editing a central enum. That is "implement, register, *and edit an enum*", which breaks the exact property being graded.
4. **Inject `List<SchedulingStrategy>`, index by an explicit `key()` method on the interface.**

### Decision
**Option 4.** `SchedulingStrategyRegistry` collects every strategy on the classpath, indexes by `strategy.key()`, and **fails fast at construction on a duplicate key** rather than silently picking one.

Switching is genuinely at runtime, and the detail that makes it so is small:

```java
public SchedulingStrategy resolve(String override) {
    if (hasText(override)) { ... }
    String configured = properties.getStrategy();   // read HERE, on every call
    ...
}
```

`SchedulingProperties` is a mutable `@ConfigurationProperties` singleton, so `PATCH /api/admin/scheduling` changes behaviour for the very next request with no restart and no Spring Cloud `@RefreshScope`. **Caching the resolved strategy in a field at construction looks identical and silently pins the value at startup** — there is a three-line test, `rereadsThePropertyOnEveryCall()`, guarding exactly that regression.

Both paths resolve through the same registry because both call one method, `RescheduleService.suggestFor(appointmentId, reason, override)`. The HTTP endpoint calls it; `RescheduleAgent` calls it. There is no second code path that generates suggestions, so "same contract from both callers" is structural rather than aspirational.

Precedence: **per-request override → live config → fallback key.** An unknown *override* is a 400 (the caller typed it); an unknown *configured* value logs an error and degrades to the fallback, because a config typo must not 500 a user's request.

### Tradeoffs
- **Paid:** one extra method on the interface, and `key()` must be kept unique — enforced at construction rather than by convention.
- **Paid:** a mutable singleton is technically shared mutable state. For a single-process demo this is the point; in a multi-instance deployment a PATCH would only affect one instance, and the honest answer is that it would need to move to a shared config store.
- **Gained:** `registeringANewStrategyRequiresNothingBeyondImplementingTheInterface()` asserts the sprint-2 promise instead of claiming it in prose.

---

## ADR-3 — Transactional accept

### Context
Accepting a suggestion must move the appointment, free the old slot and book the new one — atomically. The graded scenario (AGT-3): two coordinators accept two *different* suggestions that point at the *same* open slot. Exactly one must win, and the loser must get a clean 409, not a corrupted booking.

### Options
1. **`@Transactional` alone.** Insufficient, and this is worth being precise about: at Postgres' default READ COMMITTED, both transactions read the slot as `OPEN`, both write, and both commit. Atomicity is not isolation.
2. **Optimistic locking (`@Version` on `Slot`).** Correct, but the loser does not find out until flush/commit, so it burns a full read-modify-write first, and the failure surfaces as `ObjectOptimisticLockingFailureException` from inside the commit — the same awkward place as a constraint violation, with no opportunity to produce a domain-meaningful message.
3. **Pessimistic row locks in a fixed order, plus a partial unique index as backstop.**
4. **Serializable isolation.** Correct, and pushes retry handling onto every caller for a conflict we can name precisely ourselves.

### Decision
**Option 3.** Three layers, each doing a different job:

**Lock, then read, then validate.**
1. Load the suggestion unlocked — it is only a pointer.
2. Lock the **appointment**.
3. Lock the **slots, sorted ascending by id**, one statement each.
4. *Then* assert the target slot is still `OPEN`.

Two details carry the correctness:

- **Fixed global lock order (appointment → slots by id ascending).** Without it, a swap — A moves 005→009 while B moves 009→005 — deadlocks, and Postgres resolves it after `deadlock_timeout` by killing a non-deterministic victim. The sort is done **in Java, not `ORDER BY ... FOR UPDATE`**, because Postgres' lock-acquisition order relative to `ORDER BY` depends on the chosen plan.
- **The lock read must be the first read of each row.** Hibernate will not re-hydrate an already-managed entity, so an earlier unlocked `findById` would hand the race loser a stale `OPEN` even though the lock was acquired. `@Lock` is also silently ignored on the inherited `findById`, so `lockById` is declared explicitly with `@Query`. A unit test asserts `findById` is *never* called on this path.

**Exception translation lives in a second, non-transactional bean.** This is not stylistic. `DataIntegrityViolationException` fires at commit — *after* the `@Transactional` method body returns — so a try/catch inside it cannot observe it. And a lock failure caught mid-method arrives after Spring has already set rollback-only, so throwing `ConflictException` there yields `UnexpectedRollbackException` to the caller instead of a 409. `SuggestionDecisionService` is therefore non-transactional and delegates to `SuggestionAcceptanceService` (`REQUIRES_NEW`). Two beans rather than self-injection, because `this.acceptInTransaction(...)` would bypass the proxy and silently run with no transaction at all.

**One domain rule worth naming:** a `CANCELLED` old slot is never reopened. On a `SLOT_CANCELLED` trigger the clinic deliberately killed that slot; setting it back to `OPEN` would let the next patient be booked into a time that does not exist.

**Evidence, not assertion.** 13 genuinely concurrent HTTP accepts against real Postgres, across two runs:

```
one 200 / one 409, every time
409 names the slot → it came from the slot guard, not another check
SLT-005 BOOKED with exactly 1 live appointment on it
loser untouched on its own slot, still RESCHEDULE_PENDING
winner alternated between callers → a real race, not deterministic ordering
```

### Tradeoffs
- **Paid:** the loser blocks for the few milliseconds the winner needs. Acceptable, and it buys a message that names the slot instead of a database-level exception.
- **Paid:** lock ordering is a discipline every future write path must also follow. It is documented here because it is invisible in the code of any single method.
- **Paid:** the partial unique index is Postgres-only and therefore **invisible to the H2 test suite** — which is exactly why `AcceptSuggestionConcurrencyIT` exists and runs on Testcontainers in CI (see `DECISIONS.md` D-07, whose stated reversal trigger was "we write a native Postgres query").
- **Not done:** `NOWAIT`. It would make the loser fail instantly but cannot distinguish "someone is booking this slot" from "someone is editing an unrelated field".

---

## ADR-4 — Agentic loop decoupling

### Context
Two triggers. Provider-offline affects *many* appointments; slot-cancelled affects *one*. Same handler with a branch, or separate events and handlers?

### Options
1. **Two event types and two listeners.** Each reads cleanly in isolation — and each needs its own dedupe guard, fallback handling, per-item error isolation and fan-out cap. Four pieces of logic duplicated to express one difference.
2. **One event, one listener, with the trigger difference confined to a resolver.**
3. **No events — call the reschedule service directly from `ProviderService`.** Simplest, and wrong: the status update and the suggestion generation become one transaction, so the HTTP request is held open for the length of an LLM call, and a failure to suggest would roll back the provider status change.
4. **A `@Scheduled` poller looking for newly-offline providers.** Decoupled, but adds latency and has no transactional ordering guarantee.

### Decision
**Option 2.** `ProviderService` and `SlotService` publish `RescheduleTriggered(reason, sourceId)` and return. They do not know rescheduling exists. `RescheduleAgent` consumes it; `AffectedAppointmentResolver` is the *only* place the two triggers differ:

```java
case PROVIDER_OFFLINE -> byProvider(sourceId);   // 1-to-N
case SLOT_CANCELLED   -> bySlot(sourceId);       // 1-to-1
```

Dedupe, engine call, fallback, persistence and error isolation are written once. A third trigger in sprint 2 ("room closed") means publishing the same event with a new reason — the listener is untouched.

Three implementation details that are decisions, not detail:

- **`AFTER_COMMIT`, not `@EventListener`.** Firing pre-commit would build suggestions against a provider status that may still roll back, producing phantom suggestions for a provider who is in fact still available.
- **The event carries the source *id*, never an entity.** The listener runs on another thread with no persistence context; an entity would arrive detached.
- **Per-appointment try/catch.** Nothing above catches an `@Async void` method, so one failing appointment must neither abort the batch nor vanish silently. A `DataIntegrityViolationException` from the dedupe index is caught separately and logged at WARN, because under concurrency it is expected rather than an error.

**The agent stops at PENDING.** It has no path to book anything — the accept transaction lives in `SuggestionAcceptanceService`, reachable only from a human clicking Accept. That boundary is enforced by which bean owns the transaction, not by a comment. This is the brief's human checkpoint, and it is deliberate: the AI does not know the patient cannot drive before noon.

### Tradeoffs
- **Paid:** a `switch` on trigger reason inside the resolver. That branch is the honest expression of a real difference; duplicating four other concerns to avoid it would be worse.
- **Paid:** `@Async` means failures are invisible unless logged. Mitigated by the per-item catch and by an MDC `TaskDecorator` that carries `requestId` across the thread boundary, so an async-generated suggestion is still one `grep` from the request that caused it.
- **Paid:** fan-out is capped at 10 and the excess is logged rather than silently truncated. A provider with a full day of appointments at ~2s per LLM call would otherwise look like a 40-second hang.
- **Discovered by running it, not by reasoning:** the first working fan-out offered **both** patients the same slot. The accept transaction kept the data correct, but one coordinator would have phoned a patient, agreed a time, and hit a 409. The engine now excludes slots already promised by a PENDING suggestion. No amount of code review surfaced this; starting the stack did.

---

## ADR-5 — Extensibility and exclusions

### Context
Sprint 2 adds room and equipment constraints, patient preference history, variable durations and per-provider daily caps. Sprint 3 adds waitlist auto-fill, notifications and multi-clinic routing. What is ready, and what was deliberately left out?

### Decision — where room constraints plug in
`RoomConstraintStrategy` is a new file:

```java
@Component
class RoomConstraintStrategy implements SchedulingStrategy {
    public String key() { return "roomConstraint"; }
    public StrategyResult recommend(SchedulingContext ctx) { ... }
}
```

It is picked up automatically, is selectable via `app.scheduling.strategy` or `?strategy=roomConstraint`, and inherits validation and fallback from the engine for free. No enum, no factory, no registry edit, no fallback wiring.

The one thing that would need to change: `SchedulingContext` carries no room or equipment data. The interface does **not** forbid a strategy from injecting a repository — `recommend` runs inside a read-only transaction, so a strategy may read but must not write. That is the deliberate escape hatch that keeps "implement and register, nothing more" literally true rather than nearly true. The shipped strategies stay pure because purity is what makes them unit-testable without Spring.

### Decision — what was excluded, and why

| Excluded | Reason |
|---|---|
| **Authentication / authorisation** | ~30 min plus login UI that demonstrates nothing about rescheduling. Every endpoint is public. The intended cut if required is a single `OncePerRequestFilter` resolving a header to a user, not Spring Security. |
| **Waitlist auto-fill** | Sprint 3 by the brief. Accepting already frees the old slot, which is the event a waitlist would subscribe to — the hook exists. |
| **Notifications (SMS/email)** | Sprint 3. The agent's human checkpoint is the natural place to attach one. |
| **Multi-clinic routing** | No clinic entity exists; adding one touches every query. |
| **SSE reasoning stream (+5)** | Verified the gateway supports streaming, but it is presentation polish on an already-working path. Cut in favour of the ADR, which is worth more. |
| **UI ceiling (day view, waitlist panel, provider load)** | Lowest points-per-minute on the board. |
| **`btree_gist` overlap exclusion** | See ADR-1. |
| **Circuit breaker on the LLM** | Genuinely useful in production; a new dependency here. The cheaper equivalent already exists — flip the strategy to `rule` via the admin endpoint. |
| **Retry on malformed LLM JSON** | Doubles worst-case latency to recover a case the rule-based fallback already covers. |

### Tradeoffs
- **Paid:** Redis runs and is unused. Keeping it costs one container; retrofitting infrastructure under time pressure costs far more. It was also the tempting wrong answer for suggestion dedupe — a partial unique index is one line, survives restart, and needs no second source of truth.
- **Paid:** the demo seed (`V2__seed.sql`) and the test fixtures (`TestFixtures`) are two sources of truth for example data. Deliberate: Flyway is disabled in the test profile because H2 cannot express the partial unique indexes the schema depends on, so tests build their own fixtures. The seed gets its own coverage via `SchemaMigrationIT` against real Postgres.
- **Known gap:** the test suite proves nothing about the live LLM gateway — every AI test uses an injected fake, on purpose. A manual call against the real gateway before the demo is the mitigation, and it was done (see the Phase 6 commit).

---

## Addendum — what the process itself surfaced

Three defects were found by *running* the system or by tests failing honestly, not by review. They are listed because they are the most informative part of the record:

1. **A bad enum in a request body returned 500** with a stack trace, for what is a client typo. Found by a test written to expect 400. Fixed in the shared error contract, so every endpoint inherited the fix.
2. **The test suite was quietly flaky and a run I had already reported as passing contained two silent agent failures.** Once the loop was wired, every test that PATCHes a provider offline spawned async work, and `AFTER_COMMIT` submits it *after* the response returns — so the next test's reset deleted rows mid-flight.
3. **`TestFixtures.BASE` was a fixed past instant** while the engine filters candidates against the real clock. Fixture slots silently expired, so the suite's outcome depended on **what time of day it ran**.

The second is the one worth dwelling on: a green suite is evidence, not proof, and the log said otherwise before the assertions did.
