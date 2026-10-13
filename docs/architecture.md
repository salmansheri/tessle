# Tessle architecture plan

Vocabulary is defined in `../CONTEXT.md`; decisions with lasting consequences are in `adr/`. No feature code exists yet.

## Principles

- Everything a user builds is a versioned JSON **Definition** (Form, Report, Page). The platform interprets Definitions at runtime; AI generates Definitions, never raw code.
- Definition JSON Schemas are hand-written in a shared `schemas/` folder and are the single contract for backend validation, the AI output validator, the frontend builder (generated TS types and Zod), and export/import. CI regenerates and fails on drift.
- Published versions are immutable. Rollback creates a new version copying an old one. Exports contain Definitions only, with Data sources bound by logical name and placeholders to bind on import.
- Every tenant query is Workspace-scoped (ADR 0001).

## Repository (monorepo)

```
backend/    Spring Boot 4.1.1, JDK 25, Maven (from Lowcodebackend/)
frontend/   Next.js 16.4, Bun (from lowcodefrontend/)
schemas/    Definition JSON Schemas
docs/       this plan, adr/
compose.yaml  Postgres, Redis (AOF), RabbitMQ, Ollama (later)
```

Cleanup at M1: `git init` at the root absorbing the frontend repo; drop Mongo starters and the MySQL/Oracle/SQL Server drivers from the platform module (tenant drivers move to a separate module loaded for Data sources); set `java.version` to 25.

## Backend modules

| Module | Responsibility |
|---|---|
| identity | Users, OTP (Postgres, hashed), JWT cookies, refresh families (Redis), Google sign-in |
| workspace | Workspaces, Memberships, RBAC, Apps |
| definition | Definition storage, drafts, versions, publish, rollback, schema validation, export/import |
| datasource | Data source CRUD, encrypted credentials, per-source pools, introspection, Access rules |
| query | Definition to SQL compiler, per-dialect renderers, `QueryExecutor`, guardrails (timeout, row limit, read-only) |
| jobs | RabbitMQ consumers, retries, DLQs, trace-id propagation |
| mail | Mail consumer using `JavaMailSender` (Resend SMTP) |
| ai (M6) | Provider abstraction, step pipeline, validate and repair loop, Redis stream and cancel |
| messaging (M7), workflow (M9) | later |

Platform metadata uses JPA and Flyway on Postgres. Tenant data goes only through `query`.

## Cross-cutting

- Auth: ADR 0004. CSRF via double-submit token; browser reaches the backend through a same-site `/api` rewrite in Next.js.
- Redis: refresh tokens, Membership cache, schema-metadata cache, rate limiting, locks, WebSocket fan-out, AI stream and cancel flag. AOF on.
- RabbitMQ: exports, mail, external form writes, AI jobs, scheduled reports; idempotent consumers, backoff retries, DLQs.
- Observability: structured logs, Micrometer/OpenTelemetry, trace id from HTTP through jobs.
- CI: every PR runs Postgres integration tests plus compiler SQL-text unit tests per dialect. Nightly and release branches run the full four-database suite in a Maven profile. The OpenAPI-to-Hey-API drift check runs on every PR.
- Custom code: ADR 0003.

## Milestone 1 scope

Monorepo and cleanup, Flyway baseline with RLS helper, email-OTP and Google sign-in, Workspaces, Memberships and roles, App list, OpenAPI to Hey API pipeline with drift check, Next.js shell (sign-in, Workspace switcher, members, App list), CI. UI design to go through the `impeccable` plugin.

## Version table (verified 2026-10-13)

| Item | Version | Note |
|---|---|---|
| JDK | 25 (LTS) | Boot 4.1.1 supports up to 26; 27 is not LTS and unsupported |
| Spring Boot | 4.1.1 | |
| Spring Security | 7.1.1 | |
| Spring AI (M6) | 2.0.1 | `spring-ai-starter-model-ollama`; LangChain4j 1.22.0 is the alternative |
| springdoc-openapi | 3.1.1 | Boot 4 line |
| Flyway | 13.10.0 | |
| JSqlParser | 5.4 | |
| FastExcel / POI | 0.20.2 / 5.5.1 | |
| Next.js / React | 16.4.0 / 19.3.0 | read `node_modules/next/dist/docs` first |
| TypeScript | 7.0.2 | Hey API peers list up to 6; expect warnings, pin if needed |
| TanStack Query | 5.104.2 | |
| @hey-api/openapi-ts | 0.99.0 | pre-1.0, pin exactly |
| Zod / Tailwind / esbuild | 4.6.7 / 4.3.3 / 0.28.2 | |
| GSAP / @gsap/react | 3.15.0 / 2.1.2 | free incl. plugins; no competing visual animation builder |
| Bun | 1.4.3 | |
| PostgreSQL | 18.6 | |
| Redis | 8.10.2 | RSALv2/SSPLv1/AGPLv3 tri-licence; Valkey is a drop-in alternative |
| RabbitMQ / Ollama | 4.3.6 / 0.40.3 | |

Not carried over: jOOQ (ADR 0002).
