#!/bin/bash
# 开发环境一键启动脚本

set -e

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}===== 小登哥灵魂合盘 启动中 =====${NC}"

# 加载 .env
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
    echo "✓ 加载 .env 配置"
else
    echo -e "${RED}❌ 缺少 .env 文件,请复制 .env.example 为 .env 并填写${NC}"
    exit 1
fi

# 检查 API Key
if [ "$CLAUDE_API_KEY" = "sk-ant-api03-请替换为你的真实Key" ] || [ -z "$CLAUDE_API_KEY" ]; then
    echo -e "${RED}❌ 请在 .env 里填入真实的 CLAUDE_API_KEY${NC}"
    exit 1
fi

# 检查 MySQL 连接
echo "检查 MySQL 连接..."
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" -e "SELECT 1" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  MySQL 连接失败,请确认服务已启动且密码正确${NC}"
    echo "首次安装请执行: bash scripts/init_db.sh"
    exit 1
fi
echo "✓ MySQL 连接成功"

# 启动后端
cd backend
echo "启动后端..."
mvn spring-boot:run
