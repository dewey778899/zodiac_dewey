-- 灵魂合盘数据库初始化脚本
-- 注意:Spring JPA 启动时会自动建表(ddl-auto=update),此脚本仅用于:
--   1. 手工查看表结构
--   2. 生产环境关闭 ddl-auto 时手动执行
--   3. 初始化数据库

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS zodiac_dewey
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE zodiac_dewey;

-- 2. 合盘报告表
CREATE TABLE IF NOT EXISTS soulmate_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_uid VARCHAR(50) NOT NULL UNIQUE COMMENT '报告唯一编号',

  -- 用户 A
  user_a_name VARCHAR(50) COMMENT '姓名',
  user_a_gender VARCHAR(10) COMMENT '性别 male/female',
  user_a_birth VARCHAR(20) COMMENT '生日 YYYY-MM-DD',
  user_a_time VARCHAR(10) COMMENT '出生时间 HH:mm',
  user_a_place VARCHAR(50) COMMENT '出生地',
  zodiac_a VARCHAR(20) COMMENT '太阳星座',
  moon_a VARCHAR(20) COMMENT '月亮星座',
  rising_a VARCHAR(20) COMMENT '上升星座',

  -- 用户 B
  user_b_name VARCHAR(50),
  user_b_gender VARCHAR(10),
  user_b_birth VARCHAR(20),
  user_b_time VARCHAR(10),
  user_b_place VARCHAR(50),
  zodiac_b VARCHAR(20),
  moon_b VARCHAR(20),
  rising_b VARCHAR(20),

  -- 报告结果
  score INT COMMENT '匹配度 0-100',
  model_code VARCHAR(20) COMMENT '模型代码 deepseek/claude',
  relationship_type VARCHAR(50) COMMENT '关系类型',
  tagline VARCHAR(500) COMMENT '一句话总结',
  full_report LONGTEXT COMMENT 'Claude 原始 JSON',

  -- 追踪
  ip_address VARCHAR(50),
  user_agent VARCHAR(500),
  wechat_id VARCHAR(100) COMMENT '用户微信号(私域引流)',
  shared_count INT DEFAULT 0 COMMENT '分享次数',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_created_at (created_at),
  INDEX idx_ip (ip_address),
  INDEX idx_wechat (wechat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='灵魂合盘报告记录';

-- 3. 统计事件表
CREATE TABLE IF NOT EXISTS analytics_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
  model_code VARCHAR(20) NULL COMMENT '模型代码 deepseek/claude',
  channel VARCHAR(20) NULL COMMENT '二维码渠道 wechat/alipay',
  report_uid VARCHAR(50) NULL COMMENT '关联报告编号',
  ip_address VARCHAR(50) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_event_created_at (created_at),
  INDEX idx_event_type (event_type),
  INDEX idx_event_model (model_code),
  INDEX idx_event_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='运营统计事件';

-- 4. 常用查询示例(供小登哥后台用)

-- 今日生成报告数
-- SELECT COUNT(*) FROM soulmate_report WHERE DATE(created_at) = CURDATE();

-- 今日新增微信号(私域转化)
-- SELECT user_a_name, wechat_id, score, created_at
-- FROM soulmate_report
-- WHERE wechat_id IS NOT NULL AND DATE(created_at) = CURDATE()
-- ORDER BY created_at DESC;

-- 最热的星座组合 TOP 10
-- SELECT zodiac_a, zodiac_b, COUNT(*) c
-- FROM soulmate_report
-- GROUP BY zodiac_a, zodiac_b
-- ORDER BY c DESC LIMIT 10;

-- 单 IP 测试次数最多的(可能是恶意刷)
-- SELECT ip_address, COUNT(*) c
-- FROM soulmate_report
-- WHERE DATE(created_at) = CURDATE()
-- GROUP BY ip_address
-- HAVING c > 3
-- ORDER BY c DESC;
