/**
 * 月份选择器（§5）：提供近 12 个月。
 * 属性：value（yyyy-MM）
 * 事件：change → month（yyyy-MM）
 */
const format = require('../../utils/format');

const MONTH_COUNT = 12;

Component({
  properties: {
    value: {
      type: String,
      value: ''
    }
  },

  data: {
    show: false,
    list: [],
    selectedText: ''
  },

  observers: {
    value(value) {
      this.setData({ selectedText: format.monthToText(value) });
    }
  },

  lifetimes: {
    attached() {
      const list = format.recentMonths(MONTH_COUNT).map((month) => ({
        value: month,
        text: format.monthToText(month)
      }));
      this.setData({
        list,
        selectedText: format.monthToText(this.data.value)
      });
    }
  },

  methods: {
    open() {
      this.setData({ show: true });
    },

    close() {
      this.setData({ show: false });
    },

    choose(e) {
      const month = e.currentTarget.dataset.value;
      this.setData({ show: false });
      this.triggerEvent('change', month);
    },

    noop() {}
  }
});