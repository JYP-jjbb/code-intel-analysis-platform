# 代码智能分析与验证平台

## Quick Start

### Backend

```bash
mvn -f backend/pom.xml spring-boot:run
```

- 默认端口：`8080`
- 配置文件：`backend/src/main/resources/application.yml`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

- 默认端口：`5173`
- 后端代理：`/api -> http://localhost:8080`

## Workspace Layout

```
backend/   Spring Boot 2.7 (Java 11)
frontend/  Vue 3 + Vite + Element Plus
runtimes/  NuTera runtime (Python/Docker)
workspace/ Task working dirs and artifacts
```

## Task Flow

- `taskId` is generated on submit.
- Status flow: `PENDING -> RUNNING -> SUCCESS | FAILED`.
- Logs and result artifacts are written under `workspace/tasks/<taskId>/`.

## NuTera Adapter

Configuration in `application.yml`:

```
app:
  nutera:
    mode: MOCK | LOCAL | DOCKER
    runtimePath: runtimes/nutera
    pythonCommand: python3
    dockerImage: nutera
```

### Modes

- `MOCK`: Read existing outputs under `runtimes/nutera/results` and copy artifacts.
- `LOCAL`: Execute NuTera with local Python runtime.
- `DOCKER`: Execute NuTera via Docker image.

## Demo Data

Demo tasks are created at startup when `app.demo.enabled: true`.
Set it to `false` to disable demo seeding.
