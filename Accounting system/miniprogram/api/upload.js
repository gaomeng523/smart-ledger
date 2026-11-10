/**
 * 图片上传接口（V3 阶段二，拍照记账）
 * 走 wx.uploadFile（multipart），需手动携带 Authorization。
 */
const { BASE_URL } = require('../config/env');
const request = require('../utils/request');
const storage = require('../utils/storage');

/**
 * 上传账单图片
 * @param {string} filePath 本地临时文件路径（wx.chooseMedia 返回）
 * @returns {Promise<{url: string}>} url 为可访问的相对地址，如 /uploads/2026/10/xxx.jpg
 */
function uploadImage(filePath) {
  const ready = storage.getToken()
    ? Promise.resolve()
    : request.ensureLogin();

  return ready.then(() => new Promise((resolve, reject) => {
    wx.uploadFile({
      url: BASE_URL + '/api/upload/image',
      filePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + storage.getToken() },
      success(res) {
        let body = {};
        try {
          body = JSON.parse(res.data || '{}');
        } catch (e) {
          reject({ code: -1, message: '上传失败，请重试' });
          return;
        }
        if (body.code === 0) {
          resolve(body.data || {});
        } else {
          reject({ code: body.code, message: body.message || '上传失败，请重试' });
        }
      },
      fail(err) {
        reject({ code: -1, message: '网络异常，请检查网络后重试', raw: err });
      }
    });
  }));
}

module.exports = { uploadImage };