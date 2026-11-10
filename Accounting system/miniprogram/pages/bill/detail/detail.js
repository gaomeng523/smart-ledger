/**
 * P-03 账单详情 / 编辑页（§4.3）
 * 本页不出现吉祥物（信息密集页，装饰让位给字段与金额）。
 */
const billApi = require('../../../api/bill');
const store = require('../../../store/index');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const { BASE_URL } = require('../../../config/env');
const { CODE, BILL_TYPE, SOURCE } = require('../../../config/const');

const SOURCE_TEXT = {
  [SOURCE.TEXT]: '文本',
  [SOURCE.VOICE]: '语音',
  [SOURCE.TEMPLATE]: '模板',
  [SOURCE.IMPORT]: '导入',
  [SOURCE.OCR]: 'OCR',
  [SOURCE.MANUAL]: '手动'
};

Page({
  data: {
    loading: true,
    dict: null,
    bill: null,
    value: {},
    amountText: '0.00',
    isIncome: false,
    confidenceText: '—',
    sourceText: '手动',
    createTimeText: '',
    /** 账单图片（拍照记账，V3 阶段二）完整地址 */
    imageUrl: '',
    saving: false,
    /** 历史标签候选（V2 §4.10 ④） */
    tagSuggestions: []
  },

  billId: '',
  /** 原始快照，用于只提交发生变化的字段 */
  original: null,

  onLoad(options) {
    this.billId = (options && options.billId) || '';
    this.loadDict();
    this.loadTagSuggest();
    this.loadDetail();
  },

  loadTagSuggest() {
    billApi.tagSuggest({ silent: true })
      .then((res) => this.setData({ tagSuggestions: (res && res.tags) || [] }))
      .catch(() => {});
  },

  loadDict() {
    const cached = store.getDictCache();
    if (cached) {
      this.setData({ dict: cached });
    }
    store.loadDict()
      .then((dict) => this.setData({ dict }))
      .catch(() => {});
  },

  loadDetail() {
    billApi.detail(this.billId)
      .then((bill) => this.applyBill(bill))
      .catch((err) => {
        const code = err && err.code;
        if (code === CODE.FORBIDDEN) {
          request.toast('无权访问该账单');
        } else if (code === CODE.NOT_FOUND) {
          request.toast('账单不存在或已删除');
        } else if (code !== CODE.NETWORK_ERROR) {
          request.toast((err && err.message) || '加载失败');
        }
        // 越权/不存在：提示并自动返回上一页（§4.3）
        setTimeout(() => wx.navigateBack({ delta: 1 }), 900);
        this.setData({ loading: false });
      });
  },

  applyBill(bill) {
    const value = {
      amount: bill.amount === null || bill.amount === undefined ? '' : String(bill.amount),
      billType: Number(bill.billType),
      payChannelCode: bill.payChannelCode || '',
      payChannelName: bill.payChannelName || '',
      categoryCode: bill.categoryCode || '',
      categoryName: bill.categoryName || '',
      recordDate: bill.recordDate || '',
      remark: bill.remark || '',
      tags: Array.isArray(bill.tags) ? bill.tags.slice() : []
    };
    this.original = value;

    this.setData({
      loading: false,
      bill,
      value,
      amountText: format.formatMoney(bill.amount),
      isIncome: Number(bill.billType) === BILL_TYPE.INCOME,
      confidenceText: bill.confidence === null || bill.confidence === undefined
        ? '—'
        : Math.round(Number(bill.confidence) * 100) + '%',
      sourceText: SOURCE_TEXT[bill.source] || '手动',
      createTimeText: bill.createTime || '',
      imageUrl: bill.imageUrl ? BASE_URL + bill.imageUrl : ''
    });
  },

  /** 查看大图（拍照记账） */
  onPreviewImage() {
    if (!this.data.imageUrl) {
      return;
    }
    wx.previewImage({ urls: [this.data.imageUrl] });
  },

  onFormChange(e) {
    const value = e.detail;
    this.setData({
      value,
      amountText: format.formatMoney(value.amount),
      isIncome: Number(value.billType) === BILL_TYPE.INCOME
    });
  },

  /* ---------- 保存（仅提交变化字段，§4.3） ---------- */

  onSave() {
    if (this.data.saving) {
      return;
    }
    const value = this.data.value;
    const error = this.validate(value);
    if (error) {
      request.toast(error);
      return;
    }

    const patch = this.buildPatch(value);
    if (!patch) {
      request.toast('没有需要保存的修改');
      return;
    }

    this.setData({ saving: true });
    billApi.update(patch)
      .then(() => {
        this.setData({ saving: false });
        this.original = Object.assign({}, value);
        wx.showToast({ title: '已保存', icon: 'success' });
        setTimeout(() => wx.navigateBack({ delta: 1 }), 800);
      })
      .catch((err) => {
        this.setData({ saving: false });
        const code = err && err.code;
        if (code === CODE.FORBIDDEN || code === CODE.NOT_FOUND) {
          request.toast(err.message);
          setTimeout(() => wx.navigateBack({ delta: 1 }), 900);
        } else if (code !== CODE.NETWORK_ERROR && code !== CODE.WX_LOGIN_FAILED) {
          request.toast((err && err.message) || '保存失败，请稍后再试');
        }
      });
  },

  buildPatch(value) {
    const origin = this.original || {};
    const patch = { billId: Number(this.billId) };
    let changed = false;

    if (String(value.amount) !== String(origin.amount)) {
      patch.amount = Number(value.amount);
      changed = true;
    }
    if (Number(value.billType) !== Number(origin.billType)) {
      patch.billType = Number(value.billType);
      changed = true;
    }
    if ((value.payChannelCode || '') !== (origin.payChannelCode || '')) {
      patch.payChannelCode = value.payChannelCode;
      changed = true;
    }
    if ((value.categoryCode || '') !== (origin.categoryCode || '')) {
      patch.categoryCode = value.categoryCode;
      changed = true;
    }
    if ((value.recordDate || '') !== (origin.recordDate || '')) {
      patch.recordDate = value.recordDate;
      changed = true;
    }
    if ((value.remark || '') !== (origin.remark || '')) {
      patch.remark = value.remark || '';
      changed = true;
    }
    const valueTags = value.tags || [];
    const originTags = origin.tags || [];
    if (valueTags.join(',') !== originTags.join(',')) {
      patch.tags = valueTags;
      changed = true;
    }

    return changed ? patch : null;
  },

  /* ---------- 删除（二次确认，§4.3） ---------- */

  onDelete() {
    wx.showModal({
      title: '删除账单',
      content: '删除后不可恢复，确定删除？',
      confirmText: '删除',
      confirmColor: '#E8454F',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        billApi.remove(this.billId)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => wx.navigateBack({ delta: 1 }), 800);
          })
          .catch((err) => {
            const code = err && err.code;
            if (code === CODE.FORBIDDEN || code === CODE.NOT_FOUND) {
              request.toast(err.message);
              setTimeout(() => wx.navigateBack({ delta: 1 }), 900);
            } else if (code !== CODE.NETWORK_ERROR && code !== CODE.WX_LOGIN_FAILED) {
              request.toast((err && err.message) || '删除失败，请稍后再试');
            }
          });
      }
    });
  },

  /** 前端校验，仅做体验优化（§6.4） */
  validate(form) {
    const amountText = String(form.amount === undefined || form.amount === null ? '' : form.amount).trim();
    if (!amountText || !/^\d+(\.\d{1,2})?$/.test(amountText) || Number(amountText) <= 0) {
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
  }
});