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

Migrations are Flyway files under `src/main/resources/db/migration`. Lombok and `spring-boot-configuration-processor` are wired as annotation processors in `pom.xml`; keep that when editing the compiler plugin.

## Frontend commands (run in `frontend/`)

- Dev: `bun dev`; build: `bun run build`; lint: `bun run lint`; typecheck: `bun run typecheck`
- API client: never hand-write API calls. `frontend/openapi.json` is the committed backend spec; `bun run generate:api` regenerates `lib/api/generated` (Hey API, TanStack Query hooks). The browser calls `/api` on the Next origin, which `next.config.ts` rewrites to `BACKEND_URL` (default `http://localhost:8080`). Rewrites are baked in at `next build`, so set `BACKEND_URL` at build time, not only at `next start`.
- After changing any backend endpoint: `cd backend && ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true`, (needs Docker, like all backend tests), then `bun run generate:api`, and commit both. CI fails on a stale spec or stale generated code.
- No test runner yet. `@hey-api/openapi-ts` is pinned exactly (pre-1.0).

**Important:** `frontend/AGENTS.md` warns this Next.js version has breaking changes from training data. Read the relevant guide in `frontend/node_modules/next/dist/docs/` before writing Next.js code.

CI (`.github/workflows/ci.yml`) runs backend `verify` and frontend generate-drift check, typecheck, lint and build.

## Agent skills

### Issue tracker

GitHub Issues on salmansheri/tessle, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
