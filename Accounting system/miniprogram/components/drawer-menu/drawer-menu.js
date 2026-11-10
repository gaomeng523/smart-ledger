/**
 * 「我的」抽屉内容：首页左缘右滑与「我的」tab 共用。
 * 数据：昵称、累计记账笔数、使用天数；设置与导出分别走设置页、utils/bill-export。
 */
const billApi = require('../../api/bill');
const billExport = require('../../utils/bill-export');
const storage = require('../../utils/storage');
const avatar = require('../../utils/avatar');
const format = require('../../utils/format');

const FIRST_USE_KEY = 'firstUseDate';

const MENUS = [
  { key: 'asset', icon: 'wallet', label: '我的账本', tip: '日常账本', url: '/pages/asset/asset' },
  { key: 'report', icon: 'spark', label: 'AI 月报', badge: 'NEW', url: '/pages/stat/stat/stat', tab: true, ai: true },
  { key: 'category', icon: 'tag', label: '分类管理', url: '/pages/category/category' },
  { key: 'search', icon: 'search', label: '搜索账单', url: '/pages/bill/list/list', tab: true },
  { key: 'subscription', icon: 'repeat', label: '分期 · 周期', url: '/pages/subscription/subscription/subscription' },
  { key: 'savings', icon: 'target', label: '存钱计划', url: '/pages/savings/savings/savings' },
  { key: 'template', icon: 'receipt', label: '模板 · 预算', url: '/pages/template/list/list' },
  { key: 'settings', icon: 'gear', label: '设置', url: '/pages/settings/settings' }
];

const MORE = [
  { key: 'import', icon: 'in', label: '账单导入', url: '/pages/import/import/import' },
  { key: 'alias', icon: 'book', label: '别名词典', url: '/pages/alias/alias/alias' },
  { key: 'export', icon: 'out', label: '数据导出', action: 'export' }
];

Component({
  properties: {
    /** drawer：首页抽屉；page：我的页 */
    mode: {
      type: String,
      value: 'drawer'
    }
  },

  data: {
    nickname: '',
    avatarText: '记',
    avatarPath: '',
    billCount: 0,
    usedDays: 1,
    menus: MENUS,
    more: MORE,
    version: '2.6.0'
  },

  lifetimes: {
    attached() {
      this.setData({ nickname: this.readNickname(), avatarPath: avatar.read() });
      this.markFirstUse();
      this.loadCount();
    }
  },

  methods: {
    onProfileTap() {
      this.triggerEvent('profile');
    },

    /** 昵称/头像变更后由宿主页面调用 */
    refreshProfile() {
      this.setData({ nickname: this.readNickname(), avatarPath: avatar.read() });
    },

    readNickname() {
      const nickname = storage.getNickname() || '';
      const avatarText = nickname ? String(nickname).charAt(0) : '记';
      this.setData({ avatarText });
      return nickname;
    },

    /** 微信头像选择（open-type=chooseAvatar）：转存本机，换设备不保留 */
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

    noop() {},

    markFirstUse() {
      const today = format.todayStr();
      let first = storage.get(FIRST_USE_KEY, '');
      if (!first) {
        storage.set(FIRST_USE_KEY, today);
        first = today;
      }
      const start = new Date(first.replace(/-/g, '/'));
      const days = Math.max(1, Math.round((Date.now() - start.getTime()) / 86400000) + 1);
      this.setData({ usedDays: isFinite(days) ? days : 1 });
    },

    loadCount() {
      billApi.list({ page: 1, size: 1 }, { silent: true })
        .then((page) => this.setData({ billCount: (page && page.total) || 0 }))
        .catch(() => {});
    },

    onMenuTap(e) {
      const { url, tab, action } = e.currentTarget.dataset;
      if (action === 'export') {
        billExport.chooseRange();
        return;
      }
      if (!url) {
        return;
      }
      this.triggerEvent('navigate');
      if (tab) {
        wx.switchTab({ url });
      } else {
        wx.navigateTo({ url });
      }
    }
  }
});
