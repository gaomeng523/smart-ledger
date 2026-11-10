/**
 * 微信登录接口
 */
const { request } = require('../utils/request');

/**
 * 微信登录
 * @param {string} code wx.login 返回的 code
 * @param {string} [nickname]
 * @returns {Promise<{token, isNewUser, expiresIn}>}
 */
function login(code, nickname) {
  return request({
    url: '/api/wx/login',
    method: 'POST',
    data: { code, nickname },
    auth: false,
    silent: true
  });
}

module.exports = { login };