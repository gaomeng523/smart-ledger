App({
  globalData: {
    /** 冷启动时间，用于首页性能观测（§10-1） */
    launchTs: Date.now()
  },

  /**
   * 启动不做登录门禁：审核要求游客先浏览体验，由用户主动选择授权登录。
   * 需要写入数据时（保存账单、导入等）再引导到登录页。
   */
  onError(err) {
    console.error('[app.onError]', err);
  }
});
