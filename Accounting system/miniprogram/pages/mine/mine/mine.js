/**
 * 我的（tab）· 复用「我的」抽屉内容
 */
const storage = require('../../../utils/storage');
const request = require('../../../utils/request');
const auth = require('../../../utils/auth');

Page({
  data: {
    statusBarHeight: 20
  },

  onLoad() {
    try {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      this.setData({ statusBarHeight: info.statusBarHeight || 20 });
    } catch (e) {
      this.setData({ statusBarHeight: 20 });
    }
  },

  onShow() {
    if (!auth.guard()) {
      return;
    }
    const menu = this.selectComponent('#menu');
    if (menu) {
      menu.refreshProfile();
      menu.loadCount();
    }
  },

  onProfile() {
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
