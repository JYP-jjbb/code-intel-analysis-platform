# Architecture

## Overview

The platform is a two-page web system:
- Home: product intro + entry point.
- Workbench: left navigation, top bar, main split view (operation + display).

Two core workspaces share a unified task model but use different executors:
- NuTera 形式化验证
- 工程代码审查

## Modules

### Backend (Spring Boot 2.7 / Java 11)

- `api`: REST controllers and DTOs.
- `task`: task model, status flow, repository.
- `executor`: task executors (NuTera / CodeReview).
- `adapter`: NuTera runtime adapter.
- `storage`: workspace manager.
- `demo`: demo task seeding.

### Frontend (Vue 3 + Vite + Element Plus)

- `layouts`: workbench layout.
- `pages`: Home, workspaces, placeholders.
- `components`: shared UI blocks.
- `api`: REST clients.

## Task Model

Fields:
- `taskId`
- `taskType`
- `status`
- `sourcePath`
- `resultPath`
- `logPath`
- `createdAt` / `updatedAt`

## Execution Flow

1. Submit task from UI.
2. Task record created and queued.
3. Executor runs (mock or real).
4. Logs written to `run.log`.
5. Results written to `result.json` + artifacts.
6. Frontend polls status/log/result.

## NuTera Integration

- NuTera runtime is kept intact under `runtimes/nutera`.
- Adapter supports `MOCK`, `LOCAL`, `DOCKER`.
- Artifacts copied to `workspace/tasks/<taskId>/artifacts`:
  - `final_summary.txt`
  - `program_level.csv`
  - `attempt_level.csv`
  - `checkpoint.pkl`
