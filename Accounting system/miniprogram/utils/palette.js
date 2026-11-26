/**
 * 分类色板（8 色低饱和，避开红 / 绿 / 蓝 / 紫四个语义色相）
 * 页面中所有分类颜色都从这里取，禁止在 WXSS 里硬编码。
 */
const CATEGORY_COLORS = [
  '#F5B544', // 琥珀
  '#5CC8D7', // 青
  '#F08AB8', // 粉
  '#F59A62', // 橙
  '#C8D66B', // 柠檬
  '#8FA3BF', // 灰蓝
  '#C49A6C', // 棕
  '#9AA0A9'  // 石墨
];

/**
 * 常见分类 → 固定色，保证同一分类各页面颜色一致
 * 约定：买菜餐饮=粉、交通出行=橙、购物消费=灰蓝（与设计稿一致）
 */
const NAME_COLOR = {
  // 买菜餐饮 → 粉
  买菜餐饮: 2, 餐饮: 2, 吃饭: 2, 买菜: 2, 买菜食材: 2, 食材: 2,
  早餐: 2, 早饭: 2, 午餐: 2, 午饭: 2, 下馆子: 2, 晚餐: 2, 晚饭: 2,
  咖啡: 2, 咖啡奶茶: 2, 奶茶: 2, 零食: 2, 零食饮料: 2, 外卖: 2, 烟酒: 2,
  // 交通出行 → 橙
  交通出行: 3, 交通: 3, 出行: 3, 公交地铁: 3, 公交: 3, 地铁: 3,
  打车: 3, 出租: 3, 加油充电: 3, 加油: 3, 充电: 3, 停车过路: 3, 停车: 3, 长途票务: 3,
  // 购物消费 → 灰蓝
  购物消费: 5, 购物: 5, 日用: 5, 日用百货: 5, 服饰: 5, 服饰鞋包: 5,
  美妆: 5, 美妆个护: 5, 数码: 5, 数码电器: 5, 家居: 5,
  // 房租水电 → 棕
  房租水电: 6, 居住: 6, 房租: 6, 水电: 6, 水电燃气: 6, 物业: 6, 物业费: 6, 宽带通讯: 6,
  // 娱乐休闲 → 琥珀
  娱乐休闲: 0, 娱乐: 0, 电影: 0, 游戏: 0, 旅行: 0, 旅游: 0,
  会员订阅: 0, 运动健身: 0, 运动: 0, 休闲玩乐: 0,
  // 医疗健康 → 青
  医疗健康: 1, 医疗: 1, 健康: 1, 药品: 1, 门诊挂号: 1, 体检: 1, 医疗保险: 1,
  // 学习成长 → 柠檬
  学习成长: 4, 学习: 4, 教育: 4, 书籍: 4, 课程: 4, 课程培训: 4, 考试报名: 4, 学习工具: 4,
  // 人情往来 → 琥珀
  人情往来: 0, 人情: 0, 礼物: 0, 红包: 0, 红包礼金: 0, 请客聚餐: 0, 赡养长辈: 0, 公益捐赠: 0,
  // 其他支出 → 石墨
  其他支出: 7, 其他: 7, 未分类: 7, 手续费: 7, 罚款: 7, 其他杂项: 7, 宠物: 7,
  // 收入分类
  工资收入: 1, 月薪: 1, 加班费: 1, 年终奖: 1,
  奖金补贴: 4, 绩效奖金: 4, 补贴: 4, 提成: 4,
  理财收益: 0, 利息: 0, 基金股票: 0, 分红: 0,
  报销回款: 6, 公司报销: 6, 退款: 6, 借款收回: 6
};

function hash(str) {
  let h = 0;
  const s = String(str || '');
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) % 9973;
  }
  return h;
}

/** 分类名 → 色值（同名恒定；未知分类按名称散列） */
function colorFor(name) {
  if (name && NAME_COLOR[name] !== undefined) {
    return CATEGORY_COLORS[NAME_COLOR[name]];
  }
  return CATEGORY_COLORS[hash(name) % CATEGORY_COLORS.length];
}

/** 按稳定 index 取色（分类列表、图例用） */
function colorAt(index) {
  const i = Math.abs(Number(index) || 0) % CATEGORY_COLORS.length;
  return CATEGORY_COLORS[i];
}

/** #RRGGBB + 透明度 → rgba()，用于 15% 底色 */
function rgba(hex, alpha) {
  const h = String(hex || '').replace('#', '');
  const r = parseInt(h.slice(0, 2), 16) || 0;
  const g = parseInt(h.slice(2, 4), 16) || 0;
  const b = parseInt(h.slice(4, 6), 16) || 0;
  return 'rgba(' + r + ',' + g + ',' + b + ',' + (alpha === undefined ? 0.15 : alpha) + ')';
}

/** 分类胶囊 / 图标方块的成对样式 */
function badge(name) {
  const c = colorFor(name);
  return { color: c, bg: rgba(c, 0.15) };
}

module.exports = {
  CATEGORY_COLORS,
  colorFor,
  colorAt,
  rgba,
  badge
};
