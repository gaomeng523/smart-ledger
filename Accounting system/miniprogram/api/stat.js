/**
 * 统计相关接口
 */
const { request } = require('../utils/request');

/**
 * 月度收支统计
 * @param {string} month yyyy-MM
 * @returns {Promise<{month, income, expense, balance, billCount}>}
 */
function month(month, options) {
  return request({
    url: '/api/bill/stat/month',
    method: 'GET',
    data: month ? { month } : {},
    silent: !!(options && options.silent)
  });
}

/**
 * 分类占比统计
 * @param {string} month yyyy-MM
 * @param {number} billType 0 支出 1 收入
 * @param {object} options { silent, parentCode }
 *   parentCode 传入时下钻该一级分类的二级明细
 * @returns {Promise<{month, billType, parentCode, total, items: Array}>}
 */
function category(month, billType, options) {
  const data = {};
  if (month) {
    data.month = month;
  }
  if (billType !== undefined && billType !== null) {
    data.billType = billType;
  }
  if (options && options.parentCode) {
    data.parentCode = options.parentCode;
  }
  return request({
    url: '/api/bill/stat/category',
    method: 'GET',
    data,
    silent: !!(options && options.silent)
  });
}

/**
 * 近 N 月收支趋势（V3 · F-43）
 * @param {number} range 6 或 12
 * @returns {Promise<{range, items: Array<{month, income, expense, balance, expenseMom, incomeMom}>, expenseYoy, incomeYoy}>}
 */
function trend(range, options) {
  return request({
    url: '/api/bill/stat/trend',
    method: 'GET',
    data: { range: range || 6 },
    silent: !!(options && options.silent)
  });
}

module.exports = { month, category, trend };