/**
 * 快捷模板接口（V2，§4.6）
 */
const { request } = require('../utils/request');

/** 模板列表（后端按 useCount DESC, sortNo ASC 排序） */
function list(options) {
  return request({
    url: '/api/template',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

/** 新增模板 { title, billType, amount, payChannelCode, categoryCode, remark } */
function create(payload) {
  return request({
    url: '/api/template',
    method: 'POST',
    data: payload
  });
}

/** 修改模板 { templateId, title, billType, amount, payChannelCode, categoryCode, remark } */
function update(payload) {
  return request({
    url: '/api/template',
    method: 'PUT',
    data: JSON.stringify(payload),
    header: { 'Content-Type': 'application/json' }
  });
}

/** 删除模板（逻辑删除），后端为 @RequestParam */
function remove(templateId) {
  return request({
    url: '/api/template?templateId=' + templateId,
    method: 'DELETE'
  });
}

/** 一键模板记账（跳过 AI），后端为 @RequestParam */
function use(templateId) {
  return request({
    url: '/api/template/use?templateId=' + templateId,
    method: 'POST'
  });
}

module.exports = { list, create, update, remove, use };