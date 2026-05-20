#!/bin/bash
# 数据库初始化脚本(首次安装运行)
# 需要 MySQL root 权限

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "===== 初始化 MySQL 数据库 ====="

read -p "MySQL root 密码: " -s MYSQL_ROOT_PWD
echo ""

# 加载 .env 拿用户名密码
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
else
    echo -e "${RED}❌ 缺少 .env 文件${NC}"
    exit 1
fi

# 创建数据库 + 用户 + 授权
mysql -uroot -p"$MYSQL_ROOT_PWD" <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME:-zodiac_dewey}
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '${DB_USER:-zodiac}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME:-zodiac_dewey}.* TO '${DB_USER:-zodiac}'@'%';
FLUSH PRIVILEGES;
SQL

echo -e "${GREEN}✓ 数据库 ${DB_NAME:-zodiac_dewey} 已创建${NC}"
echo -e "${GREEN}✓ 用户 ${DB_USER:-zodiac} 已授权${NC}"

# 加载表结构(可选,因为 JPA 启动时会自动建)
mysql -u"${DB_USER:-zodiac}" -p"$DB_PASSWORD" "${DB_NAME:-zodiac_dewey}" < sql/init.sql
echo -e "${GREEN}✓ 表结构已初始化${NC}"

echo ""
echo "下一步:bash scripts/start_dev.sh"
