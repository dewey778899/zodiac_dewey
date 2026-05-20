#!/bin/bash
# 生产环境部署脚本
# 在 ECS 服务器上执行

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "===== 部署到生产环境 ====="

# 加载环境变量
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
else
    echo "❌ 缺少 .env"
    exit 1
fi

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
sudo mkdir -p "$FRONTEND_DIR"
sudo cp frontend/index.html "$FRONTEND_DIR/index.html"
echo "✓ 前端已部署到 $FRONTEND_DIR"

# 4. 重启后端
echo "4. 重启后端服务..."
PID=$(ps -ef | grep zodiac-api.jar | grep -v grep | awk '{print $2}' || true)
if [ -n "$PID" ]; then
    echo "停止旧进程 PID=$PID"
    kill "$PID"
    sleep 3
fi

# 后台启动
nohup java -Xms256m -Xmx512m -jar backend/target/zodiac-api.jar \
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
