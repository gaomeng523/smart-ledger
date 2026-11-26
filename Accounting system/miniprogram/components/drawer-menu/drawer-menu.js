/**
 * 「我的」抽屉内容：首页左缘右滑与「我的」tab 共用。
 * 菜单名称与目标页面标题保持一一对应。
 */
const billApi = require('../../api/bill');
const auth = require('../../utils/auth');
const billExport = require('../../utils/bill-export');
const storage = require('../../utils/storage');
const avatar = require('../../utils/avatar');
const format = require('../../utils/format');
const usage = require('../../utils/usage');

const GROUPS = [
  {
    key: 'ledger',
    title: '账本与数据',
    items: [
      { key: 'asset', icon: 'wallet', label: '我的账户', url: '/pages/asset/asset' },
      { key: 'category', icon: 'tag', label: '分类管理', url: '/pages/category/category' },
      { key: 'search', icon: 'search', label: '搜索账单', url: '/pages/bill/list/list', tab: true, action: 'search' },
      { key: 'import', icon: 'in', label: '导入账单', url: '/pages/import/import/import' },
      { key: 'export', icon: 'out', label: '导出账单', action: 'export' }
    ]
  },
  {
    key: 'plan',
    title: '计划与工具',
    items: [
      { key: 'subscription', icon: 'repeat', label: '订阅管理', url: '/pages/subscription/subscription/subscription' },
      { key: 'savings', icon: 'target', label: '心愿储蓄', url: '/pages/savings/savings/savings' },
      { key: 'template', icon: 'receipt', label: '模板管理', url: '/pages/template/list/list' },
      { key: 'alias', icon: 'book', label: '别名词典', url: '/pages/alias/alias/alias' }
    ]
  },
  {
    key: 'setting',
    title: '设置',
    items: [
      { key: 'settings', icon: 'gear', label: '设置', url: '/pages/settings/settings' }
    ]
  }
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
    /** 游客态：不展示微信头像授权按钮，点头像引导登录 */
    guest: false,
    nickname: '',
    avatarText: '记',
    avatarPath: '',
    billCount: 0,
    usedDays: 1,
    monthNum: new Date().getMonth() + 1,
    groups: GROUPS,
    version: '1.0.3'
  },

  lifetimes: {
    attached() {
      const guest = !auth.isLoggedIn();
      // 游客态不读取本机昵称/头像，避免退出后仍显示个人信息
      this.setData({
        nickname: guest ? '' : this.readNickname(),
        avatarPath: guest ? '' : avatar.read()
      });
      this.setData({ usedDays: usage.usedDays(), guest });
      // 游客态不发请求，避免浏览时产生任何登录行为
      if (!guest) {
        this.loadCount();
      }
    }
  },

  methods: {
    onProfileTap() {
      this.triggerEvent('profile');
    },

    /** 昵称/头像/登录态变更后由宿主页面调用 */
    refreshProfile() {
      const guest = !auth.isLoggedIn();
      this.setData({
        nickname: guest ? '' : this.readNickname(),
        avatarPath: guest ? '' : avatar.read(),
        guest
      });
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

    loadCount() {
      billApi.list({ page: 1, size: 1 }, { silent: true })
        .then((page) => this.setData({ billCount: (page && page.total) || 0 }))
        .catch(() => {});
    },

    onReportTap() {
      this.triggerEvent('navigate');
      wx.switchTab({ url: '/pages/stat/stat/stat' });
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
      if (action === 'search') {
        storage.set('pendingBillSearch', 1);
      }
      if (tab) {
        wx.switchTab({ url });
      } else {
        wx.navigateTo({ url });
      }
    }
  }
});
