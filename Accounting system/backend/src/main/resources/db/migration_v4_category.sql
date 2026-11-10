-- ============================================================
-- V4 分类层级迁移（已有数据库执行一次；全新库直接跑 schema.sql + data.sql）
-- 说明：MySQL 5.6 不支持 ADD COLUMN IF NOT EXISTS，这里用存储过程做幂等判断
-- ============================================================

USE `smart_ledger`;

DROP PROCEDURE IF EXISTS `add_category_level_columns`;
DELIMITER $$
CREATE PROCEDURE `add_category_level_columns`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'category' AND column_name = 'parent_code'
  ) THEN
    ALTER TABLE `category`
      ADD COLUMN `parent_code` VARCHAR(32) DEFAULT NULL COMMENT '父级编码,NULL=一级' AFTER `code`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'category' AND column_name = 'level'
  ) THEN
    ALTER TABLE `category`
      ADD COLUMN `level` TINYINT NOT NULL DEFAULT 1 COMMENT '1一级 2二级' AFTER `parent_code`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'category' AND index_name = 'idx_parent'
  ) THEN
    ALTER TABLE `category` ADD KEY `idx_parent` (`parent_code`);
  END IF;
END$$
DELIMITER ;

CALL `add_category_level_columns`();
DROP PROCEDURE `add_category_level_columns`;

-- 一级分类图标（父级保持 level=1、parent_code=NULL）
UPDATE `category` SET `icon` = 'food'         WHERE `code` = 'FOOD';
UPDATE `category` SET `icon` = 'traffic'      WHERE `code` = 'TRAFFIC';
UPDATE `category` SET `icon` = 'shop'         WHERE `code` = 'SHOPPING';
UPDATE `category` SET `icon` = 'home'         WHERE `code` = 'HOUSING';
UPDATE `category` SET `icon` = 'fun'          WHERE `code` = 'ENTERTAINMENT';
UPDATE `category` SET `icon` = 'med'          WHERE `code` = 'MEDICAL';
UPDATE `category` SET `icon` = 'study'        WHERE `code` = 'STUDY';
UPDATE `category` SET `icon` = 'social'       WHERE `code` = 'SOCIAL';
UPDATE `category` SET `icon` = 'other'        WHERE `code` = 'EXPENSE_OTHER';
UPDATE `category` SET `icon` = 'salary'       WHERE `code` = 'SALARY';
UPDATE `category` SET `icon` = 'bonus'        WHERE `code` = 'BONUS';
UPDATE `category` SET `icon` = 'invest'       WHERE `code` = 'INVEST';
UPDATE `category` SET `icon` = 'reimburse'    WHERE `code` = 'REIMBURSE';
UPDATE `category` SET `icon` = 'income_other' WHERE `code` = 'INCOME_OTHER';

-- 二级分类（code 唯一，重复执行会跳过）
INSERT IGNORE INTO `category` (`code`, `parent_code`, `level`, `name`, `bill_type`, `icon`, `sort_no`, `is_system`, `status`, `create_time`, `update_time`) VALUES
('FOOD_GROCERY',     'FOOD', 2, '买菜食材',   0, NULL,           1, 1, 1, NOW(), NOW()),
('FOOD_TAKEOUT',     'FOOD', 2, '外卖',       0, 'takeout',      2, 1, 1, NOW(), NOW()),
('FOOD_DINING',      'FOOD', 2, '下馆子',     0, NULL,           3, 1, 1, NOW(), NOW()),
('FOOD_COFFEE',      'FOOD', 2, '咖啡奶茶',   0, 'coffee',       4, 1, 1, NOW(), NOW()),
('FOOD_SNACK',       'FOOD', 2, '零食饮料',   0, NULL,           5, 1, 1, NOW(), NOW()),
('TRAFFIC_TRANSIT',  'TRAFFIC', 2, '公交地铁', 0, NULL,          1, 1, 1, NOW(), NOW()),
('TRAFFIC_TAXI',     'TRAFFIC', 2, '打车',     0, 'taxi',        2, 1, 1, NOW(), NOW()),
('TRAFFIC_CHARGE',   'TRAFFIC', 2, '加油充电', 0, NULL,          3, 1, 1, NOW(), NOW()),
('TRAFFIC_PARKING',  'TRAFFIC', 2, '停车过路', 0, NULL,          4, 1, 1, NOW(), NOW()),
('TRAFFIC_TICKET',   'TRAFFIC', 2, '长途票务', 0, NULL,          5, 1, 1, NOW(), NOW()),
('SHOPPING_DAILY',   'SHOPPING', 2, '日用百货', 0, NULL,         1, 1, 1, NOW(), NOW()),
('SHOPPING_CLOTHES', 'SHOPPING', 2, '服饰鞋包', 0, NULL,         2, 1, 1, NOW(), NOW()),
('SHOPPING_DIGITAL', 'SHOPPING', 2, '数码电器', 0, NULL,         3, 1, 1, NOW(), NOW()),
('SHOPPING_BEAUTY',  'SHOPPING', 2, '美妆个护', 0, NULL,         4, 1, 1, NOW(), NOW()),
('SHOPPING_HOME',    'SHOPPING', 2, '家居',     0, NULL,         5, 1, 1, NOW(), NOW()),
('HOUSING_RENT',     'HOUSING', 2, '房租',     0, 'rent',         1, 1, 1, NOW(), NOW()),
('HOUSING_UTILITY',  'HOUSING', 2, '水电燃气', 0, 'utility',      2, 1, 1, NOW(), NOW()),
('HOUSING_PROPERTY', 'HOUSING', 2, '物业费',   0, NULL,           3, 1, 1, NOW(), NOW()),
('HOUSING_TELECOM',  'HOUSING', 2, '宽带通讯', 0, NULL,           4, 1, 1, NOW(), NOW()),
('ENT_SUB',          'ENTERTAINMENT', 2, '会员订阅', 0, 'sub',    1, 1, 1, NOW(), NOW()),
('ENT_GAME',         'ENTERTAINMENT', 2, '游戏',     0, NULL,     2, 1, 1, NOW(), NOW()),
('ENT_FITNESS',      'ENTERTAINMENT', 2, '运动健身', 0, NULL,     3, 1, 1, NOW(), NOW()),
('ENT_TRAVEL',       'ENTERTAINMENT', 2, '旅游',     0, NULL,     4, 1, 1, NOW(), NOW()),
('ENT_LEISURE',      'ENTERTAINMENT', 2, '休闲玩乐', 0, NULL,     5, 1, 1, NOW(), NOW()),
('MED_CLINIC',       'MEDICAL', 2, '门诊挂号', 0, NULL,           1, 1, 1, NOW(), NOW()),
('MED_MEDICINE',     'MEDICAL', 2, '药品',     0, NULL,           2, 1, 1, NOW(), NOW()),
('MED_CHECKUP',      'MEDICAL', 2, '体检',     0, NULL,           3, 1, 1, NOW(), NOW()),
('MED_INSURANCE',    'MEDICAL', 2, '医疗保险', 0, NULL,           4, 1, 1, NOW(), NOW()),
('STUDY_BOOK',       'STUDY', 2, '书籍',       0, NULL,           1, 1, 1, NOW(), NOW()),
('STUDY_COURSE',     'STUDY', 2, '课程培训',   0, NULL,           2, 1, 1, NOW(), NOW()),
('STUDY_EXAM',       'STUDY', 2, '考试报名',   0, NULL,           3, 1, 1, NOW(), NOW()),
('STUDY_TOOL',       'STUDY', 2, '学习工具',   0, NULL,           4, 1, 1, NOW(), NOW()),
('SOCIAL_GIFT',      'SOCIAL', 2, '红包礼金',  0, NULL,           1, 1, 1, NOW(), NOW()),
('SOCIAL_TREAT',     'SOCIAL', 2, '请客聚餐',  0, NULL,           2, 1, 1, NOW(), NOW()),
('SOCIAL_FAMILY',    'SOCIAL', 2, '赡养长辈',  0, NULL,           3, 1, 1, NOW(), NOW()),
('SOCIAL_DONATE',    'SOCIAL', 2, '公益捐赠',  0, NULL,           4, 1, 1, NOW(), NOW()),
('OTHER_FEE',        'EXPENSE_OTHER', 2, '手续费', 0, NULL,       1, 1, 1, NOW(), NOW()),
('OTHER_FINE',       'EXPENSE_OTHER', 2, '罚款',   0, NULL,       2, 1, 1, NOW(), NOW()),
('OTHER_MISC',       'EXPENSE_OTHER', 2, '其他杂项', 0, NULL,     3, 1, 1, NOW(), NOW()),
('SALARY_BASE',         'SALARY', 2, '月薪',   1, NULL,           1, 1, 1, NOW(), NOW()),
('SALARY_OVERTIME',     'SALARY', 2, '加班费', 1, NULL,           2, 1, 1, NOW(), NOW()),
('SALARY_ANNUAL',       'SALARY', 2, '年终奖', 1, NULL,           3, 1, 1, NOW(), NOW()),
('BONUS_PERF',          'BONUS', 2, '绩效奖金', 1, NULL,          1, 1, 1, NOW(), NOW()),
('BONUS_ALLOWANCE',     'BONUS', 2, '补贴',     1, NULL,          2, 1, 1, NOW(), NOW()),
('BONUS_COMMISSION',    'BONUS', 2, '提成',     1, NULL,          3, 1, 1, NOW(), NOW()),
('INVEST_INTEREST',     'INVEST', 2, '利息',    1, NULL,          1, 1, 1, NOW(), NOW()),
('INVEST_FUND',         'INVEST', 2, '基金股票', 1, NULL,         2, 1, 1, NOW(), NOW()),
('INVEST_DIVIDEND',     'INVEST', 2, '分红',    1, NULL,          3, 1, 1, NOW(), NOW()),
('REIMBURSE_COMPANY',   'REIMBURSE', 2, '公司报销', 1, NULL,      1, 1, 1, NOW(), NOW()),
('REIMBURSE_REFUND',    'REIMBURSE', 2, '退款',     1, NULL,      2, 1, 1, NOW(), NOW()),
('REIMBURSE_LOAN',      'REIMBURSE', 2, '借款收回', 1, NULL,      3, 1, 1, NOW(), NOW()),
('INCOME_PARTTIME',     'INCOME_OTHER', 2, '兼职',  1, NULL,       1, 1, 1, NOW(), NOW()),
('INCOME_REDPACKET',    'INCOME_OTHER', 2, '收红包', 1, NULL,     2, 1, 1, NOW(), NOW()),
('INCOME_MISC',         'INCOME_OTHER', 2, '其他',   1, NULL,      3, 1, 1, NOW(), NOW());
