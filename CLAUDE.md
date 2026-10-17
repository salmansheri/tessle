# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Tessle, a multi-tenant low-code platform. Read `CONTEXT.md` (glossary), `docs/architecture.md` (plan, module map, version table) and `docs/adr/` before designing anything. Work is tracked in GitHub issues (spec #1, tickets #2 to #10).

Monorepo:

- `backend/`: Spring Boot 4.1.1, JDK 25 (Boot 4.1 supports up to 26; do not use 27), Maven, package `dev.salman.Lowcodebackend`.
- `frontend/`: Next.js 16.4 App Router, React 19, Tailwind 4, Bun.
- `schemas/`: hand-written Definition JSON Schemas (empty until Forms).

## Backend commands (run in `backend/`)

- Run: `./mvnw spring-boot:run` (needs Docker; `spring-boot-docker-compose` starts Postgres 18, Redis 8 with AOF, RabbitMQ 4 from `compose.yaml`)
- Build / test all: `./mvnw verify`
- Single test: `./mvnw test -Dtest=Class#method`

Tests use Testcontainers (`TestcontainersConfiguration`, `@ServiceConnection`) against real Postgres, Redis and RabbitMQ, so Docker must be running. Mail: callers use `MailQueue.enqueue(MailMessage.of(...))`; a RabbitMQ consumer sends via `JavaMailSender` (Resend SMTP) with retries, a dead-letter queue (`mail.send.dlq`) and Redis-based idempotency. Env vars are in `.env.example`; set `TESSLE_MAIL_SMOKE_TO` to send one real test email at startup. Every request gets an `X-Trace-Id` (MDC `traceId`) that is carried into queued jobs.

Auth (ADR 0004): email-OTP sign-in under `/api/auth` (`identity` package). Access and refresh JWTs live in HttpOnly cookies (`tessle_at`, `tessle_rt`); state-changing requests need the CSRF double-submit header (`X-XSRF-TOKEN`, cookie `XSRF-TOKEN`, obtained from `GET /api/auth/csrf`). The app refuses to start without `TESSLE_JWT_SECRET` and `TESSLE_OTP_SECRET` (>= 32 bytes, see `.env.example`); tests inject their own via `TestcontainersConfiguration`. Secrets belong in the git-ignored `.env`, never in `.env.example`. Sessions: sign-in starts a Redis session family; refresh tokens rotate (`POST /api/auth/refresh`) and reuse revokes the family; `logout` / `logout-all` end sessions instantly because access tokens carry `sid`. A non-HttpOnly `tessle_s` hint cookie tells the SPA whether to look for a session at all (no secret in it). Do NOT switch the security config to `oauth2ResourceServer()`: it silently exempts cookie-authenticated requests from CSRF (see ADR 0004). MockMvc skips the container's `/error` dispatch, so status-code behavior that depends on it needs a real-server test (`RealServerErrorStatusTests`).

Tenancy (ADR 0001): every platform transaction runs as the RLS-subject role `tessle_app` with the user and active Workspace from the JWT (`TenantTransactionManager`), so platform data access must be inside `@Transactional`/repository boundaries; new tenant-owned tables need `ENABLE ROW LEVEL SECURITY` plus policies, and tests under `workspace/TenantIsolationTests` show how to prove isolation below the HTTP layer. Members and invitations (`workspace` package): roles are read per request through `MembershipRoles` (Redis, 30s TTL, invalidated on every membership change); admins manage members but only owners touch owners and a workspace keeps one owner (service check plus a deferred DB trigger); invitations are accepted through the `accept_invitation` definer function and only a token hash is stored. Post-commit side effects (mail, cache invalidation) go through `AfterCommit.run`. Apps (`workspace` package, `apps` table): readable by every member of the active workspace, writable by builder and above, enforced both by `AppService` (via `MembershipRoles`) and by RLS (`is_builder`). Migrations are Flyway files under `src/main/resources/db/migration`.

Tenant test databases (Milestone 2, `tenant` = a Data source's target): `backend/tenant-dbs/<dialect>.sql` holds the same sample schema (customers, orders, invoices) for PostgreSQL, MySQL, Oracle and SQL Server. Tests get a connected, seeded database from `TenantDatabases.get(Dialect)` (`tenantdb` test package). PostgreSQL is always on; the others start only with `-Dtessle.tenantdbs=mysql,oracle,sqlserver` (or `all`) because the images are large (about 0.6 GB MySQL, 1 GB Oracle slim, 1.6 GB SQL Server) and the Oracle and SQL Server containers need minutes to boot. For manual work run `docker compose --profile tenant-dbs up` in `backend/` (ports 55432 postgres, 53306 mysql, 51521 oracle FREEPDB1, 51433 sqlserver; user `tenant`/`tenant`, SQL Server `sa`/`Tenant_pw1!`, database `tenant`). The default `spring-boot:run` never starts them. Starting or testing SQL Server means you accept Microsoft's EULA (`ACCEPT_EULA=Y` / `acceptLicense()`). Because loopback is blocked for Data sources (ADR 0007), the dev profile must allow the Compose network when a ticket connects to these.

Data sources (Milestone 2, `datasource` package, `/api/data-sources`): owners and admins create, edit, test and enable; builders list without connection details; editors and viewers get 403 (RLS on `data_sources` is the backstop). Passwords are sealed by `SecretBox` (AES-256-GCM, key id stored per secret, ciphertext bound to its row) and appear in no response, log or audit event. The app refuses to start without `TESSLE_SECRETS_MASTER_KEY` (base64 of 32 bytes, see `.env.example`); tests use a throwaway key. Requests with unknown JSON fields are refused (`fail-on-unknown-properties`), so no driver options can be smuggled in (ADR 0006). Connection tests run outside any transaction and report fixed error codes (`unreachable`, `bad_credentials`, `unknown_database`), never driver messages. Editing a connection field or the password clears the test result and disables the source. Every administrative action writes an `audit_events` row through `AuditLog` in the same transaction (append-only, admins read). Network and TLS (ADR 0007, `NetworkPolicy`): loopback, link-local, multicast and cloud-metadata addresses are always refused; other non-public ranges only if listed in `TESSLE_DATASOURCE_ALLOWED_NETWORKS` (CIDR, comma separated; empty by default). The host is resolved once and the connection goes to that address through `PinnedSocketFactory`, while TLS verification still uses the name the admin typed (`TenantConnections`). TLS is always on; certificate verification is on unless an admin sets `verifyCertificate=false`, which writes a `datasource.tls_verification_disabled` audit event. An optional PEM CA per Data source is stored sealed (`ca_ciphertext`). Enabling re-checks the address. The `dev` profile (set by the VS Code launch config) allows Docker's private ranges but not loopback, so connect to the Compose tenant databases by container IP and the database's own port. API tests reach their loopback containers through `TestNetwork`, which relaxes only loopback; `NetworkPolicyApiTests` runs the real policy. `TenantDatabases.postgres()` serves TLS with a throwaway certificate (`caCertificatePem()`); `postgresWithoutTls()` is the plain one. Lifecycle and health (#15): `POST /{id}/disable` switches a source off and forgets its test and health, so enabling needs a fresh passing test; `DELETE /{id}` relies on foreign keys from whatever references a Data source (use `ON DELETE RESTRICT`) and answers 409 while any exist. `HealthMonitor` pings enabled sources every `tessle.datasource.health-interval` (default PT5M) and records `ping_*` through `HealthStore`, the one place that uses the privileged connection outside a tenant transaction because a background job has no caller; it never disables a failing source and never touches `updated_at`. Anything that opens a pool must check `state = 'enabled'` and fail with a clear 'data source disabled' message.

Lombok and `spring-boot-configuration-processor` are wired as annotation processors in `pom.xml`; keep that when editing the compiler plugin.

## Frontend commands (run in `frontend/`)

- Dev: `bun dev`; build: `bun run build`; lint: `bun run lint`; typecheck: `bun run typecheck`
- API client: never hand-write API calls. `frontend/openapi.json` is the committed backend spec; `bun run generate:api` regenerates `lib/api/generated` (Hey API, TanStack Query hooks). The browser calls `/api` on the Next origin, which `next.config.ts` rewrites to `BACKEND_URL` (default `http://localhost:8080`). Rewrites are baked in at `next build`, so set `BACKEND_URL` at build time, not only at `next start`.
- After changing any backend endpoint: `cd backend && ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true`, (needs Docker, like all backend tests), then `bun run generate:api`, and commit both. CI fails on a stale spec or stale generated code.
- No test runner yet. `@hey-api/openapi-ts` is pinned exactly (pre-1.0).

**Design:** read `PRODUCT.md` (users, purpose, principles) and `DESIGN.md` (tokens, glass system, components, named rules) before touching UI. The look is Monochrome Proof with frosted glass in two real themes (light/dark, both mandatory, WCAG 2.2 AA). Write only the unprefixed `backdrop-filter` in CSS: Next's optimizer drops it when a hand-written `-webkit-` twin is present, and Chromium ignores the prefixed form. Use the semantic classes in `app/globals.css` (`.glass`, `.btn`, `.pill`, `.data-table`, ...) and the token colors, never hard-coded hex. The signed-in frame is `components/app-shell.tsx`; it must keep rendering while the session loads. Direction contract: `.impeccable/surfaces/`.

**Important:** `frontend/AGENTS.md` warns this Next.js version has breaking changes from training data. Read the relevant guide in `frontend/node_modules/next/dist/docs/` before writing Next.js code.

CI (`.github/workflows/ci.yml`) runs backend `verify` and frontend generate-drift check, typecheck, lint and build.

## Agent skills

### Issue tracker

GitHub Issues on salmansheri/tessle, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
