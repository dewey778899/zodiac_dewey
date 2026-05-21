#!/bin/bash
# Docker 一键部署脚本（ECS 上用）
set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "===== Docker 部署 zodiac_dewey ====="

# 首次运行交互式配置
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠️ 未检测到 .env，开始引导配置...${NC}"
    echo ""
    read -p "请输入 DeepSeek API Key (必填): " input_ai_key
    read -p "请输入 Claude API Key (可选，直接回车跳过): " input_claude_key

    cat > .env << EOF
AI_API_KEY=${input_ai_key}
AI_API_URL=https://api.deepseek.com/chat/completions
AI_MODEL=deepseek-chat
AI_MAX_TOKENS=8000
AI_TIMEOUT_SECONDS=180
CLAUDE_API_KEY=${input_claude_key}
CLAUDE_API_URL=https://api.anthropic.com/v1/messages
CLAUDE_MODEL=claude-sonnet-4-20250514
CLAUDE_MAX_TOKENS=8000
CLAUDE_TIMEOUT_SECONDS=180
DB_URL=jdbc:h2:file:/app/data/zodiac_dewey;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE
DB_USER=sa
DB_PASSWORD=
DB_DRIVER=org.h2.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.H2Dialect
JPA_DDL_AUTO=update
RATELIMIT_DAILY_TOTAL=200
RATELIMIT_PER_IP=3
RATELIMIT_ENABLED=true
CORS_ALLOWED_ORIGINS=*
SERVER_PORT=8080
H2_CONSOLE_ENABLED=true
EOF
    echo -e "${GREEN}✅ .env 已生成${NC}"
fi

echo "1. 拉取最新镜像..."
docker compose pull

echo "2. 启动容器..."
docker compose up -d

echo "3. 等待启动..."
sleep 5
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health | grep -q '"status":"ok"'; then
        echo -e "${GREEN}✅ 部署成功!${NC}"
        curl -s http://localhost:8080/api/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/api/health
        echo ""
        exit 0
    fi
    sleep 1
done

echo -e "${YELLOW}⚠️ 启动超时，查看日志: docker compose logs${NC}"
