/**
 * 设置页：昵称、本机数据（资产账户/导出/清缓存）、关于。
 * 说明：账单与字典在服务器，资产余额、使用天数只在本机。
 */
const storage = require('../../utils/storage');
const assetAccounts = require('../../utils/asset-accounts');
const billExport = require('../../utils/bill-export');
const auth = require('../../utils/auth');
const usage = require('../../utils/usage');
const userApi = require('../../api/user');
const store = require('../../store/index');
const { STORAGE_KEY } = require('../../config/const');

const VERSION = '1.0.3';
const MODEL = '本地规则';
const ASSET_KEY = assetAccounts.STORE_KEY;
const FIRST_USE_KEY = usage.FIRST_USE_KEY;

Page({
  data: {
    nickname: '',
    accountCount: 0,
    usedDays: 1,
    version: VERSION,
    model: MODEL,
    guest: false
  },

  onShow() {
    this.refresh();
  },

  refresh() {
    // 与资产体检页同源：未保存过时使用同一份预置账户
    const accounts = assetAccounts.load();
    const guest = !auth.isLoggedIn();
    this.setData({
      nickname: guest ? '' : storage.getNickname(),
      accountCount: accounts.length,
      usedDays: usage.usedDays(),
      guest
    });
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

  /** 游客态：由用户主动点击进入登录页 */
  onLogin() {
    auth.requireLogin();
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
        // 退出后回到首页游客态，不强制停在登录页
        wx.switchTab({ url: '/pages/index/index' });
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

  /** 重置所有数据：两次确认，避免误触 */
  onResetAll() {
    if (this.resetting) {
      return;
    }
    if (!auth.isLoggedIn()) {
      auth.requireLogin();
      return;
    }
    wx.showModal({
      title: '重置所有数据',
      content: '将清空云端的全部账单、预算、订阅、快捷模板、心愿储蓄、分类别名与导入记录。昵称、头像、分类字典和本机资产账户会保留。',
      confirmText: '继续',
      confirmColor: '#FF6B6B',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        wx.showModal({
          title: '确认重置？',
          content: '重置后数据无法恢复，建议先「导出账单」备份。',
          confirmText: '确认重置',
          confirmColor: '#FF6B6B',
          success: (res2) => {
            if (res2.confirm) {
              this.doResetAll();
            }
          }
        });
      }
    });
  },

  doResetAll() {
    this.resetting = true;
    wx.showLoading({ title: '正在重置…', mask: true });
    userApi.resetData()
      .then(() => {
        // 月度统计快照是内存缓存，清掉避免首页闪旧数字
        store.setMonthStat(null);
        // 通知首页清空本会话的对话与待存草稿
        const app = getApp();
        if (app && app.globalData) {
          app.globalData.resetAt = Date.now();
        }
        wx.hideLoading();
        this.resetting = false;
        this.refresh();
        wx.showToast({ title: '已重置', icon: 'success' });
      })
      .catch((err) => {
        wx.hideLoading();
        this.resetting = false;
        wx.showToast({
          title: (err && err.message) || '重置失败，请稍后重试',
          icon: 'none'
        });
      });
  },

  /* ---------- 关于 ---------- */

  onAbout() {
    wx.showModal({
      title: '萌萌记账 v' + VERSION,
      content: '说一句或手动填写即可入账，账单按月汇总，支持两级分类与月度报告。',
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
