/**
 * 预算接口（V2，§4.7）
 */
const { request } = require('../utils/request');

/**
 * 查询某月预算（含总预算与分类预算，及已用/剩余/占比）
 * @param {string} month yyyy-MM
 */
function get(month, options) {
  return request({
    url: '/api/budget',
    method: 'GET',
    data: month ? { month } : {},
    silent: !!(options && options.silent)
  });
}

/** 设置预算（同月同分类 UPSERT）{ budgetMonth, categoryCode, amount, warnRatio } */
function save(payload) {
  return request({
    url: '/api/budget',
    method: 'POST',
    data: payload
  });
}

/** 删除预算（物理删除），后端为 @RequestParam */
function remove(budgetId) {
  return request({
    url: '/api/budget?budgetId=' + budgetId,
    method: 'DELETE'
  });
}

module.exports = { get, save, remove };