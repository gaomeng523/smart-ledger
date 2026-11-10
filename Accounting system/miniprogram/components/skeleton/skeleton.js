/**
 * 骨架屏（§5）：列表/统计首屏加载占位，底色 --color-bg-muted 呼吸动画。
 */
Component({
  properties: {
    /** 占位行数 */
    rows: {
      type: Number,
      value: 3
    }
  },

  data: {
    list: [0, 1, 2]
  },

  observers: {
    rows(rows) {
      const list = [];
      const count = Math.max(1, Number(rows) || 1);
      for (let i = 0; i < count; i++) {
        list.push(i);
      }
      this.setData({ list });
    }
  }
});