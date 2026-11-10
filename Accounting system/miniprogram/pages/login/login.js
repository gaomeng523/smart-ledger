/**
 * 登录页：用户主动点击「微信一键登录」后才标记登录成功（不做静默自动登录）。
 * 登录后引导设置头像/昵称，都可跳过；头像只存本机。
 */
const auth = require('../../utils/auth');
const avatar = require('../../utils/avatar');
const storage = require('../../utils/storage');
const request = require('../../utils/request');

const FEATURES = [
  { icon: 'spark', color: '#A78BFF', text: '一句话 / 拍照，AI 自动记账' },
  { icon: 'chart', color: '#3D8BFF', text: '自动分类，按月出报表' },
  { icon: 'wallet', color: '#57D9A3', text: '资产体检，余额只存本机' }
];

Page({
  data: {
    statusBarHeight: 20,
    step: 'login',
    loading: false,
    features: FEATURES,
    nickname: '',
    avatarPath: '',
    avatarText: '记'
  },

  onLoad() {
    try {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      this.setData({ statusBarHeight: info.statusBarHeight || 20 });
    } catch (e) {
      this.setData({ statusBarHeight: 20 });
    }

    if (auth.isLoggedIn()) {
      this.goHome();
      return;
    }

    const nickname = storage.getNickname() || '';
    this.setData({
      nickname,
      avatarPath: avatar.read(),
      avatarText: nickname ? String(nickname).charAt(0) : '记'
    });
  },

  /* ---------- 步骤一：一键登录 ---------- */

  onLogin() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    request.ensureLogin()
      .then(() => {
        auth.markLoggedIn();
        this.setData({ loading: false, step: 'profile' });
      })
      .catch((err) => {
        this.setData({ loading: false });
        request.toast((err && err.message) || '登录失败，请重试');
      });
  },

  /* ---------- 步骤二：头像 / 昵称 ---------- */

  onChooseAvatar(e) {
    const tempPath = e.detail && e.detail.avatarUrl;
    if (!tempPath) {
      return;
    }
    wx.showLoading({ title: '保存中…', mask: true });
    avatar.save(tempPath)
      .then((path) => {
        wx.hideLoading();
        this.setData({ avatarPath: path });
      })
      .catch(() => {
        wx.hideLoading();
        wx.showToast({ title: '头像保存失败', icon: 'none' });
      });
  },

  onNicknameInput(e) {
    const nickname = e.detail.value || '';
    this.setData({
      nickname,
      avatarText: nickname ? String(nickname).charAt(0) : '记'
    });
  },

  onFinish() {
    this.saveNickname();
    this.goHome();
  },

  onSkip() {
    this.goHome();
  },

  saveNickname() {
    const name = String(this.data.nickname || '').trim().slice(0, 12);
    if (name) {
      storage.setNickname(name);
    }
  },

  onPrivacy() {
    wx.showModal({
      title: '隐私与数据说明',
      content: '1. 记账文本与图片会上传到服务器用于解析和保存账单；\n2. 资产账户余额、昵称、头像、使用天数只保存在本机，不会上传；\n3. 不采集通讯录、位置等无关信息。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' });
  }
});
