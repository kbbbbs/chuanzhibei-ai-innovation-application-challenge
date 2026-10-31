# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI智学平台 (AI Smart Learning Platform) — a full-stack educational platform with role-based access (admin/teacher/student), AI-assisted learning, exam/homework workflows, and academic paper search. The two apps are developed independently under `ai-learning-frontend/` and `ai-learning-backend/`.

## Commands

### Backend (Spring Boot, Java 21, Maven)

```bash
cd ai-learning-backend

# Run dev server (port 8080)
mvn spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=AgentControllerTest

# Build JAR (skip tests)
mvn -DskipTests package
```

### Frontend (Vue 3, Vite, npm)

```bash
cd ai-learning-frontend

# Install dependencies
npm install

# Run dev server (port 5173)
npm run dev

# Build for production
npm run build

# Run Playwright e2e tests
npx playwright test

# Run a single test file
npx playwright test e2e/login.spec.js
```

### Database Setup (first-time only)

```sql
-- Run against MySQL
source database/ai_academy_export.sql;
```

## Architecture

### Backend

Package root: `com.cupk.aiacademy`

The backend follows a standard Spring layered architecture: `controller → service/impl → mapper → entity`. All controllers return a unified response wrapper from `common/`.

Key modules:

- **`agent/`** — AI agent system (most recently developed). Manages agent definitions, chat sessions (with message history), and streaming SSE responses. Uses Spring AI with the DeepSeek API (OpenAI-compatible). Context limits are enforced per-session by character/message count.
- **`config/`** — Global wiring: `AuthInterceptor` validates JWT on every request, `CorsConfig` allows the frontend dev origin, `WebMvcConfiguration` registers the interceptor and static file serving for uploads.
- **`weknora/`** — Isolated module for the Weknora knowledge-base integration (paper retrieval/recommendation). Calls an external service at `WEKNORA_BASE_URL`.
- **`service/`** — Contains `PermissionService` which is used by controllers to enforce role boundaries; most business logic lives in `service/impl/`.
- **`mapper/`** — MyBatis Plus mappers; SQL logging is on by default (`application.yml`).

Authentication flow: JWT is extracted from the `Authorization: Bearer <token>` header by `AuthInterceptor`, which populates a thread-local user context used downstream by controllers and `PermissionService`.

### Frontend

The frontend uses Vue 3 Composition API throughout.

- **`src/utils/request.js`** — Axios instance with request/response interceptors. Attaches JWT from the Pinia user store; redirects to `/login` on 401.
- **`src/api/`** — One file per domain (exam, homework, knowledgeBase, etc.). All API calls go through the shared Axios instance.
- **`src/router/index.js`** — Route guards check `meta.requiresAuth` and `meta.roles` against the user store. Role values are `admin`, `teacher`, `student`.
- **`src/store/user.js`** — Pinia store; single source of truth for the current user and JWT token.
- **`src/layout/MainLayout.vue`** — Wraps all authenticated views; houses the sidebar and top nav.
- **`src/composables/`** — Reusable logic: `useAsync` for loading/error state, `useConfirm` for Element Plus confirmation dialogs, `useWeKnora` for knowledge-base queries.

Views are split by role under `src/views/teacher/`, `src/views/student/`, and `src/views/admin/`. The router enforces which roles can reach which view.

### Configuration

Backend config is in `ai-learning-backend/src/main/resources/application.yml`. Sensitive values (DB credentials, API keys, JWT secret) can be overridden via environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`, `WEKNORA_BASE_URL`, `WEKNORA_API_KEY`.

The test suite uses `src/test/resources/application.properties` which points to an H2 in-memory database, so backend tests run without a real MySQL instance.

### External Integrations

- **DeepSeek API** (`https://api.deepseek.com`) — LLM backend, accessed via Spring AI's OpenAI adapter. Model: `deepseek-v4-flash`.
- **Weknora** (`http://localhost:8090` by default) — Self-hosted knowledge base for RAG-style paper search.
- **OpenAlex / Semantic Scholar** — Called by `OpenAlexSearchService` and related services for academic paper search (no local service required).
