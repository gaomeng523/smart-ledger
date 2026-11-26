/**
 * 用户相关接口
 */
const { request } = require('../utils/request');

/**
 * 重置当前用户的全部记账数据（保留账号、昵称与头像）
 * @returns {Promise<void>}
 */
function resetData() {
  return request({
    url: '/api/user/reset',
    method: 'POST',
    silent: true
  });
}

module.exports = {
  resetData
};
