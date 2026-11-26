/**
 * 账单相关接口
 */
const requestLib = require('../utils/request');
const { BASE_URL } = require('../config/env');
const storage = require('../utils/storage');

const { request } = requestLib;

/**
 * 解析自然语言 → 结构化草稿（不落库）
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
 * 拍照识别：上传小票图片 → 视觉模型解析成结构化草稿（不落库）
 * 走 wx.uploadFile（multipart），需手动携带 Authorization。
 * @param {string} filePath 本地临时文件路径（wx.chooseMedia 返回）
 * @returns {Promise<ParseResultDTO>} 额外带 imageUrl（已保存的小票地址）
 */
function parseImage(filePath) {
  const ready = storage.getToken()
    ? Promise.resolve()
    : requestLib.ensureLogin();

  return ready.then(() => new Promise((resolve, reject) => {
    wx.uploadFile({
      url: BASE_URL + '/api/bill/parse-image',
      filePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + storage.getToken() },
      success(res) {
        let body = {};
        try {
          body = JSON.parse(res.data || '{}');
        } catch (e) {
          reject({ code: -1, message: '识别失败，请重试' });
          return;
        }
        if (body.code === 0) {
          resolve(body.data || {});
        } else {
          reject({
            code: body.code,
            message: body.message || '识别失败，请重试',
            data: body.data || null
          });
        }
      },
      fail(err) {
        reject({ code: -1, message: '网络异常，请检查网络后重试', raw: err });
      }
    });
  }));
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
  parseImage,
  save,
  list,
  tagSuggest,
  detail,
  update,
  remove
};