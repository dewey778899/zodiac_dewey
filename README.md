# 🌙 小登哥的灵魂合盘

> 一个用 AI 生成深度星座合盘报告的 H5 应用，**小登哥出品**。
> 用户输入双方信息（姓名/生日/出生时间/出生地），AI 实时生成 4000+ 字深度合盘报告。
> 支持 **DeepSeek（免费）** 和 **Claude（付费）** 双模型切换。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Docker Build](https://github.com/dewey778899/zodiac_dewey/actions/workflows/docker-build.yml/badge.svg)](https://github.com/dewey778899/zodiac_dewey/actions/workflows/docker-build.yml)

---

## ✨ 功能特性

- 🤖 **双模型切换**：DeepSeek 免费体验 + Claude 高品质付费，一键切换
- 💰 **付费解锁**：Claude 模式接入微信/支付宝收款码，用户扫码即用
- 🎴 **真实 AI 报告**：每份报告由 AI 实时生成，4000-5000 字深度内容
- 🌟 **三大星座**：太阳星座 + 月亮星座 + 上升星座（基于出生时间计算）
- 💎 **珍藏锦囊**：6 条可直接收藏的精华相处建议
- 🆔 **独一无二编号**：每份报告唯一编号（如 `N° SASC-260520-RT93`），仪式感拉满
- 📤 **分享卡片**：Canvas 原生绘制精美分享图，一键保存转发
- 📄 **PDF 下载**：多页高清 PDF，retina 级清晰
- 💌 **私域引流**：报告结尾可填微信号，自动入库
- 🔒 **数据持久化**：H2 本地存储 / MySQL，JPA 自动建表
- 🛡️ **限流保护**：全局每日总额度 + 单 IP 限制，防止 API 成本失控
- 🎨 **温柔治愈风**：蜜桃粉 + 星光紫，小登哥手书风格署名

---

## 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| 前端 | 纯 HTML + Canvas 2D API，无框架，单文件 |
| 后端 | Spring Boot 3.2 + Java 17 |
| 数据库 | H2（开箱即用）/ MySQL + JPA |
| AI · 免费 | DeepSeek Chat（OpenAI 兼容接口） |
| AI · 付费 | Claude Sonnet 4（Anthropic 接口） |
| 限流 | Caffeine 内存缓存（无需 Redis） |
| PDF | jsPDF + 原生 Canvas |
| 部署 | Docker / Nginx + systemd |

---

## 📂 项目结构

```
zodiac_dewey/
├── backend/                    Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/zodiac/api/
│       │   ├── controller/    REST 接口
│       │   ├── service/       AI 调用（DeepSeek + Claude）、限流、报告生成
│       │   ├── entity/        数据库实体
│       │   ├── repository/    JPA 仓库
│       │   ├── dto/           请求/响应
│       │   ├── util/          星座计算工具
│       │   ├── config/        CORS、启动监听
│       │   └── exception/     全局异常处理
│       └── resources/
│           └── application.yml
├── frontend/                   前端（单文件 H5）
│   ├── index.html             主页面（含双模型切换 + 支付弹窗）
│   └── img/                   支付二维码图片
├── scripts/                    运维脚本
│   ├── deploy.sh              Docker 一键部署
│   ├── docker-deploy.sh       Docker 交互式部署
│   ├── start_dev.sh           开发启动（Linux/Mac）
│   └── start_dev.ps1          开发启动（Windows）
├── sql/                        建表 SQL
│   └── init.sql
├── docs/                       详细文档
├── Dockerfile                  多阶段构建
├── docker-compose.yml          Docker Compose 编排
├── .env.example                环境变量模板
├── .gitignore
└── README.md
```

---

## 🚀 快速开始（本地开发）

### 前置要求

- JDK 17+
- Maven 3.8+
- DeepSeek API Key（[点此申请](https://platform.deepseek.com)）

### 步骤

```bash
# 1. 克隆
git clone https://github.com/dewey778899/zodiac_dewey.git
cd zodiac_dewey

# 2. 配置环境变量
cp .env.example .env
vim .env   # 填入 AI_API_KEY 等配置

# 3. 启动后端
# Linux / Mac
bash scripts/start_dev.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File .\scripts\start_dev.ps1

# 4. 启动前端
cd frontend && python3 -m http.server 5173
# 浏览器打开 http://localhost:5173
```

---

## 🐳 Docker 一键部署（推荐）

> **镜像地址：`dwaigx/zodiac-dewey:latest`**
>
> Docker Hub：[https://hub.docker.com/r/dwaigx/zodiac-dewey](https://hub.docker.com/r/dwaigx/zodiac-dewey)

### 方式 A：docker run（最简）

```bash
docker run -d \
  --name zodiac_dewey \
  --restart unless-stopped \
  -p 8080:8080 \
  -e AI_API_KEY=你的DeepSeek密钥 \
  -v zodiac_data:/app/data \
  dwaigx/zodiac-dewey:latest
```

访问 `http://服务器IP:8080`

### 方式 B：docker-compose（推荐）

```bash
# 1. 下载 docker-compose.yml
curl -O https://raw.githubusercontent.com/dewey778899/zodiac_dewey/main/docker-compose.yml

# 2. 创建 .env 配置文件
cat > .env << EOF
AI_API_KEY=你的DeepSeek密钥
AI_API_URL=https://api.deepseek.com/chat/completions
AI_MODEL=deepseek-chat
CLAUDE_API_KEY=你的Claude密钥（可选，不填则仅DeepSeek可用）
CORS_ALLOWED_ORIGINS=*
EOF

# 3. 启动
docker-compose up -d

# 4. 查看日志
docker-compose logs -f
```

### 升级镜像

```bash
docker-compose pull && docker-compose up -d
```

### 镜像自动构建

每次推送到 `main` 分支，GitHub Actions 自动构建并更新 Docker Hub 镜像。

---

## 🤖 双模型配置

### DeepSeek（免费，默认）

开箱即用，只需配置 `AI_API_KEY`：

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `AI_API_KEY` | DeepSeek API Key | 必填 |
| `AI_API_URL` | API 地址 | `https://api.deepseek.com/chat/completions` |
| `AI_MODEL` | 模型名 | `deepseek-chat` |
| `AI_MAX_TOKENS` | 最大输出 token | `8000` |

### Claude（付费）

用户切换到 Claude 模式时弹出支付二维码，扫码付款后手动确认解锁。

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `CLAUDE_API_KEY` | Claude API Key | 可选，不填则 Claude 不可用 |
| `CLAUDE_API_URL` | API 地址 | `https://api.anthropic.com/v1/messages` |
| `CLAUDE_MODEL` | 模型名 | `claude-sonnet-4-20250514` |
| `CLAUDE_MAX_TOKENS` | 最大输出 token | `8000` |

> 💡 **关于支付验证**：当前为第一阶段纯前端验证（localStorage 标记），适合 MVP 快速上线。后续可升级为订单号 + 后端校验或微信/支付宝官方支付回调。

---

## 🔌 API 接口

### `POST /api/compatibility` - 生成合盘报告

请求:
```json
{
  "maleBirth": "1990-01-15",
  "femaleBirth": "1992-06-20",
  "model": "deepseek"
}
```

`model` 可选值：`deepseek`（默认）、`claude`

返回:
```json
{
  "score": 88,
  "relationshipType": "灵魂深度互补型",
  "tagline": "...",
  "chapters": [...],
  "essence": [...],
  "reportUid": "N° SASC-260520-RT93",
  "zodiacA": {"sun":"射手座","moon":"双鱼座","rising":"狮子座"},
  "zodiacB": {"sun":"天蝎座","moon":"巨蟹座","rising":"水瓶座"}
}
```

### `POST /api/wechat` - 用户提交微信号（私域引流）

```json
{
  "reportUid": "N° SASC-260520-RT93",
  "wechatId": "xiaodengge_001"
}
```

### `GET /api/health` - 健康检查

```json
{
  "status": "ok",
  "service": "zodiac-api",
  "global_used": 12,
  "global_total": 200
}
```

完整接口见 **[docs/API.md](docs/API.md)**

---

## 💰 成本估算

### DeepSeek（免费模式）

| 用量级别 | 每日次数 | 月成本 |
|---------|---------|-------|
| 试水期 | 50 / 日 | ~¥600 / 月 |
| 小爆款 | 200 / 日（默认上限） | ~¥2400 / 月 |
| 大爆款 | 1000 / 日（需调整限流） | ~¥12000 / 月 |

### Claude（付费模式）

Claude 成本约为 DeepSeek 的 5-8 倍，建议定价 ¥9.9/次覆盖成本 + 利润。

**省钱建议**：先用 `deepseek-chat` 跑通，追求推理质量可换 `deepseek-reasoner`，修改 `.env` 中的 `AI_MODEL` 即可。

---

## 🔍 后台运营查询

```sql
-- 今天有多少人测了
SELECT COUNT(*) FROM soulmate_report WHERE DATE(created_at) = CURDATE();

-- 今天有哪些人留了微信（私域线索）
SELECT user_a_name, user_b_name, wechat_id, score, created_at
FROM soulmate_report
WHERE wechat_id IS NOT NULL AND DATE(created_at) = CURDATE()
ORDER BY created_at DESC;

-- 最受欢迎的星座组合
SELECT CONCAT(zodiac_a, ' × ', zodiac_b) AS pair, COUNT(*) c
FROM soulmate_report
GROUP BY zodiac_a, zodiac_b
ORDER BY c DESC LIMIT 10;
```

---

## 🌐 生产部署

详见 **[docs/DEPLOY.md](docs/DEPLOY.md)**

---

## 📜 License

MIT © 2026 小登哥

---

## 🙋 联系

- 抖音：[@小登哥](https://www.douyin.com/user/小登哥) ♐♏
- GitHub Issues：有问题直接提
