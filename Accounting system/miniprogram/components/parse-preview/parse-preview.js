/**
 * 解析结果预览（半屏弹窗，§4.1.1、§5）
 * 顶部 32rpx 圆角；内部维护可编辑副本；confidence<0.5 与 missingFields 走警示样式。
 *
 * 属性：visible、draft、dict、saving
 * 事件：confirm(draft) / cancel
 */
const format = require('../../utils/format');
const { BILL_TYPE } = require('../../config/const');

Component({
  properties: {
    visible: {
      type: Boolean,
      value: false
    },
    /** ParseResultDTO */
    draft: {
      type: Object,
      value: null
    },
    dict: {
      type: Object,
      value: null
    },
    /** 保存中（由页面维护，防重复点击） */
    saving: {
      type: Boolean,
      value: false
    },
    /** 历史标签候选（透传给 bill-form，V2 §4.10 ④） */
    tagSuggestions: {
      type: Array,
      value: []
    }
  },

  data: {
    form: {},
    warn: false,
    marks: {},
    confidenceText: ''
  },

  observers: {
    'visible, draft': function (visible, draft) {
      if (!visible || !draft) {
        return;
      }
      this.setData(Object.assign({ form: this.buildForm(draft) }, this.buildMarks(draft)));
    }
  },

  methods: {
    buildForm(draft) {
      const hasAmount = draft.amount !== null && draft.amount !== undefined && draft.amount !== '';
      return {
        amount: hasAmount ? String(draft.amount) : '',
        billType: draft.billType === null || draft.billType === undefined
          ? BILL_TYPE.EXPENSE
          : Number(draft.billType),
        payChannelCode: draft.payChannelCode || '',
        payChannelName: draft.payChannelName || '',
        categoryCode: draft.categoryCode || '',
        categoryName: draft.categoryName || '',
        recordDate: draft.recordDate || format.todayStr(),
        remark: draft.remark || '',
        tags: Array.isArray(draft.tags) ? draft.tags.slice() : []
      };
    },

    buildMarks(draft) {
      const missing = draft.missingFields || [];
      const low = draft.confidence !== null &&
        draft.confidence !== undefined &&
        Number(draft.confidence) < 0.5;

      const marks = {};
      missing.forEach((field) => {
        marks[field] = 'missing';
      });
      if (low) {
        ['amount', 'billType', 'payChannelCode', 'categoryCode'].forEach((field) => {
          if (!marks[field]) {
            marks[field] = 'low';
          }
        });
      }

      const hasConfidence = draft.confidence !== null && draft.confidence !== undefined;
      return {
        marks,
        warn: low,
        confidenceText: hasConfidence ? Math.round(Number(draft.confidence) * 100) + '%' : '—'
      };
    },

    onFormChange(e) {
      this.setData({ form: e.detail });
    },

    onCancel() {
      this.triggerEvent('cancel');
    },

    onConfirm() {
      if (this.data.saving) {
        return;
      }
      const error = this.validate(this.data.form);
      if (error) {
        wx.showToast({ title: error, icon: 'none' });
        return;
      }
      this.triggerEvent('confirm', { draft: this.data.form });
    },

    /** 前端校验，仅做体验优化，不替代后端校验（§6.4） */
    validate(form) {
      const amountText = String(form.amount === undefined || form.amount === null ? '' : form.amount).trim();
      if (!amountText) {
        return '请输入正确的金额';
      }
      if (!/^\d+(\.\d{1,2})?$/.test(amountText)) {
        return '请输入正确的金额';
      }
      if (Number(amountText) <= 0) {
        return '请输入正确的金额';
      }
      if (!form.payChannelCode) {
        return '请选择支付渠道';
      }
      if (!form.categoryCode) {
        return '请选择分类';
      }
      if (!form.recordDate) {
        return '请选择正确的日期';
      }
      return '';
    },

    noop() {}
  }
});