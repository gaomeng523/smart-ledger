-- ============================================================
-- AI 智能记账小程序 · 建表脚本（MySQL 5.6 可直接执行）
-- 说明：时间字段全部由应用层（MyBatis-Plus MetaObjectHandler）填充，DDL 不设默认值
-- ============================================================

CREATE DATABASE IF NOT EXISTS `smart_ledger`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `smart_ledger`;

-- ============ 1. 用户表 ============
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`      VARCHAR(191)    NOT NULL                COMMENT '微信唯一标识',
  `unionid`     VARCHAR(191)    DEFAULT NULL            COMMENT '开放平台唯一标识',
  `nickname`    VARCHAR(64)     DEFAULT NULL            COMMENT '昵称',
  `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0停用',
  `create_time` DATETIME        NOT NULL                COMMENT '创建时间(应用层填充)',
  `update_time` DATETIME        NOT NULL                COMMENT '更新时间(应用层填充)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ============ 2. 分类字典表 ============
CREATE TABLE IF NOT EXISTS `category` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(32)     NOT NULL                COMMENT '分类编码',
  `parent_code` VARCHAR(32)     DEFAULT NULL            COMMENT '父级编码,NULL=一级',
  `level`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1一级 2二级',
  `name`        VARCHAR(32)     NOT NULL                COMMENT '分类名称',
  `bill_type`   TINYINT         NOT NULL DEFAULT 0      COMMENT '0支出 1收入',
  `icon`        VARCHAR(64)     DEFAULT NULL            COMMENT '图标标识',
  `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '排序',
  `is_system`   TINYINT         NOT NULL DEFAULT 1      COMMENT '1内置 0自定义',
  `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time` DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_type_status` (`bill_type`, `status`),
  KEY `idx_parent` (`parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分类字典表';

-- ============ 3. 渠道字典表 ============
CREATE TABLE IF NOT EXISTS `pay_channel` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(32)     NOT NULL                COMMENT '渠道编码',
  `name`        VARCHAR(32)     NOT NULL                COMMENT '渠道名称',
  `icon`        VARCHAR(64)     DEFAULT NULL            COMMENT '图标',
  `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '排序',
  `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time` DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='渠道字典表';

-- ============ 4. 账单表 ============
CREATE TABLE IF NOT EXISTS `bill` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`           VARCHAR(191)    NOT NULL                COMMENT '所属用户',
  `book_id`          BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '账本ID,0=个人默认',
  `amount`           DECIMAL(10,2)   NOT NULL                COMMENT '金额(正数)',
  `bill_type`        TINYINT         NOT NULL DEFAULT 0      COMMENT '0支出 1收入',
  `pay_channel_code` VARCHAR(32)     NOT NULL                COMMENT '渠道编码',
  `category_code`    VARCHAR(32)     NOT NULL                COMMENT '分类编码',
  `record_text`      VARCHAR(500)    DEFAULT NULL            COMMENT '用户原始输入',
  `remark`           VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
  `record_date`      DATE            NOT NULL                COMMENT '记账日期',
  `source`           TINYINT         NOT NULL DEFAULT 1      COMMENT '1文本2语音3模板4导入5OCR6手动',
  `confidence`       DECIMAL(3,2)    DEFAULT NULL            COMMENT '解析置信度',
  `tags`             VARCHAR(255)    DEFAULT NULL            COMMENT '标签,逗号分隔',
  `image_url`        VARCHAR(255)    DEFAULT NULL            COMMENT '账单图片URL',
  `request_id`       VARCHAR(64)     DEFAULT NULL            COMMENT '幂等标识',
  `deleted`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0正常 1删除',
  `create_time`      DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time`      DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_openid_date` (`openid`, `record_date`),
  KEY `idx_openid_category` (`openid`, `category_code`),
  KEY `idx_openid_channel` (`openid`, `pay_channel_code`),
  KEY `idx_book_date` (`book_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账单表';

-- ============ 5. AI 解析日志表 ============
CREATE TABLE IF NOT EXISTS `ai_parse_log` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`            VARCHAR(191)    NOT NULL                COMMENT '用户',
  `input_text`        VARCHAR(500)    NOT NULL                COMMENT '原始输入',
  `output_json`       TEXT            DEFAULT NULL            COMMENT '模型原始输出',
  `model`             VARCHAR(64)     DEFAULT NULL            COMMENT '模型名',
  `success`           TINYINT         NOT NULL DEFAULT 0      COMMENT '1成功 0失败',
  `error_code`        VARCHAR(32)     DEFAULT NULL            COMMENT '错误码',
  `error_msg`         VARCHAR(255)    DEFAULT NULL            COMMENT '错误原因',
  `cost_ms`           INT             DEFAULT NULL            COMMENT '耗时ms',
  `prompt_tokens`     INT             DEFAULT NULL            COMMENT '输入token',
  `completion_tokens` INT             DEFAULT NULL            COMMENT '输出token',
  `create_time`       DATETIME        NOT NULL                COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_openid_time` (`openid`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI解析日志表';

-- ============ 6. 快捷记账模板表(V2) ============
CREATE TABLE IF NOT EXISTS `bill_template` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`           VARCHAR(191)    NOT NULL                COMMENT '用户',
  `title`            VARCHAR(64)     NOT NULL                COMMENT '模板名称',
  `amount`           DECIMAL(10,2)   NOT NULL                COMMENT '金额',
  `bill_type`        TINYINT         NOT NULL DEFAULT 0      COMMENT '0支出 1收入',
  `pay_channel_code` VARCHAR(32)     NOT NULL                COMMENT '渠道编码',
  `category_code`    VARCHAR(32)     NOT NULL                COMMENT '分类编码',
  `remark`           VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
  `use_count`        INT             NOT NULL DEFAULT 0      COMMENT '使用次数',
  `sort_no`          INT             NOT NULL DEFAULT 0      COMMENT '排序',
  `status`           TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time`      DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time`      DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_openid_status` (`openid`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='快捷记账模板表';

-- ============ 7. 别名词典表(V2) ============
CREATE TABLE IF NOT EXISTS `alias_dict` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`      VARCHAR(191)    NOT NULL                COMMENT '用户',
  `keyword`     VARCHAR(64)     NOT NULL                COMMENT '别名关键词',
  `target_type` TINYINT         NOT NULL DEFAULT 1      COMMENT '1分类 2渠道',
  `target_code` VARCHAR(32)     NOT NULL                COMMENT '目标编码',
  `hit_count`   INT             NOT NULL DEFAULT 0      COMMENT '命中次数',
  `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time` DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid_keyword` (`openid`, `keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='别名词典表';

-- ============ 8. 预算表(V2) ============
CREATE TABLE IF NOT EXISTS `budget` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`        VARCHAR(191)    NOT NULL                COMMENT '用户',
  `budget_month`  CHAR(7)         NOT NULL                COMMENT '预算月份yyyy-MM',
  `category_code` VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '空=总预算',
  `amount`        DECIMAL(10,2)   NOT NULL                COMMENT '预算金额',
  `warn_ratio`    DECIMAL(5,2)    NOT NULL DEFAULT 0.80  COMMENT '预警比例',
  `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time`   DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid_month_cat` (`openid`, `budget_month`, `category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预算表';

-- ============ 9. 导入批次表(V2) ============
CREATE TABLE IF NOT EXISTS `import_record` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`        VARCHAR(191)    NOT NULL                COMMENT '用户',
  `file_name`     VARCHAR(255)    DEFAULT NULL            COMMENT '原文件名',
  `source_type`   VARCHAR(32)     NOT NULL                COMMENT 'WECHAT/ALIPAY',
  `total_count`   INT             NOT NULL DEFAULT 0      COMMENT '总条数',
  `success_count` INT             NOT NULL DEFAULT 0      COMMENT '成功条数',
  `fail_count`    INT             NOT NULL DEFAULT 0      COMMENT '失败条数',
  `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '0处理中1成功2部分失败3已撤销',
  `fail_detail`   TEXT            DEFAULT NULL            COMMENT '失败明细',
  `create_time`   DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_openid_time` (`openid`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='导入批次表';

-- ============ 10. 周期订阅表(V3) ============
CREATE TABLE IF NOT EXISTS `subscription` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`           VARCHAR(191)    NOT NULL                COMMENT '所属用户',
  `title`            VARCHAR(64)     NOT NULL                COMMENT '订阅名称',
  `category_code`    VARCHAR(32)     NOT NULL                COMMENT '分类编码',
  `pay_channel_code` VARCHAR(32)     NOT NULL                COMMENT '渠道编码',
  `avg_amount`       DECIMAL(10,2)   NOT NULL                COMMENT '参考金额',
  `cycle_days`       INT             NOT NULL DEFAULT 30     COMMENT '周期天数:30月90季365年',
  `next_date`        DATE            NOT NULL                COMMENT '下次预计扣费日',
  `last_bill_id`     BIGINT UNSIGNED DEFAULT NULL            COMMENT '最近匹配账单ID',
  `sample_count`     INT             NOT NULL DEFAULT 0      COMMENT '已匹配次数',
  `status`           TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0暂停',
  `deleted`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0正常 1删除',
  `create_time`      DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time`      DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_openid_status` (`openid`, `status`, `deleted`),
  KEY `idx_openid_nextdate` (`openid`, `next_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='周期订阅表';

-- ============ 11. 心愿储蓄表(V3 阶段二) ============
CREATE TABLE IF NOT EXISTS `savings_goal` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`        VARCHAR(191)    NOT NULL                COMMENT '所属用户',
  `title`         VARCHAR(64)     NOT NULL                COMMENT '心愿名称',
  `icon`          VARCHAR(16)     DEFAULT NULL            COMMENT '图标(emoji)',
  `target_amount` DECIMAL(10,2)   NOT NULL                COMMENT '目标金额',
  `saved_amount`  DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '已存金额',
  `due_date`      DATE            DEFAULT NULL            COMMENT '目标日期',
  `category_code` VARCHAR(32)     DEFAULT NULL            COMMENT '关联分类(预留)',
  `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '0进行中 1已达成 2已放弃',
  `deleted`       TINYINT         NOT NULL DEFAULT 0      COMMENT '0正常 1删除',
  `create_time`   DATETIME        NOT NULL                COMMENT '创建时间',
  `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_openid_status` (`openid`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='心愿储蓄表';
