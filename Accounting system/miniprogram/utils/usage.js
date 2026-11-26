/**
 * 使用天数：唯一实现，我的页与设置页共用，避免两处出现 16 / 17 的差异。
 */
const storage = require('./storage');
const format = require('./format');

const FIRST_USE_KEY = 'firstUseDate';

/** 首次使用日期（不存在则写入今天） */
function firstUseDate() {
  let first = storage.get(FIRST_USE_KEY, '');
  if (!first) {
    first = format.todayStr();
    storage.set(FIRST_USE_KEY, first);
  }
  return first;
}

/** 使用天数：首日算第 1 天 */
function usedDays() {
  const first = firstUseDate();
  const start = new Date(String(first).replace(/-/g, '/'));
  const days = Math.floor((Date.now() - start.getTime()) / 86400000) + 1;
  return isFinite(days) && days > 0 ? days : 1;
}

module.exports = {
  FIRST_USE_KEY,
  firstUseDate,
  usedDays
};
