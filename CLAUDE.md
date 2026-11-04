# CLAUDE.md — working agreement for AI-assisted development

Read this before writing any code in this repo. It encodes the conventions. Follow them; if a
request conflicts with them, say so before writing the code.

---

## 1. What this repo is

A 3-hour hackathon project. Java 21 / Spring Boot 3.3 API + React 18 / TypeScript / Vite frontend,
every service on Docker. The scaffold already exists. We are adding features to it, not rebuilding it.

Optimise for: **working demo, honest code, fast recovery from mistakes.** In that order.

---

## 2. Hard rules

1. **No new dependencies without asking.** Not for the backend, not for npm. If you think one is
   needed, say what it buys and what the hand-rolled alternative costs, then wait. Adding a library
   at hour two is how a demo dies.
2. **Write tests with every endpoint.** A new backend endpoint lands with a MockMvc test covering
   the happy path, one validation failure, and not-found where applicable. Mirror
   `ItemApiTest`. A new API client module lands with a Vitest test. No endpoint ships untested.
3. **Never edit an applied Flyway migration.** Add `V2__`, `V3__`. Editing a migration that has run
   breaks every other machine, silently.
4. **Entities never cross the HTTP boundary.** Controllers accept and return records in `api/`.
5. **Don't invent requirements.** If the task is ambiguous, implement the narrow reading and flag
   the assumption in one line. Do not widen scope to be "helpful".
6. **Don't touch `docker-compose*.yml`, `pom.xml`, `package.json` or CI without being asked.**
   Infrastructure churn mid-hackathon costs more than any feature it enables.
7. **Report failures honestly.** If tests fail, paste the output. Never say "should work" about
   something you did not run.

---

## 3. Layering

### Backend — `com.slotmind`

```
<feature>/api/      Controller + request/response records. Thin: validate, delegate, map status.
<feature>/service/  All business rules. Owns @Transactional. Throws ApiException subclasses.
<feature>/domain/   JPA entity + Spring Data repository. No logic beyond queries.
common/             ApiError, PageResponse, exception hierarchy, GlobalExceptionHandler, filters.
config/             @Configuration beans only. No logic.
```

Dependencies point one way: `api → service → domain`. A controller that touches a repository, or a
repository call inside a controller test, is a bug.

`example/` is the reference slice. Copy its shape; delete the package once real features exist.

### Frontend — `src/`

```
api/        One module per resource. The ONLY place fetch is called (via client.ts).
hooks/      React Query hooks. Components never call api/* directly.
features/   Feature-scoped components.
components/ Shared presentational components.
pages/      Route-level components. Composition only.
lib/        env.ts and pure helpers. All import.meta.env reads happen in env.ts.
```

No `fetch` outside `api/client.ts`. No API URL strings outside `api/*.ts`.

---

## 4. Error handling

**Backend.** Controllers and services never build error responses and never catch-to-translate.
They throw `NotFoundException` / `BadRequestException` / `ConflictException` (or a new
`ApiException` subclass). `GlobalExceptionHandler` is the single exit point and emits one shape:

```json
{ "code": "VALIDATION_FAILED", "message": "...", "details": [{"field":"name","message":"..."}],
  "path": "/api/items", "requestId": "a1b2c3d4", "timestamp": "..." }
```

`code` is stable and machine-readable; `message` is safe to show in a toast. Unexpected exceptions
log the full stack trace server-side and return `INTERNAL_ERROR` with nothing leaked.

**Frontend.** `api/client.ts` throws `ApiError` for every non-2xx. Components render it via
`ErrorBanner`, which surfaces `requestId` — that id appears in every backend log line
(`[requestId]` from `RequestIdFilter`), so a UI error is one `grep` from its server log.

Never swallow an error to make the UI quiet.

---

## 5. Naming

| Thing | Convention | Example |
|---|---|---|
| REST collection | plural, kebab-case | `/api/purchase-orders` |
| Java class | `*Controller`, `*Service`, `*Repository` | `ItemService` |
| Request/response | `*Request`, `*Response` records | `ItemRequest` |
| DB table/column | snake_case, plural table | `items`, `created_at` |
| Error code | SCREAMING_SNAKE, stable | `VALIDATION_FAILED` |
| React component | PascalCase, one per file, default export | `ItemList.tsx` |
| Hook | `use*`, named export | `useItems` |
| Env var (frontend) | `VITE_*` | `VITE_API_BASE_URL` |

---

## 6. Config

Every environment-dependent value is read from an env var with a local-dev default, in
`application.yml` (backend) or `src/lib/env.ts` (frontend). No hardcoded hosts, ports, URLs or
credentials anywhere else. Any new var goes into `.env.example` in the same commit.

---

## 7. Git discipline

- Conventional commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`.
- Mention the AI's role when it was substantial: `feat(items): bulk import (AI-drafted, reviewed)`.
- One logical change per commit. Commit after each working slice — a hackathon needs a working
  commit to roll back to, and the commit log is itself evaluated.
- Never commit `.env`, `target/`, `node_modules/`, or `dist/`.
- Branch per feature when more than one person is working; `main` stays runnable at all times.

---

## 8. How to work with me (prompting conventions)

- **State the plan before writing code** for anything spanning more than one file. One short
  paragraph, then build.
- **Full vertical slices**, not fragments: migration → entity → repository → service → controller →
  test → API client → hook → component. Half a slice is harder to review than none.
- **Verify before claiming done.** Run `make test-api` / `npm test`, hit the endpoint with curl,
  paste real output.
- **When debugging:** state the hypothesis, the evidence for it, and the smallest check that would
  disprove it — before changing code. No shotgun edits.
- **Flag invented knowledge.** If an API signature or library behaviour is uncertain, say "unverified"
  rather than asserting it.

---

## 9. Definition of done for a feature

- [ ] Migration added (never edited)
- [ ] Endpoint returns the standard error shape on failure
- [ ] MockMvc test: happy path + validation failure + not-found
- [ ] Frontend hook + component, loading and error states both rendered
- [ ] `make test` green
- [ ] Runs from a cold `make reset && make up`
- [ ] Committed with a conventional message
