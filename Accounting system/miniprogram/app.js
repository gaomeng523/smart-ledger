const auth = require('./utils/auth');

App({
  globalData: {
    /** 冷启动时间，用于首页性能观测（§10-1） */
    launchTs: Date.now()
  },

  onLaunch() {
    // 登录门禁：未登录先进登录页，登录页点击后才算登录（不做静默自动登录）
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' });
    }
  },

  onError(err) {
    console.error('[app.onError]', err);
  }
});
