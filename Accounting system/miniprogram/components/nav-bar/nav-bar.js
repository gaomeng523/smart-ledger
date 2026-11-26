/**
 * 自定义导航（navigationStyle: custom）
 * 高 44px，标题左对齐 18/600；右侧为胶囊预留宽度（≥100px），按钮不得紧贴胶囊。
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
    }
  },

  data: {
    statusBarHeight: 20,
    barHeight: 44,
    rightSpace: 100
  },

  lifetimes: {
    attached() {
      const sys = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const statusBarHeight = sys.statusBarHeight || 20;
      let barHeight = 44;
      let rightSpace = 100;

      try {
        const rect = wx.getMenuButtonBoundingClientRect();
        if (rect && rect.height) {
          barHeight = (rect.top - statusBarHeight) * 2 + rect.height;
          rightSpace = (sys.windowWidth || 375) - rect.left + 12;
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
