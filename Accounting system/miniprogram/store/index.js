/**
 * 轻量全局 store（§7.2）
 * 只放「字典」与「本月统计快照」两类共享数据，减少重复请求。
 */
const apiDict = require('../api/dict');
const storage = require('../utils/storage');
const { DICT_TTL, STORAGE_KEY } = require('../config/const');

/** 本月统计快照：{ month, income, expense, balance, billCount } */
let monthStat = null;

/* ---------- 字典（10 分钟缓存，§6.5） ---------- */

function readDictCache() {
  const cached = storage.get(STORAGE_KEY.DICT, null);
  // 旧版本缓存没有 categoryTree，直接丢弃，避免分类选择器退回单列平铺
  if (cached && cached.ts && cached.data && cached.data.categoryTree
      && (Date.now() - cached.ts) < DICT_TTL) {
    return cached.data;
  }
  return null;
}

/**
 * 获取字典（默认走缓存）
 * @param {boolean} [force] 强制刷新
 */
function loadDict(force) {
  if (!force) {
    const cached = readDictCache();
    if (cached) {
      return Promise.resolve(cached);
    }
  }
  return apiDict.all({ silent: true }).then((data) => {
    const dict = {
      channels: (data && data.channels) || [],
      categories: (data && data.categories) || [],
      categoryTree: (data && data.categoryTree) || []
    };
    storage.set(STORAGE_KEY.DICT, { data: dict, ts: Date.now() });
    return dict;
  });
}

/** 同步读取字典缓存（可能为 null） */
function getDictCache() {
  return readDictCache();
}

/* ---------- 本月统计快照 ---------- */

function setMonthStat(stat) {
  monthStat = stat || null;
}

function getMonthStat() {
  return monthStat;
}

module.exports = {
  loadDict,
  getDictCache,
  setMonthStat,
  getMonthStat
};