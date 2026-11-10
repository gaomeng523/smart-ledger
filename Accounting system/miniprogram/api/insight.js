/**
 * 消费洞察接口（V3 阶段一）
 */
const { request } = require('../utils/request');

/**
 * 记账后的即时洞察
 * @param {number} billId
 * @returns {Promise<Array<{type, level, title, body}>>}
 */
function afterSave(billId, options) {
  return request({
    url: '/api/insight/after-save',
    method: 'GET',
    data: { billId },
    silent: !!(options && options.silent)
  });
}

/**
 * 月度洞察（统计页卡片）
 * @param {string} [month] yyyy-MM，空则当月
 */
function month(month, options) {
  return request({
    url: '/api/insight/month',
    method: 'GET',
    data: month ? { month } : {},
    silent: !!(options && options.silent)
  });
}

module.exports = { afterSave, month };