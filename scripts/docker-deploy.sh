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
    read -p "请输入前端域名(如 https://your-domain.com，留空则仅允许同域访问): " input_cors_origin
    read -p "请输入后台管理员用户名(默认 admin): " input_admin_username
    read -p "请输入后台管理员密码(留空则使用示例弱密码，请务必手工修改): " input_admin_password

    if [ -z "$input_admin_username" ]; then
        input_admin_username="admin"
    fi
    if [ -z "$input_admin_password" ]; then
        input_admin_password="change_admin_password"
    fi

    cat > .env << EOF
AI_API_KEY=${input_ai_key}
AI_API_URL=https://api.deepseek.com/chat/completions
AI_MODEL=deepseek-chat
AI_MAX_TOKENS=8000
AI_TIMEOUT_SECONDS=180
CLAUDE_API_KEY=${input_claude_key}
CLAUDE_API_URL=https://api.anthropic.com/v1/messages
CLAUDE_MODEL=claude-opus-4-7
CLAUDE_MAX_TOKENS=8000
CLAUDE_TIMEOUT_SECONDS=180
DB_URL=jdbc:sqlite:/app/data/zodiac_dewey.db
DB_DRIVER=org.sqlite.JDBC
HIBERNATE_DIALECT=org.hibernate.community.dialect.SQLiteDialect
HIBERNATE_USE_GET_GENERATED_KEYS=false
JPA_DDL_AUTO=update
RATELIMIT_DAILY_TOTAL=200
RATELIMIT_PER_IP=3
RATELIMIT_ENABLED=true
CORS_ALLOWED_ORIGINS=${input_cors_origin}
SERVER_PORT=8080
ADMIN_USERNAME=${input_admin_username}
ADMIN_PASSWORD=${input_admin_password}
ADMIN_SESSION_HOURS=12
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
