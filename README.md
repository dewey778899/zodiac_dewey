# Zodiac Dewey

AI compatibility report app with:

- Frontend: static H5 page
- Backend: Spring Boot 3.2 / Java 17
- Models: DeepSeek and Claude Opus 4.7
- Database: SQLite only

## Images

- Backend image: `dwaigx/zodiac-dewey-backend:latest`
- Frontend image: `dwaigx/zodiac-dewey-frontend:latest`

## Quick Start

1. Copy `.env.example` to `.env`
2. Fill in your API keys
3. Start locally or with Docker

### Local backend

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_dev.ps1
```

### Local frontend

```bash
cd frontend
python -m http.server 5173
```

### Docker

```bash
docker compose up -d
```

## SQLite

The project uses SQLite in all environments.

- Local default: `backend/data/zodiac_dewey.db`
- Docker default: `/app/data/zodiac_dewey.db`

There is no H2 or MySQL runtime path in the current setup.

## Key env vars

```bash
AI_API_KEY=your_deepseek_key
CLAUDE_API_KEY=your_claude_key
CLAUDE_MODEL=claude-opus-4-7
DB_URL=jdbc:sqlite:./data/zodiac_dewey.db
DB_DRIVER=org.sqlite.JDBC
HIBERNATE_DIALECT=org.hibernate.community.dialect.SQLiteDialect
HIBERNATE_USE_GET_GENERATED_KEYS=false
JPA_DDL_AUTO=update
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change_me
```

## Health Check

```bash
curl http://localhost:8080/api/health
```

## Docs

- Deployment: [DEPLOYMENT_README.md](DEPLOYMENT_README.md)
- Production notes: [docs/DEPLOY.md](docs/DEPLOY.md)
