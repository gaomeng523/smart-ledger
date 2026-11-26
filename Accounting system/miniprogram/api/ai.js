/**
 * 智能账单问答接口
 */
const { request } = require('../utils/request');

/**
 * 自然语言问账本 → 中文回答
 * @param {string} question 1~100 字，如「这个月吃饭花了多少」
 * @param {Object} [options] { silent }
 * @returns {Promise<AskAnswerDTO>} { answer, sql, rows, latencyMs }
 */
function ask(question, options) {
  return request({
    url: '/api/ai/ask',
    method: 'POST',
    data: { question },
    silent: !!(options && options.silent)
  });
}

module.exports = {
  ask
};
