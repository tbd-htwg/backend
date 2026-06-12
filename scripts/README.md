# Local JVM dev (`dev.sh`)

Start all backend services without Kubernetes.

## Prerequisites

- Java 21, Maven, Docker, `gcloud` (Firestore emulator), Node.js (frontend)
- Ports free: **8080–8083**, **6379**, **9090**

## Quick start

```bash
# 1. Optional: copy env template (defaults work for a smoke test)
cp docs/gettingstarted/.env.example docs/gettingstarted/.env
# Edit JWT_SECRET (≥32 chars) if you create .env

# 2. Backend
./scripts/dev.sh start

# 3. Frontend (repo root)
cd ../frontend && npm run dev
```

Open http://localhost:5173

## Commands

| Command | Description |
|---------|-------------|
| `./scripts/dev.sh start` | Start Valkey, Firestore emulator, all JVM services |
| `./scripts/dev.sh stop` | Stop everything |
| `./scripts/dev.sh restart` | Stop + start |
| `./scripts/dev.sh status` | Show running services |
| `./scripts/dev.sh logs social` | Tail one service log |

Logs: `backend/.jvm-dev/logs/`

## Service ports

| Service | Port |
|---------|------|
| trip | 8080 |
| social | 8081 |
| external-info | 8082 |
| platform | 8083 |

Vite (`npm run dev`) proxies `/api/v2` to these ports — see `frontend/.env.development`.

## Env files (auto-loaded)

1. `docs/gettingstarted/.env`
2. `backend/.env.local`

See `.env.example` for the full list. **Required for real login:** Firebase web config in `frontend/.env` (`VITE_FIREBASE_*`).
