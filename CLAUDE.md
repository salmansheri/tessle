# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Low-code platform ("Tessle"), early scaffold stage. The workspace root is not a git repo; it holds two independent projects:

- `Lowcodebackend/` — Spring Boot 4.1.1, Java 27, Maven (package `dev.salman.Lowcodebackend`). Currently only the generated application class and a context-load test exist.
- `lowcodefrontend/` — Next.js 16.4 (App Router, `app/`), React 19, Tailwind 4, TypeScript, Bun (`packageManager: bun@1.4.0`). Has its own git repo. Still the create-next-app starter.

No API contract or frontend↔backend wiring exists yet.

## Backend commands (run in `Lowcodebackend/`)

- Run: `./mvnw spring-boot:run`
- Build / test all: `./mvnw verify`
- Single test: `./mvnw test -Dtest=LowcodebackendApplicationTests` (or `Class#method`)

Lombok and `spring-boot-configuration-processor` are wired as annotation processors explicitly in `pom.xml` (compile and testCompile executions) — keep that when editing the compiler plugin.

### Infrastructure

The backend is intended to be polyglot: starters for JPA, MongoDB, Redis, AMQP (RabbitMQ), and Security/WebMVC, with JDBC drivers for PostgreSQL, MySQL, Oracle, and SQL Server. `compose.yaml` defines a container for each (dev credentials inline, ports unmapped/random). `spring-boot-docker-compose` auto-starts these on `spring-boot:run`, so Docker must be running, and the app will try to connect to all of them. `application.yaml` only sets the app name; no datasource config yet.

## Frontend commands (run in `lowcodefrontend/`)

- Dev: `bun dev`
- Build: `bun run build`
- Lint: `bun run lint` (ESLint 9 flat config, `eslint.config.mjs`)

No test runner is configured.

**Important:** `lowcodefrontend/AGENTS.md` warns this Next.js version has breaking changes from training data. Read the relevant guide in `lowcodefrontend/node_modules/next/dist/docs/` before writing Next.js code, and heed deprecation notices.
