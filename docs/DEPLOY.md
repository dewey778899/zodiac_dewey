# 🚀 生产部署完整指南

> 本文档假设你有一台已就绪的 ECS 服务器(Ubuntu 22.04 或 CentOS 7+),并且**已有可公网访问的 HTTPS 域名**。
> 整个部署预计 **1-2 小时**完成。

---

## 一、部署前清单

确认以下事项都已就绪:

- [ ] ECS 服务器(2 核 4G 以上,公网带宽 1Mbps+)
- [ ] **已有 HTTPS 域名**(微信里能访问 HTTPS,不能用 HTTP)
- [ ] SSL 证书已配置或准备好
- [ ] 安全组开放端口:**80, 443**(对外),22(SSH)
- [ ] 你的 **Claude API Key**(去 https://console.anthropic.com 申请)
- [ ] Anthropic 账户已充值($5 起,够测 100+ 次)

---

## 二、服务器环境准备

### 1. 安装基础软件

#### Ubuntu / Debian

```bash
# 更新包
sudo apt update && sudo apt upgrade -y

# JDK 17
sudo apt install -y openjdk-17-jdk
java -version    # 验证

# Maven
sudo apt install -y maven
mvn -version

# MySQL 8
sudo apt install -y mysql-server
sudo systemctl enable mysql && sudo systemctl start mysql

# Nginx
sudo apt install -y nginx
sudo systemctl enable nginx && sudo systemctl start nginx

# Git
sudo apt install -y git

# Certbot(免费 HTTPS 证书,可选)
sudo apt install -y certbot python3-certbot-nginx
```

#### CentOS / Rocky / AlmaLinux

```bash
sudo yum install -y java-17-openjdk-devel maven mysql-server nginx git
sudo systemctl enable --now mysqld nginx
```

### 2. 配置 MySQL

```bash
# 首次安全配置(Ubuntu)
sudo mysql_secure_installation

# CentOS 首次找 root 临时密码
sudo grep 'temporary password' /var/log/mysqld.log
```

设置 root 密码后,登录验证:
```bash
mysql -uroot -p
```

### 3. 创建项目用户(推荐,不要用 root 跑应用)

```bash
sudo useradd -m -s /bin/bash zodiac
sudo passwd zodiac
# 把 SSH 公钥添加到 ~/.ssh/authorized_keys
```

---

## 三、拉代码 + 配置

### 1. 切换到项目用户

```bash
su - zodiac
# 或 SSH 直接登录 zodiac 账号
```

### 2. 克隆代码

```bash
cd ~
git clone https://github.com/dewey778899/zodiac_dewey.git
cd zodiac_dewey
```

### 3. 配置环境变量

```bash
cp .env.example .env
vim .env
```

**必须填写的字段**:

```bash
# Claude
CLAUDE_API_KEY=sk-ant-api03-你的真实key
CLAUDE_MODEL=claude-opus-4-5    # 或 claude-haiku-4-5-20251001 省钱

# 数据库(改成强密码!)
DB_HOST=localhost
DB_PORT=3306
DB_NAME=zodiac_dewey
DB_USER=zodiac
DB_PASSWORD=请设置一个强密码至少16位

# 限流(可按需调整)
RATELIMIT_DAILY_TOTAL=200    # 每天全局生成上限,超了不再调 Claude
RATELIMIT_PER_IP=3            # 单 IP 每天最多 3 次

# 前端域名(用于 CORS)
FRONTEND_ORIGIN=https://你的域名.com
```

### 4. 初始化数据库

```bash
bash scripts/init_db.sh
# 会提示输入 MySQL root 密码
# 自动创建数据库、用户、表
```

验证:
```bash
mysql -u zodiac -p
# 输入 DB_PASSWORD

mysql> SHOW DATABASES;
# 应该看到 zodiac_dewey
mysql> USE zodiac_dewey;
mysql> SHOW TABLES;
# 应该看到 soulmate_report
```

---

## 四、构建 & 启动后端

### 1. 打包

```bash
cd ~/zodiac_dewey/backend
mvn clean package -DskipTests

# 完成后会生成 target/zodiac-api.jar
ls -lh target/zodiac-api.jar
```

### 2. 测试启动

```bash
cd ~/zodiac_dewey

# 加载环境变量
export $(grep -v '^#' .env | xargs)

# 启动
java -jar backend/target/zodiac-api.jar
```

**看到 `Started ZodiacApiApplication` 就成功**。

新开终端测试:
```bash
curl http://localhost:8080/api/health
# {"status":"ok","service":"zodiac-api","global_used":0,"global_total":200}
```

按 Ctrl+C 停止,接下来配置成系统服务。

### 3. 配置 systemd(后台运行 + 开机自启)

```bash
sudo vim /etc/systemd/system/zodiac-api.service
```

粘贴(把 `zodiac` 替换成你的实际用户名,路径同):

```ini
[Unit]
Description=Zodiac Dewey API
After=network.target mysql.service

[Service]
Type=simple
User=zodiac
WorkingDirectory=/home/zodiac/zodiac_dewey
EnvironmentFile=/home/zodiac/zodiac_dewey/.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /home/zodiac/zodiac_dewey/backend/target/zodiac-api.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:/home/zodiac/zodiac_dewey/logs/app.log
StandardError=append:/home/zodiac/zodiac_dewey/logs/error.log

[Install]
WantedBy=multi-user.target
```

启动 + 设为开机自启:
```bash
mkdir -p ~/zodiac_dewey/logs
sudo systemctl daemon-reload
sudo systemctl enable zodiac-api
sudo systemctl start zodiac-api

# 查看状态
sudo systemctl status zodiac-api

# 看日志
tail -f ~/zodiac_dewey/logs/app.log
```

---

## 五、Nginx 配置(关键!)

### 1. 部署前端文件

```bash
sudo mkdir -p /var/www/zodiac
sudo cp ~/zodiac_dewey/frontend/index.html /var/www/zodiac/
sudo chown -R www-data:www-data /var/www/zodiac    # Ubuntu
# CentOS 用: sudo chown -R nginx:nginx /var/www/zodiac
```

### 2. 配置 Nginx

```bash
sudo vim /etc/nginx/sites-available/zodiac
```

粘贴(替换 `你的域名.com` 和证书路径):

```nginx
# HTTP 自动跳转 HTTPS
server {
    listen 80;
    server_name 你的域名.com;
    return 301 https://$host$request_uri;
}

# HTTPS 主站
server {
    listen 443 ssl http2;
    server_name 你的域名.com;

    # SSL 证书路径(根据你的实际证书改)
    ssl_certificate /etc/letsencrypt/live/你的域名.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/你的域名.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # 安全头
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header Strict-Transport-Security "max-age=31536000" always;

    # 前端静态
    root /var/www/zodiac;
    index index.html;

    # 主页
    location / {
        try_files $uri $uri/ /index.html;
        # 不缓存 index.html(更新后立即生效)
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # API 转发到 Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # Claude 调用慢,把超时给足
        proxy_read_timeout 200s;
        proxy_send_timeout 200s;
        proxy_connect_timeout 30s;
    }

    # 文件大小上限
    client_max_body_size 5M;

    # gzip
    gzip on;
    gzip_types text/plain text/css text/javascript application/json application/javascript;
}
```

启用配置:

```bash
# Ubuntu
sudo ln -s /etc/nginx/sites-available/zodiac /etc/nginx/sites-enabled/
sudo nginx -t       # 测试配置
sudo systemctl reload nginx

# CentOS 直接放 /etc/nginx/conf.d/zodiac.conf,无需 sites-enabled
```

### 3. 申请 HTTPS 证书(用 Certbot,免费)

如果还没有证书:
```bash
sudo certbot --nginx -d 你的域名.com
# 按提示操作,自动配置 + 自动续期
```

---

## 六、验证部署

### 1. 健康检查

```bash
curl https://你的域名.com/api/health
```

应该返回:
```json
{"status":"ok","service":"zodiac-api","global_used":0,"global_total":200}
```

### 2. 浏览器测试

打开 `https://你的域名.com`,应该看到合盘表单。

测试完整流程:
1. 填表(用真实的生日测试)
2. 点击"解锁我们的灵魂合盘"
3. 30-60 秒后看到 AI 生成的报告
4. 检查太阳/月亮/上升星座是否显示
5. 点击"生成分享卡片"测试
6. 点击"下载完整报告"测试 PDF
7. 填微信号测试入库

### 3. 数据库验证

```bash
mysql -u zodiac -p zodiac_dewey
mysql> SELECT id, user_a_name, user_b_name, score, wechat_id, created_at
       FROM soulmate_report ORDER BY id DESC LIMIT 5;
```

应该看到你刚才生成的报告。

---

## 七、生成二维码 + 上线推广

### 1. 生成二维码

用任何在线工具生成,内容就是 `https://你的域名.com`:
- 草料二维码(国内):https://cli.im
- QR Code Generator(国外):https://www.qr-code-generator.com

**建议**:
- 二维码下方加文字:「扫码测你和 TA 的灵魂合盘 · 小登哥出品」
- 二维码尺寸 ≥ 200×200 像素
- 颜色用品牌主色(蜜桃粉 / 紫色)

### 2. 投放渠道

- 抖音视频结尾贴二维码(或主页简介加链接)
- 微信公众号头图
- 朋友圈测试图

---

## 八、运维监控

### 1. 实时看日志

```bash
# 应用日志
tail -f ~/zodiac_dewey/logs/app.log

# 错误日志
tail -f ~/zodiac_dewey/logs/error.log

# Nginx 访问日志
sudo tail -f /var/log/nginx/access.log

# Nginx 错误日志
sudo tail -f /var/log/nginx/error.log
```

### 2. 业务数据查询

每天上午看一下:

```sql
-- 昨天的数据
SELECT COUNT(*) AS yesterday_total
FROM soulmate_report
WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY);

-- 昨天留微信号的(私域线索!)
SELECT user_a_name AS 主角, wechat_id AS 微信, score AS 分, created_at
FROM soulmate_report
WHERE wechat_id IS NOT NULL
  AND DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
ORDER BY created_at DESC;
```

### 3. 限流监控

```bash
curl https://你的域名.com/api/health
# 看 global_used / global_total 用量
```

如果接近上限,说明:
- 流量超预期(好事!考虑加大限流上限)
- 或者被恶意刷(查 IP)

### 4. 重启 / 更新代码

```bash
# 拉新代码
cd ~/zodiac_dewey
git pull origin main

# 一键重新部署
bash scripts/deploy.sh
```

---

## 九、常见问题

### Q1:用户报"AI 服务暂时不可用"

```bash
# 检查 Claude API Key 是否正确
echo $CLAUDE_API_KEY

# 检查服务器能否访问 Anthropic
curl -I https://api.anthropic.com

# 检查日志
tail -100 ~/zodiac_dewey/logs/app.log | grep -i "claude\|error"
```

**国内服务器**:可能需要代理才能访问 `api.anthropic.com`。

### Q2:数据库连接失败

```bash
# 测试连接
mysql -u zodiac -p -h localhost

# 看 MySQL 日志
sudo tail -50 /var/log/mysql/error.log

# 看应用日志
grep -i "datasource\|jdbc" ~/zodiac_dewey/logs/app.log
```

### Q3:微信扫码进入显示"无法访问"

- 必须用 HTTPS(微信拦截 HTTP)
- 域名必须备案(国内服务器)
- 检查 SSL 证书有效(`curl -I https://你的域名.com`)

### Q4:报告生成超时

Claude Opus 4.5 生成 5000 字大概 30-60 秒。如果经常超时:
- 检查 Nginx `proxy_read_timeout` 是否 ≥ 200s
- 检查 `application.yml` 的 `claude.api.timeout-seconds`
- 考虑换成 `claude-haiku-4-5-20251001` 更快

### Q5:成本超预期

调小 `RATELIMIT_DAILY_TOTAL`(在 .env 里),重启:
```bash
sudo systemctl restart zodiac-api
```

或者换便宜模型:
```bash
# 编辑 .env
CLAUDE_MODEL=claude-haiku-4-5-20251001    # 便宜 5-10 倍

# 重启
sudo systemctl restart zodiac-api
```

---

## 十、安全建议

1. **API Key 永远不要提交到 Git**(`.env` 已在 `.gitignore` 里)
2. **数据库密码至少 16 位强密码**
3. **定期备份数据库**:
   ```bash
   # crontab -e
   0 3 * * * mysqldump -u zodiac -p密码 zodiac_dewey > /backup/zodiac_$(date +\%Y\%m\%d).sql
   ```
4. **MySQL 不要绑定 0.0.0.0**:确认 `/etc/mysql/mysql.conf.d/mysqld.cnf` 里 `bind-address = 127.0.0.1`
5. **防火墙只开必要端口**:
   ```bash
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   ```

---

## 完成 ✨

部署成功后,你的灵魂合盘已经可以:
- 接收抖音粉丝的流量
- 每天生成最多 200 份报告
- 自动收集微信号到数据库
- 让小登哥每天看后台筛选高价值线索

**祝你流量爆炸 💕**
