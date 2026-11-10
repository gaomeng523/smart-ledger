/**
 * 心愿储蓄接口（V3 阶段二）
 */
const { request } = require('../utils/request');

/** 我的心愿列表 */
function list(options) {
  return request({
    url: '/api/savings',
    method: 'GET',
    silent: !!(options && options.silent)
  });
}

/** 新建心愿 { title, icon, targetAmount, dueDate, categoryCode } */
function create(payload) {
  return request({
    url: '/api/savings',
    method: 'POST',
    data: payload
  });
}

/** 编辑心愿 { id, title, icon, targetAmount, dueDate, categoryCode } */
function update(payload) {
  return request({
    url: '/api/savings',
    method: 'PUT',
    data: payload
  });
}

/** 存入 / 取出 { id, amount, withdraw } */
function changeAmount(payload) {
  return request({
    url: '/api/savings/amount',
    method: 'POST',
    data: payload
  });
}

/** 删除心愿（后端为 @RequestParam） */
function remove(id) {
  return request({
    url: '/api/savings?id=' + id,
    method: 'DELETE'
  });
}

module.exports = { list, create, update, changeAmount, remove };