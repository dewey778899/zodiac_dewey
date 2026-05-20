# Deployment README

This project now runs with:

- Backend: Spring Boot 3.2 / Java 17
- Frontend: static `frontend/index.html`
- AI provider: DeepSeek Chat Completions API
- Local database: H2 file database
- Production database: MySQL recommended

## 1. Environment

Copy `.env.example` to `.env` and fill in your values.

Required for real report generation:

```bash
AI_API_KEY=your_deepseek_key
AI_API_URL=https://api.deepseek.com/chat/completions
AI_MODEL=deepseek-chat
```

For local development, the default H2 configuration is enough.

For production, switch to MySQL:

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/zodiac_dewey?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USER=zodiac
DB_PASSWORD=strong_password
DB_DRIVER=com.mysql.cj.jdbc.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect
JPA_DDL_AUTO=update
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

## 4. Production Run

Start the jar with the `.env` variables loaded into the shell or your process manager:

```bash
java -jar backend/target/zodiac-dewey.jar
```

## 5. Nginx

Serve the frontend as static files and reverse proxy `/api/` to the Spring Boot service on `127.0.0.1:8080`.

Example:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /var/www/zodiac;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }
}
```

## 6. Notes

- `.env` is ignored by git and should stay private.
- `backend/data/` is local H2 runtime data and should not be committed.
- Without `AI_API_KEY`, the service can start but report generation will fail.
