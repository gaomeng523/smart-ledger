/**
 * 自定义导航（navigationStyle: custom）
 * 与右上角胶囊对齐：导航内容右侧预留胶囊宽度，标题可居中。
 */
Component({
  options: {
    multipleSlots: true
  },

  properties: {
    title: {
      type: String,
      value: ''
    },
    /** 左侧返回箭头 */
    back: {
      type: Boolean,
      value: false
    },
    /** 标题居中（日历 / 统计这类标题） */
    center: {
      type: Boolean,
      value: false
    }
  },

  data: {
    statusBarHeight: 20,
    barHeight: 44,
    rightSpace: 96
  },

  lifetimes: {
    attached() {
      const sys = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const statusBarHeight = sys.statusBarHeight || 20;
      let barHeight = 44;
      let rightSpace = 96;

      try {
        const rect = wx.getMenuButtonBoundingClientRect();
        if (rect && rect.height) {
          // 上下留白对称 → 导航条高度
          barHeight = (rect.top - statusBarHeight) * 2 + rect.height;
          rightSpace = (sys.windowWidth || 375) - rect.left + 8;
        }
      } catch (e) {
        // 取不到胶囊信息时用默认值，不影响展示
      }

      this.setData({ statusBarHeight, barHeight, rightSpace });
    }
  },

  methods: {
    onBack() {
      if (getCurrentPages().length > 1) {
        wx.navigateBack();
        return;
      }
      wx.switchTab({ url: '/pages/index/index' });
    }
  }
});
