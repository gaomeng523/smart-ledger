/**
 * 账单导入接口（V2，§4.8）
 *
 * 文件上传必须走 wx.uploadFile（不能走 request.js 的 wx.request），
 * 这里单独封装，并复用登录锁保证 Authorization 有效。
 */
const { BASE_URL } = require('../config/env');
const request = require('../utils/request');
const storage = require('../utils/storage');
const { CODE } = require('../config/const');

/**
 * 上传 CSV（dryRun=true 仅试算不入库）
 * @param {string} filePath wx.chooseMessageFile 返回的临时路径
 * @param {string} sourceType 'WECHAT' | 'ALIPAY'
 * @param {boolean} dryRun
 * @returns {Promise<ImportResultDTO>}
 */
function upload(filePath, sourceType, dryRun, options) {
  const silent = !!(options && options.silent);

  return request.ensureLogin().then(() => new Promise((resolve, reject) => {
    wx.uploadFile({
      url: BASE_URL + '/api/bill/import',
      filePath,
      name: 'file',
      formData: {
        sourceType,
        dryRun: dryRun ? 'true' : 'false'
      },
      header: {
        Authorization: 'Bearer ' + storage.getToken()
      },
      timeout: 30000,
      success: (res) => {
        let body = {};
        try {
          body = JSON.parse(res.data || '{}');
        } catch (e) {
          body = {};
        }
        const code = body.code !== undefined ? body.code : res.statusCode;
        if (code === CODE.SUCCESS) {
          resolve(body.data);
          return;
        }
        const err = {
          code,
          message: body.message || '导入失败',
          data: body.data === undefined ? null : body.data
        };
        if (!silent && (code === CODE.PARAM_ERROR ||
            code === CODE.SYSTEM_ERROR ||
            code === CODE.IMPORT_FORMAT_ERROR)) {
          request.toast(err.message);
        }
        reject(err);
      },
      fail: () => reject({
        code: CODE.NETWORK_ERROR,
        message: '网络异常，请检查网络后重试',
        data: null
      })
    });
  }));
}

/** 试算（dryRun=true，不入库） */
function preview(filePath, sourceType, options) {
  return upload(filePath, sourceType, true, options);
}

/** 确认导入（dryRun=false） */
function confirm(filePath, sourceType, options) {
  return upload(filePath, sourceType, false, options);
}

/** 撤销导入批次，后端为 @RequestParam */
function rollback(importId) {
  return request({
    url: '/api/import/rollback?importId=' + importId,
    method: 'POST'
  });
}

module.exports = { preview, confirm, rollback };