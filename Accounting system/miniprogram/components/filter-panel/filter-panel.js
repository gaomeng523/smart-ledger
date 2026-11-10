/**
 * 筛选面板（V2，§5、§4.10 ④）
 * P-02 半屏筛选面板，不新增页面。浮层阴影与 dict-picker 同层级规范（mask 300 / panel 301）。
 *
 * 事件：change(draft) / confirm(query) / reset / close
 */
const format = require('../../utils/format');

const SOURCE_OPTIONS = [
  { value: '', label: '全部' },
  { value: '3', label: '模板' },
  { value: '4', label: '导入' },
  { value: '6', label: '手动' }
];

Component({
  properties: {
    visible: {
      type: Boolean,
      value: false
    },
    /** 当前生效筛选条件 */
    value: {
      type: Object,
      value: {}
    },
    /** 标签候选 */
    tags: {
      type: Array,
      value: []
    },
    /** 试算结果数量（-1 表示未知，由页面请求后回填，§4.10 ④） */
    resultCount: {
      type: Number,
      value: -1
    }
  },

  data: {
    draft: {},
    sourceOptions: SOURCE_OPTIONS,
    maxDate: ''
  },

  observers: {
    visible(val) {
      if (val) {
        this.setData({ draft: this.normalize(this.data.value) });
      }
    }
  },

  lifetimes: {
    attached() {
      this.setData({ maxDate: format.todayStr() });
    }
  },

  methods: {
    normalize(value) {
      const v = value || {};
      return {
        amountMin: v.amountMin || '',
        amountMax: v.amountMax || '',
        dateStart: v.dateStart || '',
        dateEnd: v.dateEnd || '',
        tags: (v.tags || []).slice(),
        source: v.source === undefined || v.source === null ? '' : String(v.source)
      };
    },

    onAmountMin(e) {
      this.update('amountMin', e.detail.value);
    },

    onAmountMax(e) {
      this.update('amountMax', e.detail.value);
    },

    onDateStart(e) {
      this.update('dateStart', e.detail.value);
    },

    onDateEnd(e) {
      this.update('dateEnd', e.detail.value);
    },

    onTagsChange(e) {
      this.update('tags', e.detail || []);
    },

    onSourceTap(e) {
      this.update('source', e.currentTarget.dataset.value);
    },

    /** 更新草稿并抛出 change（页面据此试算结果数量） */
    update(key, value) {
      const draft = Object.assign({}, this.data.draft);
      draft[key] = value;
      this.setData({ draft });
      this.triggerEvent('change', draft);
    },

    onReset() {
      this.setData({ draft: this.normalize({}) });
      this.triggerEvent('reset');
    },

    onConfirm() {
      const draft = this.data.draft;
      const min = Number(draft.amountMin);
      const max = Number(draft.amountMax);
      if (draft.amountMin !== '' && draft.amountMax !== '' &&
          isFinite(min) && isFinite(max) && min > max) {
        wx.showToast({ title: '金额下限不能大于上限', icon: 'none' });
        return;
      }
      if (draft.dateStart && draft.dateEnd && draft.dateStart > draft.dateEnd) {
        wx.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' });
        return;
      }
      this.triggerEvent('confirm', draft);
    },

    close() {
      this.triggerEvent('close');
    },

    noop() {}
  }
});