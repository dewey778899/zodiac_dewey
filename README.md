# 🌙 小登哥的灵魂合盘

> AI 实时生成深度星座合盘报告，支持 DeepSeek（免费）+ Claude（付费）双模型。

[![Docker Build](https://github.com/dewey778899/zodiac_dewey/actions/workflows/docker-build.yml/badge.svg)](https://github.com/dewey778899/zodiac_dewey/actions/workflows/docker-build.yml)

---

## 🐳 一键部署

### 前后端双镜像

| 镜像 | 地址 |
|------|------|
| 后端 | `dwaigx/zodiac-dewey-backend:latest` |
| 前端 | `dwaigx/zodiac-dewey-frontend:latest` |

### docker-compose（推荐）

```bash
# 1. 创建配置
cat > .env << EOF
AI_API_KEY=你的DeepSeek密钥
CLAUDE_API_KEY=你的Claude密钥(可选)
CORS_ALLOWED_ORIGINS=*
EOF

# 2. 下载编排文件
curl -O https://raw.githubusercontent.com/dewey778899/zodiac_dewey/main/docker-compose.yml

# 3. 启动
docker-compose up -d

# 4. 访问
# http://服务器IP
```

### docker run

```bash
# 后端
docker run -d --name zodiac_backend --restart unless-stopped \
  -e AI_API_KEY=你的DeepSeek密钥 \
  -e CORS_ALLOWED_ORIGINS='*' \
  -v zodiac_data:/app/data \
  dwaigx/zodiac-dewey-backend:latest

# 前端（Nginx，自动代理 /api/ 到后端）
docker run -d --name zodiac_frontend --restart unless-stopped \
  --link zodiac_backend:backend \
  -p 80:80 \
  dwaigx/zodiac-dewey-frontend:latest
```

### 升级

```bash
docker-compose pull && docker-compose up -d
```

---

## 🤖 模型配置

### DeepSeek（免费，默认）

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `AI_API_KEY` | API Key | **必填** |
| `AI_API_URL` | API 地址 | `https://api.deepseek.com/chat/completions` |
| `AI_MODEL` | 模型名 | `deepseek-chat` |

### Claude（付费）

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `CLAUDE_API_KEY` | API Key | 可选 |
| `CLAUDE_MODEL` | 模型名 | `claude-sonnet-4-5-20250929` |
| `AI_PROXY_HOST` | 代理地址（国内访问 Claude 需要） | 空 |
| `AI_PROXY_PORT` | 代理端口 | `0` |

---

## 📂 项目结构

```
zodiac_dewey/
├── backend/           Spring Boot 3.2 + Java 17 + H2
│   ├── Dockerfile
│   └── src/
├── frontend/          纯 HTML + Canvas，Nginx 部署
│   ├── Dockerfile
│   └── img/           支付二维码
├── docker-compose.yml
└── .github/workflows/ CI 自动构建推送镜像
```

---

## 📜 License

MIT © 2026 小登哥
