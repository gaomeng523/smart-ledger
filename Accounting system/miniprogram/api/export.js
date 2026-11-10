/**
 * 数据导出相关接口（V3 · F-91）
 * 导出走 wx.downloadFile（文件流），需手动携带 Authorization。
 */
const { BASE_URL } = require('../config/env');
const request = require('../utils/request');
const storage = require('../utils/storage');

/** 过滤空参数并拼接完整导出地址 */
function buildBillUrl(query) {
  const params = {};
  Object.keys(query || {}).forEach((key) => {
    const value = query[key];
    if (value !== undefined && value !== null && value !== '') {
      params[key] = value;
    }
  });
  const qs = Object.keys(params)
    .map((key) => encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
    .join('&');
  return BASE_URL + '/api/export/bill' + (qs ? '?' + qs : '');
}

/**
 * 下载账单 CSV，成功 resolve wx.downloadFile 的结果（含 tempFilePath）。
 * 无本地 token 时先走一次登录；401 / 网络错误统一 reject。
 */
function downloadBill(query) {
  const ready = storage.getToken()
    ? Promise.resolve()
    : request.ensureLogin();

  return ready.then(() => new Promise((resolve, reject) => {
    wx.downloadFile({
      url: buildBillUrl(query),
      header: { Authorization: 'Bearer ' + storage.getToken() },
      success(res) {
        if (res.statusCode === 200) {
          resolve(res);
        } else {
          reject({ code: res.statusCode, message: '导出失败，请稍后重试' });
        }
      },
      fail(err) {
        reject({ code: -1, message: '网络异常，请检查网络后重试', raw: err });
      }
    });
  }));
}

module.exports = { buildBillUrl, downloadBill };
