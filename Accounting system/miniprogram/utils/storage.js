/**
 * 本地存储封装（§7.1）
 * 只允许存储 token / dict / nickname，禁止存储任何敏感信息。
 */
const { STORAGE_KEY } = require('../config/const');

function get(key, defaultValue) {
  try {
    const value = wx.getStorageSync(key);
    if (value === '' || value === undefined || value === null) {
      return defaultValue;
    }
    return value;
  } catch (e) {
    return defaultValue;
  }
}

function set(key, value) {
  try {
    wx.setStorageSync(key, value);
  } catch (e) {
    console.error('[storage.set]', key, e);
  }
}

function remove(key) {
  try {
    wx.removeStorageSync(key);
  } catch (e) {
    console.error('[storage.remove]', key, e);
  }
}

/* ---- token ---- */

function getToken() {
  return get(STORAGE_KEY.TOKEN, '');
}

function setToken(token) {
  set(STORAGE_KEY.TOKEN, token);
}

function clearToken() {
  remove(STORAGE_KEY.TOKEN);
}

/* ---- 昵称 ---- */

function getNickname() {
  return get(STORAGE_KEY.NICKNAME, '');
}

function setNickname(nickname) {
  set(STORAGE_KEY.NICKNAME, nickname);
}

module.exports = {
  get,
  set,
  remove,
  getToken,
  setToken,
  clearToken,
  getNickname,
  setNickname
};