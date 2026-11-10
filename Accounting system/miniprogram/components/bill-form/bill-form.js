/**
 * 记账表单（§5）：首页降级表单与解析预览弹窗共用。
 * 受控组件：字段值来自 value 属性，任一字段变化抛出 change(value)。
 *
 * 事件：change → value（完整表单对象）
 *
 * focusAmount 属性（可选）：为 true 时自动拉起金额输入框（1004 降级聚焦，§4.1.2）
 *
 * marks 属性（可选，用于警示样式）：
 *   { amount: 'missing' | 'low', payChannelCode: ..., categoryCode: ..., recordDate: ... }
 *   - 值为 'missing' 时右侧显示「待补充」
 *   - 任意真值都给该字段加警示描边
 */
const format = require('../../utils/format');
const { BILL_TYPE } = require('../../config/const');

Component({
  properties: {
    /** 表单值：{ amount, billType, payChannelCode, categoryCode, recordDate, remark, tags } */
    value: {
      type: Object,
      value: {}
    },
    /** 全量字典 */
    dict: {
      type: Object,
      value: null
    },
    /** create | edit | fallback */
    mode: {
      type: String,
      value: 'create'
    },
    /** mode=fallback 时顶部吉祥物状态（见 mascot 组件） */
    mascotState: {
      type: String,
      value: 'expect'
    },
    /** 警示标记（见文件头说明） */
    marks: {
      type: Object,
      value: {}
    },
    /** 为 true 时自动拉起金额输入框（1004 降级场景，§4.1.2） */
    focusAmount: {
      type: Boolean,
      value: false
    },
    /** 历史标签候选（来自 /api/bill/tag/suggest，V2 §4.10 ④） */
    tagSuggestions: {
      type: Array,
      value: []
    },
    /** 是否展示标签行（模板表单等不支持标签的场景传 false） */
    showTags: {
      type: Boolean,
      value: true
    }
  },

  data: {
    maxDate: '',
    amountFocus: false,
    expense: BILL_TYPE.EXPENSE,
    income: BILL_TYPE.INCOME
  },

  observers: {
    focusAmount(val) {
      if (val) {
        this.focusAmountInput();
      }
    }
  },

  lifetimes: {
    attached() {
      // 日期上限：今天 +1 天（§6.4）
      const d = new Date();
      d.setDate(d.getDate() + 1);
      this.setData({ maxDate: format.toDateStr(d) });

      if (this.data.focusAmount) {
        this.focusAmountInput();
      }
    },
    detached() {
      if (this._focusTimer) {
        clearTimeout(this._focusTimer);
        this._focusTimer = null;
      }
    }
  },

  methods: {
    /** 先置 false 再置 true，确保重复触发时也能唤起键盘 */
    focusAmountInput() {
      if (this._focusTimer) {
        clearTimeout(this._focusTimer);
      }
      this.setData({ amountFocus: false });
      this._focusTimer = setTimeout(() => {
        this._focusTimer = null;
        this.setData({ amountFocus: true });
      }, 120);
    },

    emit(patch) {
      const next = Object.assign({}, this.data.value, patch);
      this.triggerEvent('change', next);
    },

    onAmountInput(e) {
      this.emit({ amount: e.detail.value });
    },

    onRemarkInput(e) {
      this.emit({ remark: e.detail.value });
    },

    onTagsChange(e) {
      this.emit({ tags: e.detail || [] });
    },

    onBillType(e) {
      const billType = Number(e.currentTarget.dataset.type);
      if (billType === Number(this.data.value.billType)) {
        return;
      }
      const patch = { billType };
      // 切换收支类型后，原分类不属于新类型则清空（§4.3 分类联动）
      const categories = (this.data.dict && this.data.dict.categories) || [];
      const current = categories.filter((c) => c.code === this.data.value.categoryCode)[0];
      if (current && Number(current.billType) !== billType) {
        patch.categoryCode = '';
        patch.categoryName = '';
      }
      this.emit(patch);
    },

    onChannelChange(e) {
      this.emit({
        payChannelCode: e.detail.code,
        payChannelName: e.detail.name
      });
    },

    onCategoryChange(e) {
      this.emit({
        categoryCode: e.detail.code,
        categoryName: e.detail.name
      });
    },

    onDateChange(e) {
      this.emit({ recordDate: e.detail.value });
    }
  }
});