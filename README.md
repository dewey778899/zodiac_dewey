# 🌙 小登哥的灵魂合盘

> 一个用 Claude AI 生成深度星座合盘报告的 H5 应用,**小登哥出品**。
> 用户输入双方信息(姓名/生日/出生时间/出生地),AI 生成 4000+ 字深度合盘报告。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ 功能特性

- 🎴 **真实 AI 报告**:接入 Claude 官方 API,每份报告由 AI 实时生成,4000-5000 字深度内容
- 🌟 **三大星座**:不只算太阳星座,还有月亮星座、上升星座(基于出生时间)
- 💎 **珍藏锦囊**:6 条可直接收藏的精华相处建议,易记易用
- 🆔 **独一无二编号**:每份报告唯一编号(如 `N° SASC-260520-RT93`),仪式感拉满
- 📤 **分享卡片**:Canvas 原生绘制的精美分享图,一键保存到相册转发
- 📄 **PDF 下载**:多页高清 PDF,retina 清晰
- 💌 **私域引流**:报告结尾可填微信号,自动入库
- 🔒 **数据持久化**:所有报告 + 用户数据存入 MySQL
- 🛡️ **限流保护**:全局每日总额度 + 单 IP 限制,防止 API 成本失控
- 🎨 **温柔治愈风**:蜜桃粉 + 星光紫,小登哥手书风格署名

---

## 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| 前端 | 纯 HTML + Canvas 2D API,无任何前端框架,单文件 |
| 后端 | Spring Boot 3.2 + Java 17 |
| 数据库 | MySQL 8 + JPA |
| AI | Claude Opus 4.5(可换成其他模型) |
| 限流 | Caffeine 内存缓存(无需 Redis) |
| PDF | jsPDF + 原生 Canvas |
| 部署 | Nginx + systemd / Docker |

---

## 📂 项目结构

```
zodiac_dewey/
├── backend/                    Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/zodiac/api/
│       │   ├── controller/    REST 接口
│       │   ├── service/       业务(Claude 调用、限流、报告生成)
│       │   ├── entity/        数据库实体
│       │   ├── repository/    JPA 仓库
│       │   ├── dto/           请求/响应
│       │   ├── util/          星座计算工具
│       │   ├── config/        CORS、启动监听
│       │   └── exception/     全局异常处理
│       └── resources/
│           └── application.yml
├── frontend/                   前端(单文件 H5)
│   └── index.html
├── sql/                        建表 SQL
│   └── init.sql
├── scripts/                    运维脚本
│   ├── init_db.sh              数据库初始化
│   ├── start_dev.sh            开发启动
│   └── deploy.sh               生产部署
├── docs/                       详细文档
│   ├── DEPLOY.md               生产部署完整指南
│   ├── API.md                  API 接口文档
│   └── NGINX.md                Nginx 配置参考
├── .env.example                环境变量模板(必须复制为 .env)
├── .gitignore
└── README.md                   本文件
```

---

## 🚀 快速开始(本地开发)

### 前置要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Claude API Key([这里申请](https://console.anthropic.com))

### 步骤

```bash
# 1. 克隆
git clone https://github.com/dewey778899/zodiac_dewey.git
cd zodiac_dewey

# 2. 配置环境变量
cp .env.example .env
vim .env   # 填入 CLAUDE_API_KEY、数据库密码等

# 3. 初始化数据库(首次)
bash scripts/init_db.sh

# 4. 启动后端
bash scripts/start_dev.sh

# 5. 前端访问
# 方法 A:开新终端,跑静态服务
cd frontend && python3 -m http.server 5173
# 浏览器打开 http://localhost:5173

# 方法 B:直接用后端访问(需要把 frontend 复制到 backend/src/main/resources/static/)
# 浏览器打开 http://localhost:8080
```

---

## 🌐 生产部署(ECS 服务器)

详见 **[docs/DEPLOY.md](docs/DEPLOY.md)**

简版流程:

```bash
# 在 ECS 上
git clone https://github.com/dewey778899/zodiac_dewey.git
cd zodiac_dewey

# 配置
cp .env.example .env && vim .env

# 初始化数据库
bash scripts/init_db.sh

# 部署(一键)
bash scripts/deploy.sh
```

---

## 🔌 API 接口

### `POST /api/compatibility` - 生成合盘报告

请求:
```json
{
  "personA": {
    "name": "dewey",
    "gender": "male",
    "birthDate": "1986-12-10",
    "birthTime": "14:30",
    "birthPlace": "北京"
  },
  "personB": {
    "name": "snow",
    "gender": "female",
    "birthDate": "1988-11-06",
    "birthTime": "10:15",
    "birthPlace": "上海"
  }
}
```

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

### `POST /api/wechat` - 用户提交微信号(私域引流)

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

| 用量级别 | 每日次数 | 月成本(Claude Opus 4.5) |
|---|---|---|
| 试水期 | 50 / 日 | ¥600 / 月 |
| 小爆款 | 200 / 日(默认上限) | ¥2400 / 月 |
| 大爆款 | 1000 / 日(需调整限流) | ¥12000 / 月 |

**省钱建议**:跑通后切换到 `claude-haiku-4-5` 或 `claude-sonnet-4-5`,成本降低 5-10 倍。

修改 `.env` 里的 `CLAUDE_MODEL=claude-haiku-4-5-20251001`,重启即可。

---

## 🔍 后台运营查询(给小登哥用)

```sql
-- 今天有多少人测了
SELECT COUNT(*) FROM soulmate_report WHERE DATE(created_at) = CURDATE();

-- 今天有哪些人留了微信(私域线索)
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

## 📜 License

MIT © 2026 小登哥

---

## 🙋 联系

- 抖音:[@小登哥](https://www.douyin.com/user/小登哥) ♐♏
- GitHub Issues:有问题直接提
