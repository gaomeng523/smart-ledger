/**
 * 分类图标：单色线性图标，颜色由 background-color 控制（mask 只取轮廓）。
 * icon 取值见 utils/category-icon.js；未命中时不渲染。
 */
Component({
  properties: {
    /** 图标标识，如 food / takeout */
    icon: {
      type: String,
      value: ''
    },
    /** 尺寸（rpx） */
    size: {
      type: Number,
      value: 40
    },
    /** 颜色，默认浅灰（深色底） */
    color: {
      type: String,
      value: '#C9CAC6'
    }
  }
});
