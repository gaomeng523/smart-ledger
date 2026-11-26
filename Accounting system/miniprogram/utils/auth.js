/**
 * 登录态：必须由用户在登录页主动点击后才标记为已登录。
 * token 由 utils/request 静默维护，这里只负责门禁与退出。
 */
const storage = require('./storage');
const avatar = require('./avatar');

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
  // 同时清掉本机缓存的昵称与头像，退出后不再显示个人信息
  storage.clearNickname();
  avatar.remove();
}

/**
 * 页面守卫：未登录返回 false 表示游客态。
 * 按审核要求不强制跳登录页，未登录用户可以先浏览体验，需要数据时再由页面引导登录。
 */
function guard() {
  return isLoggedIn();
}

/** 需要登录的动作里调用：未登录时跳登录页并返回 true */
function requireLogin() {
  if (isLoggedIn()) {
    return false;
  }
  wx.navigateTo({ url: '/pages/login/login' });
  return true;
}

module.exports = {
  isLoggedIn,
  markLoggedIn,
  logout,
  guard,
  requireLogin
};
