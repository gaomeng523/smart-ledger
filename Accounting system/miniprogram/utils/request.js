/**
 * 请求封装（§7.3、§6.2、§6.3）
 *
 * 职责：
 * 1. 拼接 BASE_URL，自动附带 Authorization: Bearer <token>
 * 2. 统一解包 R：code === 0 → resolve(data)，否则 reject({code, message, data})
 * 3. 401 静默重登（登录加锁，只发一次 wx.login）并自动重放 1 次
 * 4. 通用错误提示（400 / 429 / 500 / 网络异常）；1002/1003/1004/1005 等由页面处理
 */
const { ENV, BASE_URL } = require('../config/env');
const { CODE } = require('../config/const');
const storage = require('./storage');

/** 登录锁：并发 401 时只发起一次 wx.login */
let loginPromise = null;

/** wx.login 超时保护（ms）：开发者工具异常时回调可能不触发，避免登录锁永久挂起 */
const LOGIN_TIMEOUT = 5000;

function toast(message) {
  wx.showToast({
    title: message || '操作失败',
    icon: 'none',
    duration: 2000
  });
}

function rawRequest(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: Object.assign(
        { 'Content-Type': 'application/json' },
        options.header || {}
      ),
      timeout: options.timeout || 15000,
      success: resolve,
      fail: (err) => {
        console.error('[request.fail]', options.url, err);
        reject({
          code: CODE.NETWORK_ERROR,
          message: '网络异常，请检查网络后重试',
          data: null
        });
      }
    });
  });
}

function wxLoginCode() {
  return new Promise((resolve, reject) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) {
        return;
      }
      settled = true;
      reject({ code: CODE.WX_LOGIN_FAILED, message: '微信登录超时，请重试', data: null });
    }, LOGIN_TIMEOUT);

    const done = (fn) => (arg) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timer);
      fn(arg);
    };

    wx.login({
      success: done((res) => {
        if (res && res.code) {
          resolve(res.code);
        } else {
          reject({ code: CODE.WX_LOGIN_FAILED, message: '微信登录失败', data: null });
        }
      }),
      fail: done(() => reject({ code: CODE.WX_LOGIN_FAILED, message: '微信登录失败', data: null }))
    });
  });
}

/**
 * 取登录 code
 * dev 环境兜底：后端 `mock-enabled: true` 时接受任意 code，
 * 因此在开发者工具里 wx.login 不可用时改用本地伪 code，保证本地联调不被阻塞。
 */
function loginCode() {
  return wxLoginCode().catch((err) => {
    if (ENV !== 'dev') {
      throw err;
    }
    console.warn('[login] wx.login 不可用，dev 环境改用 mock code：', err && err.message);
    return 'dev-mock-' + Date.now() + '-' + Math.floor(Math.random() * 1000000);
  });
}

/** 执行一次登录（不走 request，避免鉴权递归） */
function doLogin() {
  return loginCode()
    .then((code) => rawRequest({
      url: '/api/wx/login',
      method: 'POST',
      data: { code }
    }))
    .then((res) => {
      const body = res.data || {};
      if (body.code === CODE.SUCCESS && body.data && body.data.token) {
        storage.setToken(body.data.token);
        if (body.data.nickname) {
          storage.setNickname(body.data.nickname);
        }
        return body.data.token;
      }
      throw {
        code: body.code || CODE.WX_LOGIN_FAILED,
        message: body.message || '微信登录失败',
        data: body.data
      };
    });
}

/** 确保已登录（有 token 直接返回，否则登录；并发加锁） */
function ensureLogin() {
  const token = storage.getToken();
  if (token) {
    return Promise.resolve(token);
  }
  if (loginPromise) {
    return loginPromise;
  }
  loginPromise = doLogin();
  const release = () => {
    loginPromise = null;
  };
  loginPromise.then(release, release);
  return loginPromise;
}

/**
 * 发起请求
 * @param {Object} options
 * @param {string} options.url        以 / 开头的接口路径
 * @param {string} [options.method]   GET / POST / PUT / DELETE
 * @param {Object} [options.data]     请求体或查询参数
 * @param {boolean} [options.auth]    是否需要鉴权，默认 true
 * @param {boolean} [options.silent]  静默模式：不弹通用错误提示，默认 false
 * @param {boolean} [options.retry]   内部使用：是否允许 401 重放
 */
function request(options) {
  const opts = Object.assign({ auth: true, silent: false, retry: true }, options);

  const withAuth = opts.auth ? ensureLogin() : Promise.resolve('');

  return withAuth
    .catch((err) => {
      if (!opts.silent) {
        toast(err && err.message ? err.message : '微信登录失败');
      }
      throw err;
    })
    .then(() => {
      if (opts.auth) {
        const token = storage.getToken();
        opts.header = Object.assign({}, opts.header, {
          Authorization: 'Bearer ' + token
        });
      }
      return rawRequest(opts);
    })
    .catch((err) => {
      // 网络异常统一提示（登录失败的提示已在上一段处理）
      if (err && err.code === CODE.NETWORK_ERROR && !opts.silent) {
        toast(err.message);
      }
      throw err;
    })
    .then((res) => {
      const body = res.data || {};
      const code = body.code !== undefined ? body.code : res.statusCode;

      if (code === CODE.SUCCESS) {
        return body.data;
      }

      // 401：清除 token → 静默重登 → 重放 1 次（§6.3）
      if (code === CODE.UNAUTHORIZED && opts.auth && opts.retry) {
        storage.clearToken();
        loginPromise = null;
        return ensureLogin()
          .catch(() => {
            throw { code: CODE.WX_LOGIN_FAILED, message: '登录已失效，请重新进入', data: null };
          })
          .then(() => request(Object.assign({}, opts, { retry: false })));
      }

      const err = {
        code,
        message: body.message || '操作失败',
        data: body.data === undefined ? null : body.data
      };

      if (!opts.silent) {
        if (code === CODE.PARAM_ERROR ||
            code === CODE.TOO_MANY_REQUESTS ||
            code === CODE.SYSTEM_ERROR) {
          toast(err.message);
        } else if (code === CODE.WX_LOGIN_FAILED) {
          storage.clearToken();
          toast('登录已失效，请重新进入');
        }
      }

      throw err;
    });
}

/**
 * 同时支持两种引用方式（项目内两种写法并存，见 §7.3）：
 *   const request = require('...');  request({...})   /  request.toast(...)
 *   const { request, ensureLogin, toast } = require('...');
 */
module.exports = request;
module.exports.request = request;
module.exports.ensureLogin = ensureLogin;
module.exports.toast = toast;