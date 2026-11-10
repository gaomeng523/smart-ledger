/**
 * 周期订阅接口（V3 阶段一）
 */
const { request } = require('../utils/request');

/** 我的订阅列表 */
function list(options) {
  return request({
    url: '/api/subscription',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

/** 扫描历史账单 → 待确认的订阅候选 */
function suggest(options) {
  return request({
    url: '/api/subscription/suggest',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

/** 确认候选 / 手动新建 { title, categoryCode, payChannelCode, avgAmount, cycleDays, nextDate } */
function create(payload) {
  return request({
    url: '/api/subscription',
    method: 'POST',
    data: payload
  });
}

/** 编辑 / 暂停 / 恢复 { id, title, avgAmount, cycleDays, nextDate, status } */
function update(payload) {
  return request({
    url: '/api/subscription',
    method: 'PUT',
    data: payload
  });
}

/** 删除（后端为 @RequestParam） */
function remove(id) {
  return request({
    url: '/api/subscription?id=' + id,
    method: 'DELETE'
  });
}

module.exports = { list, suggest, create, update, remove };