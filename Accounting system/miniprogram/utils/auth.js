/**
 * 登录态：必须由用户在登录页主动点击后才标记为已登录。
 * token 由 utils/request 静默维护，这里只负责门禁与退出。
 */
const storage = require('./storage');

const LOGIN_KEY = 'loggedIn';

function isLoggedIn() {
  return !!storage.get(LOGIN_KEY, false) && !!storage.getToken();
}

function markLoggedIn() {
  storage.set(LOGIN_KEY, true);
}

function logout() {
  storage.remove(LOGIN_KEY);
  storage.clearToken();
}

/** 页面守卫：未登录直接回登录页，返回是否放行 */
function guard() {
  if (isLoggedIn()) {
    return true;
  }
  wx.reLaunch({ url: '/pages/login/login' });
  return false;
}

module.exports = {
  isLoggedIn,
  markLoggedIn,
  logout,
  guard
};
