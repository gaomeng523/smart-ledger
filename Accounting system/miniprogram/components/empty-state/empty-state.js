/**
 * 统一空态（§5）：吉祥物插画 + 文案 + 胶囊按钮。
 * 插画加载失败时由 mascot 组件自动降级，本组件保证文案与按钮仍然可用（§10-13）。
 */
Component({
  properties: {
    /** 吉祥物状态，默认 empty */
    mascotState: {
      type: String,
      value: 'empty'
    },
    text: {
      type: String,
      value: '还没有数据'
    },
    actionText: {
      type: String,
      value: ''
    }
  },

  methods: {
    onAction() {
      this.triggerEvent('action');
    }
  }
});