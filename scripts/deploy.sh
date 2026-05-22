#!/bin/bash
# 生产环境部署脚本
# 在 ECS 服务器上执行

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "===== 部署到生产环境 ====="

# 交互式配置（首次运行自动引导）
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠️ 未检测到 .env 配置文件，开始引导配置...${NC}"
    echo ""
    read -p "请输入 DeepSeek API Key (必填): " input_ai_key
    read -p "请输入 Claude API Key (可选，直接回车跳过): " input_claude_key
    read -p "请输入后台管理员密码(留空则使用示例弱密码，请务必手工修改): " input_admin_password
    read -p "请输入前端域名(如 https://your-domain.com): " input_cors_origin

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
DB_URL=jdbc:sqlite:./data/zodiac_dewey.db
DB_DRIVER=org.sqlite.JDBC
HIBERNATE_DIALECT=org.hibernate.community.dialect.SQLiteDialect
HIBERNATE_USE_GET_GENERATED_KEYS=false
JPA_DDL_AUTO=update
RATELIMIT_DAILY_TOTAL=200
RATELIMIT_PER_IP=3
RATELIMIT_ENABLED=true
CORS_ALLOWED_ORIGINS=${input_cors_origin}
SERVER_PORT=8080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=${input_admin_password}
EOF
    echo -e "${GREEN}✅ .env 配置文件已生成${NC}"
fi

export $(grep -v '^#' .env | xargs)

# 1. 拉最新代码
echo "1. 拉取最新代码..."
git pull origin main

# 2. 打包后端
echo "2. 打包后端..."
cd backend
mvn clean package -DskipTests
cd ..

# 3. 部署前端(假设 Nginx 已配置静态目录)
echo "3. 部署前端..."
FRONTEND_DIR=${FRONTEND_DIR:-/var/www/zodiac}
sudo mkdir -p "$FRONTEND_DIR/img"
sudo cp frontend/index.html "$FRONTEND_DIR/index.html"
sudo cp -r frontend/img/* "$FRONTEND_DIR/img/"
echo "✓ 前端已部署到 $FRONTEND_DIR"

# 4. 重启后端
echo "4. 重启后端服务..."
PID=$(ps -ef | grep zodiac-dewey.jar | grep -v grep | awk '{print $2}' || true)
if [ -n "$PID" ]; then
    echo "停止旧进程 PID=$PID"
    kill "$PID"
    sleep 3
fi

# 后台启动
nohup java -Xms256m -Xmx512m -jar backend/target/zodiac-dewey.jar \
    > logs/app.log 2>&1 &

NEW_PID=$!
echo "✓ 新进程 PID=$NEW_PID,日志在 logs/app.log"

# 等待启动
echo "等待服务启动..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health | grep -q '"status":"ok"'; then
        echo -e "${GREEN}✅ 部署成功!${NC}"
        curl -s http://localhost:8080/api/health
        echo ""
        exit 0
    fi
    sleep 1
done

echo -e "${YELLOW}⚠️ 服务启动超时,请查看 logs/app.log${NC}"
tail -50 logs/app.log
exit 1
