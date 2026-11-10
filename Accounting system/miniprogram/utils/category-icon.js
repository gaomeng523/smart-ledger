/**
 * 分类 code -> 图标标识。
 * 一级分类各自有图标；二级默认继承一级，只有高频二级单独配图标。
 * 与后端 category.icon 保持一致，后端字段为空时前端也能兜底。
 */

/** 一级分类（及需兜底的编码前缀） */
const PARENT_ICON = {
  FOOD: 'food',
  TRAFFIC: 'traffic',
  SHOPPING: 'shop',
  HOUSING: 'home',
  ENTERTAINMENT: 'fun',
  MEDICAL: 'med',
  STUDY: 'study',
  SOCIAL: 'social',
  EXPENSE_OTHER: 'other',
  EXPENSE: 'other',
  SALARY: 'salary',
  BONUS: 'bonus',
  INVEST: 'invest',
  REIMBURSE: 'reimburse',
  INCOME_OTHER: 'income_other',
  INCOME: 'income_other'
};

/** 单独配图标的二级分类 */
const LEAF_ICON = {
  FOOD_TAKEOUT: 'takeout',
  FOOD_COFFEE: 'coffee',
  TRAFFIC_TAXI: 'taxi',
  HOUSING_RENT: 'rent',
  HOUSING_UTILITY: 'utility',
  ENT_SUB: 'sub'
};

/** 真正存在的一级分类编码（可下钻二级） */
const PARENT_CODES = [
  'FOOD', 'TRAFFIC', 'SHOPPING', 'HOUSING', 'ENTERTAINMENT',
  'MEDICAL', 'STUDY', 'SOCIAL', 'EXPENSE_OTHER',
  'SALARY', 'BONUS', 'INVEST', 'REIMBURSE', 'INCOME_OTHER'
];

/** 特殊前缀：二级编码不以一级编码开头的情况 */
const PREFIX_ALIAS = {
  MED_: 'med',
  ENT_: 'fun',
  OTHER_: 'other'
};

function iconFor(code) {
  if (!code) {
    return '';
  }
  if (LEAF_ICON[code]) {
    return LEAF_ICON[code];
  }
  if (PARENT_ICON[code]) {
    return PARENT_ICON[code];
  }
  const aliases = Object.keys(PREFIX_ALIAS);
  for (let i = 0; i < aliases.length; i += 1) {
    if (code.indexOf(aliases[i]) === 0) {
      return PREFIX_ALIAS[aliases[i]];
    }
  }
  const prefix = code.split('_')[0];
  return PARENT_ICON[prefix] || '';
}

/** 取字典项上的图标（后端已带 icon 时优先用后端值） */
function iconOf(item) {
  if (!item) {
    return '';
  }
  return item.icon || iconFor(item.code);
}

/** 是否一级分类（统计页据此显示下钻箭头） */
function isParentCode(code) {
  return PARENT_CODES.indexOf(code) >= 0;
}

module.exports = {
  iconFor,
  iconOf,
  isParentCode
};
