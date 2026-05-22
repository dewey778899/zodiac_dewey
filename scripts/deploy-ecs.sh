#!/bin/bash
# zodiac_dewey - ACR 部署脚本 (阿里云 ECS)
# 用法: ./deploy.sh [tag]
#   tag 留空则拉取 latest

set -e

REGISTRY="registry.cn-shanghai.aliyuncs.com"
NAMESPACE="dewey_zodiac"
TAG="${1:-latest}"

# ECS 内网地址（上海同域 ECS 走内网，速度快）
# 如果你的 ECS 不在上海，改为公网地址: registry.cn-shanghai.aliyuncs.com
VPC_REGISTRY="registry-vpc.cn-shanghai.aliyuncs.com"

# 自动检测是否在阿里云 ECS 内网
if curl -s --connect-timeout 2 "${VPC_REGISTRY}/v2/" > /dev/null 2>&1; then
    DOCKER_REGISTRY="${VPC_REGISTRY}"
    echo "✅ 检测到内网环境，使用 VPC 内网地址拉取镜像"
else
    DOCKER_REGISTRY="${REGISTRY}"
    echo "⚠️ 非内网环境，使用公网地址拉取镜像"
fi

echo "📦 拉取镜像 (tag: ${TAG})..."

docker pull "${DOCKER_REGISTRY}/${NAMESPACE}/zodiac-dewey-backend:${TAG}"
docker pull "${DOCKER_REGISTRY}/${NAMESPACE}/zodiac-dewey-frontend:${TAG}"

# 重新 tag 为公网地址（docker-compose 用公网地址引用）
docker tag "${DOCKER_REGISTRY}/${NAMESPACE}/zodiac-dewey-backend:${TAG}" "${REGISTRY}/${NAMESPACE}/zodiac-dewey-backend:${TAG}"
docker tag "${DOCKER_REGISTRY}/${NAMESPACE}/zodiac-dewey-frontend:${TAG}" "${REGISTRY}/${NAMESPACE}/zodiac-dewey-frontend:${TAG}"

echo "🚀 重启服务..."
docker compose down
docker compose up -d

echo "🧹 清理旧镜像..."
docker image prune -f

echo "✅ 部署完成!"
docker compose ps
