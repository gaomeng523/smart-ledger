/**
 * 设置页：昵称、本机数据（资产账户/导出/清缓存）、关于。
 * 说明：账单与字典在服务器，资产余额、使用天数只在本机。
 */
const storage = require('../../utils/storage');
const billExport = require('../../utils/bill-export');
const auth = require('../../utils/auth');
const { STORAGE_KEY } = require('../../config/const');

const VERSION = '2.6.0';
const MODEL = 'Lumen 2';
const ASSET_KEY = 'assetAccounts';
const FIRST_USE_KEY = 'firstUseDate';

Page({
  data: {
    nickname: '',
    accountCount: 0,
    usedDays: 1,
    version: VERSION,
    model: MODEL
  },

  onShow() {
    this.refresh();
  },

  refresh() {
    const accounts = storage.get(ASSET_KEY, []);
    this.setData({
      nickname: storage.getNickname(),
      accountCount: Array.isArray(accounts) ? accounts.length : 0,
      usedDays: this.calcUsedDays()
    });
  },

  calcUsedDays() {
    const first = storage.get(FIRST_USE_KEY, '');
    if (!first) {
      return 1;
    }
    const start = new Date(String(first).replace(/-/g, '/'));
    const days = Math.max(1, Math.round((Date.now() - start.getTime()) / 86400000) + 1);
    return isFinite(days) ? days : 1;
  },

  /* ---------- 账户 ---------- */

  onChangeNickname() {
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: '最多 12 个字',
      content: this.data.nickname || '',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        const name = String(res.content || '').trim().slice(0, 12);
        if (!name) {
          wx.showToast({ title: '昵称不能为空', icon: 'none' });
          return;
        }
        storage.setNickname(name);
        this.setData({ nickname: name });
        wx.showToast({ title: '已保存', icon: 'success' });
      }
    });
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新登录才能记账，本机昵称、头像与资产账户不会被删除。',
      confirmText: '退出',
      confirmColor: '#FF6B6B',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        auth.logout();
        wx.reLaunch({ url: '/pages/login/login' });
      }
    });
  },

  /* ---------- 数据 ---------- */

  onAsset() {
    wx.navigateTo({ url: '/pages/asset/asset' });
  },

  onExport() {
    billExport.chooseRange();
  },

  onClearCache() {
    wx.showModal({
      title: '清空本机缓存',
      content: '将清除本机保存的资产账户、分类字典缓存与使用天数，不影响服务器上的账单与昵称。',
      confirmText: '清空',
      confirmColor: '#FF6B6B',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        [ASSET_KEY, STORAGE_KEY.DICT, FIRST_USE_KEY].forEach((key) => storage.remove(key));
        this.refresh();
        wx.showToast({ title: '已清空', icon: 'success' });
      }
    });
  },

  /* ---------- 关于 ---------- */

  onAbout() {
    wx.showModal({
      title: 'AI 记账 v' + VERSION,
      content: '一句话或一张小票完成入账，账单按月汇总，支持两级分类与 AI 月报。',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  onPrivacy() {
    wx.showModal({
      title: '隐私与数据说明',
      content: '1. 记账文本与图片会上传到服务器用于解析和保存账单；\n2. 资产账户余额、昵称、使用天数只保存在本机，不会上传；\n3. 不采集通讯录、位置等无关信息。',
      showCancel: false,
      confirmText: '我知道了'
    });
  }
});
