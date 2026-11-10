/**
 * P-07 预算设置（V2，§4.7）
 * 数据来源：GET /api/budget（总预算 + 分类预算，含已用/剩余/占比）
 * 本页只做本地视觉预警，不发任何订阅消息推送（F-62 本轮不做）。
 */
const budgetApi = require('../../../api/budget');
const store = require('../../../store/index');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const { CODE } = require('../../../config/const');

/** 总预算无 warnRatio 字段，取默认 0.80（§4.7-4） */
const DEFAULT_WARN_RATIO = 0.8;
/** slider 激活色需为具体色值，收在 data 中避免在 wxml 写死 */
const BRAND_COLOR = '#07C160';

/**
 * 进度条三档（§4.7-3）：
 * ratio < warnRatio → normal；>= warnRatio → warn；> 1 → over
 */
function barLevel(ratio, warnRatio) {
  const r = Number(ratio) || 0;
  const w = Number(warnRatio);
  const threshold = isFinite(w) && w > 0 ? w : DEFAULT_WARN_RATIO;
  if (r > 1) {
    return 'over';
  }
  if (r >= threshold) {
    return 'warn';
  }
  return 'normal';
}

/** 进度条填充宽度（%），超支封顶 100 */
function barWidth(ratio) {
  const r = Number(ratio) || 0;
  if (r <= 0) {
    return 0;
  }
  return Math.min(r, 1) * 100;
}

Page({
  data: {
    month: '',
    loading: true,
    loaded: false,
    empty: false,
    dict: null,

    // 总预算视图
    totalBudgetText: '0.00',
    totalRemainText: '0.00',
    totalPercentText: '0%',
    totalPercent: 0,
    totalLevel: 'normal',
    totalOver: false,

    // 分类预算视图
    items: [],

    // 半屏弹窗
    modalVisible: false,
    modalMode: 'total', // total | category
    form: {
      amount: '',
      categoryCode: '',
      categoryName: '',
      warnPercent: 80
    },
    sliderActiveColor: BRAND_COLOR,
    saving: false
  },

  onLoad() {
    this.setData({ month: format.currentMonthStr() });
    this.loadDict();
  },

  onShow() {
    this.loadBudget();
  },

  onPullDownRefresh() {
    this.loadBudget().then(() => wx.stopPullDownRefresh());
  },

  /* ---------- 字典（分类选择器用，§6.5） ---------- */

  loadDict() {
    const cached = store.getDictCache();
    if (cached) {
      this.setData({ dict: cached });
    }
    store.loadDict()
      .then((dict) => this.setData({ dict }))
      .catch(() => {});
  },

  /* ---------- 预算数据 ---------- */

  loadBudget() {
    if (!this.data.loaded) {
      this.setData({ loading: true });
    }
    return budgetApi.get(this.data.month, { silent: true })
      .then((dto) => {
        this.setData(Object.assign({ loaded: true }, this.buildView(dto)));
      })
      .catch((err) => {
        this.setData({ loading: false });
        if (!this.data.loaded) {
          this.setData(Object.assign({ loaded: true }, this.buildView(null)));
          request.toast((err && err.message) || '加载失败，请稍后再试');
        }
      });
  },

  buildView(dto) {
    const totalBudget = Number(dto && dto.totalBudget) || 0;
    const totalRemain = Number(dto && dto.totalRemain) || 0;
    const totalRatio = Number(dto && dto.usedRatio) || 0;
    const rawItems = (dto && dto.items) || [];

    const items = rawItems.map((it) => {
      const ratio = Number(it.usedRatio) || 0;
      return {
        budgetId: it.budgetId,
        categoryCode: it.categoryCode,
        categoryName: it.categoryName || '未分类',
        amountText: format.formatMoney(it.amount),
        usedText: format.formatMoney(it.used),
        percentText: format.ratioToPercent(ratio, 0),
        percent: barWidth(ratio),
        level: barLevel(ratio, it.warnRatio),
        over: ratio > 1
      };
    });

    // 无任何预算：总预算为空/0 且分类预算为空（§4.7-1）
    const empty = !(totalBudget > 0) && rawItems.length === 0;

    return {
      loading: false,
      empty,
      items,
      totalBudgetText: format.formatMoney(totalBudget),
      totalRemainText: format.formatMoney(totalRemain),
      totalPercentText: format.ratioToPercent(totalRatio, 0),
      totalPercent: barWidth(totalRatio),
      totalLevel: barLevel(totalRatio, DEFAULT_WARN_RATIO),
      totalOver: totalRatio > 1
    };
  },

  onMonthChange(e) {
    this.setData({ month: e.detail || format.currentMonthStr() });
    this.loadBudget();
  },

  /* ---------- 弹窗 ---------- */

  openTotalModal() {
    this.setData({
      modalVisible: true,
      modalMode: 'total',
      form: { amount: '', categoryCode: '', categoryName: '', warnPercent: 80 }
    });
  },

  openCategoryModal() {
    this.setData({
      modalVisible: true,
      modalMode: 'category',
      form: { amount: '', categoryCode: '', categoryName: '', warnPercent: 80 }
    });
  },

  closeModal() {
    if (this.data.saving) {
      return;
    }
    this.setData({ modalVisible: false });
  },

  onAmountInput(e) {
    this.setData({ 'form.amount': e.detail.value });
  },

  onWarnChange(e) {
    this.setData({ 'form.warnPercent': e.detail.value });
  },

  onCategoryChange(e) {
    this.setData({
      'form.categoryCode': e.detail.code,
      'form.categoryName': e.detail.name
    });
  },

  onSave() {
    if (this.data.saving) {
      return;
    }
    const form = this.data.form;
    const amountText = String(form.amount === undefined || form.amount === null ? '' : form.amount).trim();
    if (!amountText || !/^\d+(\.\d{1,2})?$/.test(amountText) || Number(amountText) <= 0) {
      request.toast('请输入正确的金额');
      return;
    }
    if (this.data.modalMode === 'category' && !form.categoryCode) {
      request.toast('请选择分类');
      return;
    }

    const payload = {
      budgetMonth: this.data.month,
      // 总预算 categoryCode 为空串（接口约定）
      categoryCode: this.data.modalMode === 'category' ? form.categoryCode : '',
      amount: Number(amountText),
      warnRatio: Number(form.warnPercent) / 100
    };

    this.setData({ saving: true });
    budgetApi.save(payload)
      .then(() => {
        this.setData({ saving: false, modalVisible: false });
        wx.showToast({ title: '已保存', icon: 'success' });
        this.loadBudget();
      })
      .catch((err) => {
        this.setData({ saving: false });
        const code = err && err.code;
        // 400/429/500/网络异常已由 request 层统一提示，避免重复
        if (code !== CODE.PARAM_ERROR &&
            code !== CODE.SYSTEM_ERROR &&
            code !== CODE.TOO_MANY_REQUESTS &&
            code !== CODE.NETWORK_ERROR &&
            code !== CODE.WX_LOGIN_FAILED) {
          request.toast((err && err.message) || '保存失败，请稍后再试');
        }
      });
  },

  /* ---------- 删除分类预算（二次确认，§4.7-3） ---------- */

  onDeleteItem(e) {
    const budgetId = e.currentTarget.dataset.id;
    const name = e.currentTarget.dataset.name || '该分类';
    wx.showModal({
      title: '删除预算',
      content: '确定删除「' + name + '」的预算吗？',
      confirmText: '删除',
      confirmColor: '#E8454F',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        budgetApi.remove(budgetId)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            this.loadBudget();
          })
          .catch((err) => {
            const code = err && err.code;
            if (code !== CODE.PARAM_ERROR &&
                code !== CODE.SYSTEM_ERROR &&
                code !== CODE.TOO_MANY_REQUESTS &&
                code !== CODE.NETWORK_ERROR &&
                code !== CODE.WX_LOGIN_FAILED) {
              request.toast((err && err.message) || '删除失败，请稍后再试');
            }
          });
      }
    });
  },

  noop() {}
});