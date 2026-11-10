/**
 * 别名词典接口（V2，§4.9）
 */
const { request } = require('../utils/request');

/** 别名列表 */
function list(options) {
  return request({
    url: '/api/alias',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

/** 新增别名 { keyword, targetType(1 分类/2 渠道), targetCode } */
function create(payload) {
  return request({
    url: '/api/alias',
    method: 'POST',
    data: payload
  });
}

/** 修改别名 { aliasId, keyword, targetType, targetCode } */
function update(payload) {
  return request({
    url: '/api/alias',
    method: 'PUT',
    data: JSON.stringify(payload),
    header: { 'Content-Type': 'application/json' }
  });
}

/** 删除别名，后端为 @RequestParam */
function remove(aliasId) {
  return request({
    url: '/api/alias?aliasId=' + aliasId,
    method: 'DELETE'
  });
}

module.exports = { list, create, update, remove };