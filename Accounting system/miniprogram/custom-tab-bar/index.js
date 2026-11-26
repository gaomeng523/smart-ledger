/**
 * 自定义 tab 栏（高 56 + 安全区，图标 24px / 文字 12px）
 * 页面在 onShow 中调用 getTabBar().setSelected(n) 同步选中态。
 */
Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/index/index', text: '记账', icon: 'book' },
      { pagePath: '/pages/bill/list/list', text: '日历', icon: 'calendar' },
      { pagePath: '/pages/stat/stat/stat', text: '统计', icon: 'chart' },
      { pagePath: '/pages/mine/mine/mine', text: '我的', icon: 'user' }
    ]
  },

  methods: {
    onTap(e) {
      const { index, path } = e.currentTarget.dataset;
      if (index === this.data.selected) {
        return;
      }
      wx.switchTab({ url: path });
    },

    setSelected(index) {
      if (this.data.selected !== index) {
        this.setData({ selected: index });
      }
    }
  }
});
