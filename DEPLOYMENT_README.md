# Deployment README

This project uses SQLite everywhere:

- Local: SQLite file at `backend/data/zodiac_dewey.db`
- Docker: SQLite file at `/app/data/zodiac_dewey.db`
- No H2
- No MySQL

## 1. Environment

Copy `.env.example` to `.env` and fill in at least the AI keys you need.

Example:

```bash
AI_API_KEY=your_deepseek_key
AI_API_URL=https://api.deepseek.com/chat/completions
AI_MODEL=deepseek-chat

CLAUDE_API_KEY=your_claude_key
CLAUDE_API_URL=https://api.anthropic.com/v1/messages
CLAUDE_MODEL=claude-opus-4-7

DB_URL=jdbc:sqlite:./data/zodiac_dewey.db
DB_DRIVER=org.sqlite.JDBC
HIBERNATE_DIALECT=org.hibernate.community.dialect.SQLiteDialect
HIBERNATE_USE_GET_GENERATED_KEYS=false
JPA_DDL_AUTO=update

ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace_with_a_strong_password
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

## 2. Local Start

Backend:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_dev.ps1
```

Frontend:

```bash
cd frontend
python -m http.server 5173
```

Open:

- `http://localhost:5173`
- `http://localhost:8080/api/health`

## 3. Build Backend

```bash
cd backend
mvn clean package -DskipTests
```

Jar path:

```bash
backend/target/zodiac-dewey.jar
```

## 4. Run Backend

```bash
java -jar backend/target/zodiac-dewey.jar
```

The SQLite database file will be created automatically on first start.

## 5. Docker

```bash
docker compose up -d
```

The container stores SQLite data in the `sqlite_data` volume mounted to `/app/data`.

## 6. Notes

- `.env` should stay private and must not be committed.
- `backend/data/` contains local SQLite runtime data and should not be committed.
- Without API keys, the app can start but real AI report generation will fail.
