/**
 * 字典接口（渠道 + 分类）
 */
const { request } = require('../utils/request');

/**
 * 获取全量字典
 * @returns {Promise<{channels: Array, categories: Array}>}
 */
function all(options) {
  return request({
    url: '/api/dict/all',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

module.exports = { all };