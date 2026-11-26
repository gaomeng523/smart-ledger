/**
 * 我的（tab）· 复用「我的」抽屉内容
 */
const auth = require('../../../utils/auth');
const storage = require('../../../utils/storage');
const request = require('../../../utils/request');

Page({
  data: {},

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(3);
    }
    // 游客态也能查看菜单，点昵称/登录入口时再引导授权
    if (!auth.guard()) {
      const guestMenu = this.selectComponent('#menu');
      if (guestMenu) {
        guestMenu.refreshProfile();
      }
      return;
    }
    const menu = this.selectComponent('#menu');
    if (menu) {
      menu.refreshProfile();
      menu.loadCount();
    }
  },

  /** 点头像 / 昵称：游客先去登录，已登录则改名 */
  onProfile() {
    if (auth.requireLogin()) {
      return;
    }
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: '输入昵称',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        const nickname = (res.content || '').trim();
        if (!nickname) {
          request.toast('昵称不能为空');
          return;
        }
        if (nickname.length > 12) {
          request.toast('昵称最多 12 个字');
          return;
        }
        storage.setNickname(nickname);
        const menu = this.selectComponent('#menu');
        if (menu) {
          menu.refreshProfile();
        }
        request.toast('已更新');
      }
    });
  }
});
