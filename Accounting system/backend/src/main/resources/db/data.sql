-- ============================================================
-- AI 智能记账小程序 · 字典初始化数据（MySQL 5.6）
-- 说明：此处 NOW() 仅用于手工执行脚本；应用运行期时间字段一律由 Java 代码填充
-- ============================================================

USE `smart_ledger`;

-- 支付渠道
INSERT INTO `pay_channel` (`code`, `name`, `sort_no`, `status`, `create_time`, `update_time`) VALUES
('WECHAT', '微信',   1, 1, NOW(), NOW()),
('ALIPAY', '支付宝', 2, 1, NOW(), NOW()),
('CASH',   '现金',   3, 1, NOW(), NOW()),
('BANK',   '银行卡', 4, 1, NOW(), NOW()),
('OTHER',  '其他',   9, 1, NOW(), NOW());

-- 支出分类：一级 9 类，二级 39 类；父级本身仍可作为「不细分」的取值
INSERT INTO `category` (`code`, `parent_code`, `level`, `name`, `bill_type`, `icon`, `sort_no`, `is_system`, `status`, `create_time`, `update_time`) VALUES
('FOOD',             NULL, 1, '买菜餐饮',     0, 'food',         1, 1, 1, NOW(), NOW()),
('FOOD_GROCERY',     'FOOD', 2, '买菜食材',   0, NULL,           1, 1, 1, NOW(), NOW()),
('FOOD_TAKEOUT',     'FOOD', 2, '外卖',       0, 'takeout',      2, 1, 1, NOW(), NOW()),
('FOOD_DINING',      'FOOD', 2, '下馆子',     0, NULL,           3, 1, 1, NOW(), NOW()),
('FOOD_COFFEE',      'FOOD', 2, '咖啡奶茶',   0, 'coffee',       4, 1, 1, NOW(), NOW()),
('FOOD_SNACK',       'FOOD', 2, '零食饮料',   0, NULL,           5, 1, 1, NOW(), NOW()),
('TRAFFIC',          NULL, 1, '交通出行',     0, 'traffic',      2, 1, 1, NOW(), NOW()),
('TRAFFIC_TRANSIT',  'TRAFFIC', 2, '公交地铁', 0, NULL,          1, 1, 1, NOW(), NOW()),
('TRAFFIC_TAXI',     'TRAFFIC', 2, '打车',     0, 'taxi',        2, 1, 1, NOW(), NOW()),
('TRAFFIC_CHARGE',   'TRAFFIC', 2, '加油充电', 0, NULL,          3, 1, 1, NOW(), NOW()),
('TRAFFIC_PARKING',  'TRAFFIC', 2, '停车过路', 0, NULL,          4, 1, 1, NOW(), NOW()),
('TRAFFIC_TICKET',   'TRAFFIC', 2, '长途票务', 0, NULL,          5, 1, 1, NOW(), NOW()),
('SHOPPING',         NULL, 1, '购物消费',     0, 'shop',         3, 1, 1, NOW(), NOW()),
('SHOPPING_DAILY',   'SHOPPING', 2, '日用百货', 0, NULL,         1, 1, 1, NOW(), NOW()),
('SHOPPING_CLOTHES', 'SHOPPING', 2, '服饰鞋包', 0, NULL,         2, 1, 1, NOW(), NOW()),
('SHOPPING_DIGITAL', 'SHOPPING', 2, '数码电器', 0, NULL,         3, 1, 1, NOW(), NOW()),
('SHOPPING_BEAUTY',  'SHOPPING', 2, '美妆个护', 0, NULL,         4, 1, 1, NOW(), NOW()),
('SHOPPING_HOME',    'SHOPPING', 2, '家居',     0, NULL,         5, 1, 1, NOW(), NOW()),
('HOUSING',          NULL, 1, '房租水电',     0, 'home',         4, 1, 1, NOW(), NOW()),
('HOUSING_RENT',     'HOUSING', 2, '房租',     0, 'rent',         1, 1, 1, NOW(), NOW()),
('HOUSING_UTILITY',  'HOUSING', 2, '水电燃气', 0, 'utility',      2, 1, 1, NOW(), NOW()),
('HOUSING_PROPERTY', 'HOUSING', 2, '物业费',   0, NULL,           3, 1, 1, NOW(), NOW()),
('HOUSING_TELECOM',  'HOUSING', 2, '宽带通讯', 0, NULL,           4, 1, 1, NOW(), NOW()),
('ENTERTAINMENT',    NULL, 1, '娱乐休闲',     0, 'fun',          5, 1, 1, NOW(), NOW()),
('ENT_SUB',          'ENTERTAINMENT', 2, '会员订阅', 0, 'sub',    1, 1, 1, NOW(), NOW()),
('ENT_GAME',         'ENTERTAINMENT', 2, '游戏',     0, NULL,     2, 1, 1, NOW(), NOW()),
('ENT_FITNESS',      'ENTERTAINMENT', 2, '运动健身', 0, NULL,     3, 1, 1, NOW(), NOW()),
('ENT_TRAVEL',       'ENTERTAINMENT', 2, '旅游',     0, NULL,     4, 1, 1, NOW(), NOW()),
('ENT_LEISURE',      'ENTERTAINMENT', 2, '休闲玩乐', 0, NULL,     5, 1, 1, NOW(), NOW()),
('MEDICAL',          NULL, 1, '医疗健康',     0, 'med',          6, 1, 1, NOW(), NOW()),
('MED_CLINIC',       'MEDICAL', 2, '门诊挂号', 0, NULL,           1, 1, 1, NOW(), NOW()),
('MED_MEDICINE',     'MEDICAL', 2, '药品',     0, NULL,           2, 1, 1, NOW(), NOW()),
('MED_CHECKUP',      'MEDICAL', 2, '体检',     0, NULL,           3, 1, 1, NOW(), NOW()),
('MED_INSURANCE',    'MEDICAL', 2, '医疗保险', 0, NULL,           4, 1, 1, NOW(), NOW()),
('STUDY',            NULL, 1, '学习成长',     0, 'study',        7, 1, 1, NOW(), NOW()),
('STUDY_BOOK',       'STUDY', 2, '书籍',       0, NULL,           1, 1, 1, NOW(), NOW()),
('STUDY_COURSE',     'STUDY', 2, '课程培训',   0, NULL,           2, 1, 1, NOW(), NOW()),
('STUDY_EXAM',       'STUDY', 2, '考试报名',   0, NULL,           3, 1, 1, NOW(), NOW()),
('STUDY_TOOL',       'STUDY', 2, '学习工具',   0, NULL,           4, 1, 1, NOW(), NOW()),
('SOCIAL',           NULL, 1, '人情往来',     0, 'social',       8, 1, 1, NOW(), NOW()),
('SOCIAL_GIFT',      'SOCIAL', 2, '红包礼金',  0, NULL,           1, 1, 1, NOW(), NOW()),
('SOCIAL_TREAT',     'SOCIAL', 2, '请客聚餐',  0, NULL,           2, 1, 1, NOW(), NOW()),
('SOCIAL_FAMILY',    'SOCIAL', 2, '赡养长辈',  0, NULL,           3, 1, 1, NOW(), NOW()),
('SOCIAL_DONATE',    'SOCIAL', 2, '公益捐赠',  0, NULL,           4, 1, 1, NOW(), NOW()),
('EXPENSE_OTHER',    NULL, 1, '其他支出',     0, 'other',        9, 1, 1, NOW(), NOW()),
('OTHER_FEE',        'EXPENSE_OTHER', 2, '手续费', 0, NULL,       1, 1, 1, NOW(), NOW()),
('OTHER_FINE',       'EXPENSE_OTHER', 2, '罚款',   0, NULL,       2, 1, 1, NOW(), NOW()),
('OTHER_MISC',       'EXPENSE_OTHER', 2, '其他杂项', 0, NULL,     3, 1, 1, NOW(), NOW());

-- 收入分类：一级 5 类，二级 15 类
INSERT INTO `category` (`code`, `parent_code`, `level`, `name`, `bill_type`, `icon`, `sort_no`, `is_system`, `status`, `create_time`, `update_time`) VALUES
('SALARY',              NULL, 1, '工资收入',   1, 'salary',         1, 1, 1, NOW(), NOW()),
('SALARY_BASE',         'SALARY', 2, '月薪',   1, NULL,           1, 1, 1, NOW(), NOW()),
('SALARY_OVERTIME',     'SALARY', 2, '加班费', 1, NULL,           2, 1, 1, NOW(), NOW()),
('SALARY_ANNUAL',       'SALARY', 2, '年终奖', 1, NULL,           3, 1, 1, NOW(), NOW()),
('BONUS',               NULL, 1, '奖金补贴',   1, 'bonus',          2, 1, 1, NOW(), NOW()),
('BONUS_PERF',          'BONUS', 2, '绩效奖金', 1, NULL,          1, 1, 1, NOW(), NOW()),
('BONUS_ALLOWANCE',     'BONUS', 2, '补贴',     1, NULL,          2, 1, 1, NOW(), NOW()),
('BONUS_COMMISSION',    'BONUS', 2, '提成',     1, NULL,          3, 1, 1, NOW(), NOW()),
('INVEST',              NULL, 1, '理财收益',   1, 'invest',         3, 1, 1, NOW(), NOW()),
('INVEST_INTEREST',     'INVEST', 2, '利息',    1, NULL,          1, 1, 1, NOW(), NOW()),
('INVEST_FUND',         'INVEST', 2, '基金股票', 1, NULL,         2, 1, 1, NOW(), NOW()),
('INVEST_DIVIDEND',     'INVEST', 2, '分红',    1, NULL,          3, 1, 1, NOW(), NOW()),
('REIMBURSE',           NULL, 1, '报销回款',   1, 'reimburse',      4, 1, 1, NOW(), NOW()),
('REIMBURSE_COMPANY',   'REIMBURSE', 2, '公司报销', 1, NULL,      1, 1, 1, NOW(), NOW()),
('REIMBURSE_REFUND',    'REIMBURSE', 2, '退款',     1, NULL,      2, 1, 1, NOW(), NOW()),
('REIMBURSE_LOAN',      'REIMBURSE', 2, '借款收回', 1, NULL,      3, 1, 1, NOW(), NOW()),
('INCOME_OTHER',        NULL, 1, '其他收入',   1, 'income_other',   5, 1, 1, NOW(), NOW()),
('INCOME_PARTTIME',     'INCOME_OTHER', 2, '兼职',  1, NULL,       1, 1, 1, NOW(), NOW()),
('INCOME_REDPACKET',    'INCOME_OTHER', 2, '收红包', 1, NULL,     2, 1, 1, NOW(), NOW()),
('INCOME_MISC',         'INCOME_OTHER', 2, '其他',   1, NULL,      3, 1, 1, NOW(), NOW());