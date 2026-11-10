/**
 * 数据报告相关接口（V3 · F-44 AI 月报）
 */
const { request } = require('../utils/request');

/**
 * AI 月度消费报告
 * @param {string} month yyyy-MM，不传由后端取当前月
 * @returns {Promise<{month, aiEnabled, aiGenerated, summary, highlights, suggestions, stats}>}
 */
function month(month, options) {
  return request({
    url: '/api/report/month',
    method: 'GET',
    data: month ? { month } : {},
    silent: !!(options && options.silent)
  });
}

module.exports = { month };
