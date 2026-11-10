/**
 * 账单相关接口
 */
const { request } = require('../utils/request');

/**
 * AI 解析自然语言 → 结构化草稿（不落库）
 * @param {string} text 1~200 字
 * @param {Object} [options] { silent }
 * @returns {Promise<ParseResultDTO>}
 */
function parse(text, options) {
  return request({
    url: '/api/bill/parse',
    method: 'POST',
    data: { text },
    silent: !!(options && options.silent)
  });
}

/**
 * 保存账单（幂等：同一 requestId 不会重复落库）
 * @param {Object} payload { requestId, amount, billType, payChannelCode, categoryCode, recordDate, remark, recordText, source, confidence, tags, force }
 *   命中疑似重复（1005）时，前端二次确认后带 force=true 与「新的」requestId 重提。
 */
function save(payload, options) {
  return request({
    url: '/api/bill/save',
    method: 'POST',
    data: payload,
    silent: !!(options && options.silent)
  });
}

/** 过滤掉 undefined / null / 空串，避免拼进 query string */
function cleanParams(query) {
  const params = {};
  Object.keys(query || {}).forEach((key) => {
    const value = query[key];
    if (value !== undefined && value !== null && value !== '') {
      params[key] = value;
    }
  });
  return params;
}

/**
 * 账单分页列表
 * @param {Object} query { page, size, month, billType, categoryCode, payChannelCode, keyword,
 *                         amountMin, amountMax, dateStart, dateEnd, tag, source }
 */
function list(query, options) {
  return request({
    url: '/api/bill/list',
    method: 'GET',
    data: cleanParams(query),
    silent: !!(options && options.silent)
  });
}

/** 历史标签联想（V2）→ { tags: string[] } */
function tagSuggest(options) {
  return request({
    url: '/api/bill/tag/suggest',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

/** 账单详情 */
function detail(billId) {
  return request({
    url: '/api/bill/detail',
    method: 'GET',
    data: { billId }
  });
}

/** 修改账单（后端为 @RequestBody，需显式 JSON 序列化） */
function update(payload) {
  return request({
    url: '/api/bill/update',
    method: 'PUT',
    data: JSON.stringify(payload),
    header: { 'Content-Type': 'application/json' }
  });
}

/** 删除账单（后端为 @RequestParam，走 query string） */
function remove(billId) {
  return request({
    url: '/api/bill/delete?billId=' + billId,
    method: 'DELETE'
  });
}

module.exports = {
  parse,
  save,
  list,
  tagSuggest,
  detail,
  update,
  remove
};