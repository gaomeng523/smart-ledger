/**
 * 线性图标（mask + SVG），name 见 ui-icon.wxss 的 .ic--* 类。
 */
Component({
  properties: {
    /** 图标名，如 spark / calendar */
    name: {
      type: String,
      value: ''
    },
    /** 尺寸（rpx） */
    size: {
      type: Number,
      value: 40
    },
    /** 颜色 */
    color: {
      type: String,
      value: '#D3D4D2'
    }
  }
});
