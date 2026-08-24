# Biblos — Agent Instructions

Two independent modules, no shared build system. Each has its own toolchain.

## Modules

| Module   | Path        | Stack                                  | Build                  |
|----------|-------------|----------------------------------------|------------------------|
| Agent    | `agent/`    | Java 21, Maven, SQLite                 | `mvnw` (Maven Wrapper) |
| Frontend | `frontend/` | React 19, TypeScript 6, Vite 8, Oxlint | npm                    |

## Source of truth

Detailed specs live in `docs/` — these define the intended architecture, not the current code:
- `docs/agent.md` — agent module spec (filesystem scanner, SQLite, CLI pipeline)
- `docs/front.md` — frontend spec (structural CSS, sql.js, hash routing)

Consult these before implementing features in either module.

## Commands

### Agent module (run from `agent/`)

```
mvnw clean compile    # build
mvnw test             # tests (JUnit — pom.xml has JUnit 3.8.1 placeholder)
```

### Frontend module (run from `frontend/`)

```
npm run dev           # dev server (Vite)
npm run build         # tsc -b && vite build
npm run lint          # oxlint (NOT eslint)
npx tsc --noEmit      # typecheck only
```

OpenCode slash commands also available: `/agent-build`, `/agent-test`, `/front-build`, `/front-dev`, `/front-lint`, `/front-typecheck`.

## Conventions

- **No shared root build** — there is no root `package.json` or monorepo tool. Modules are independent.
- **Frontend CSS** — structural-only (no colors, gradients, fonts, transforms). See `docs/front.md` for philosophy.
- **TypeScript strictness** — `erasableSyntaxOnly`, `verbatimModuleSyntax` are enabled. No enums.
- **Linter** — frontend uses Oxlint, not ESLint. Config at `frontend/.oxlintrc.json`.
- **Commit style** — Conventional Commits format. Use `/commit` command for guided flow.
- **Agent is placeholder** — `agent/src/main/java/com/biblos/App.java` is "Hello World". Real implementation follows `docs/agent.md`.
- **Frontend is boilerplate** — default Vite React template. Real implementation follows `docs/front.md`.

## Current state (Aug 2026)

Both modules are scaffolds. Specs are comprehensive (agent: 1038 lines, frontend: 545 lines). Implementation has not started.
