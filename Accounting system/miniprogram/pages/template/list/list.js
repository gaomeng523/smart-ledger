/**
 * P-06 模板管理页（V2，§4.6）
 * 列表一键记账（不经过 AI）+ 撤销、新建 / 编辑半屏弹窗（复用 bill-form）、删除二次确认。
 */
const templateApi = require('../../../api/template');
const billApi = require('../../../api/bill');
const store = require('../../../store/index');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const { CODE, BILL_TYPE } = require('../../../config/const');

/** 撤销条展示时长（ms） */
const UNDO_DURATION = 3000;

/** request 层已自动 toast 的错误码，页面不再重复提示 */
const AUTO_TOASTED = [
  CODE.PARAM_ERROR,
  CODE.TOO_MANY_REQUESTS,
  CODE.SYSTEM_ERROR,
  CODE.UNAUTHORIZED,
  CODE.WX_LOGIN_FAILED,
  CODE.NETWORK_ERROR
];

function toastBizError(err, fallback) {
  const code = err && err.code;
  if (AUTO_TOASTED.indexOf(code) < 0) {
    request.toast((err && err.message) || fallback);
  }
}

Page({
  data: {
    // 列表
    templates: [],
    firstLoading: true,
    empty: false,
    // 当前正在「记一笔」的模板（防重复点击）
    usingId: '',
    // 撤销条
    showUndo: false,
    undoBillId: '',
    // 字典
    dict: null,
    // 新建 / 编辑弹窗
    showPanel: false,
    editing: false,
    editingId: '',
    formName: '',
    formValue: {},
    formMarks: {},
    saving: false
  },

  onLoad() {
    this.loadDict();
    this.loadList();
  },

  onUnload() {
    if (this._undoTimer) {
      clearTimeout(this._undoTimer);
      this._undoTimer = null;
    }
  },

  onPullDownRefresh() {
    this.loadList(true, () => wx.stopPullDownRefresh());
  },

  /* ---------- 数据加载 ---------- */

  loadDict() {
    const cached = store.getDictCache();
    if (cached) {
      this.setData({ dict: cached });
    }
    store.loadDict()
      .then((dict) => this.setData({ dict }))
      .catch(() => {});
  },

  /**
   * 拉取模板列表
   * @param {boolean} [refresh] 静默刷新（不展示首屏骨架，用于操作后回填）
   * @param {Function} [callback]
   */
  loadList(refresh, callback) {
    if (!refresh) {
      this.setData({ firstLoading: true });
    }
    return templateApi.list({ silent: true })
      .then((list) => {
        const templates = list || [];
        this.setData({
          templates,
          firstLoading: false,
          empty: templates.length === 0
        });
        if (callback) callback();
      })
      .catch(() => {
        this.setData({
          firstLoading: false,
          empty: this.data.templates.length === 0
        });
        request.toast('加载失败，请下拉/稍后重试');
        if (callback) callback();
      });
  },

  /* ---------- 一键记账 / 撤销 ---------- */

  onUse(e) {
    const template = e.detail && e.detail.template;
    if (!template || this.data.usingId) {
      return;
    }
    const templateId = template.templateId;
    this.setData({ usingId: templateId });

    templateApi.use(templateId)
      .then((res) => {
        this.setData({ usingId: '' });
        wx.showToast({ title: '已记一笔', icon: 'success' });
        this.showUndoBar(res && res.billId);
        this.loadList(true);
      })
      .catch((err) => {
        this.setData({ usingId: '' });
        toastBizError(err, '记账失败，请稍后再试');
      });
  },

  showUndoBar(billId) {
    if (!billId) {
      return;
    }
    if (this._undoTimer) {
      clearTimeout(this._undoTimer);
    }
    this.setData({ showUndo: true, undoBillId: billId });
    this._undoTimer = setTimeout(() => {
      this._undoTimer = null;
      this.setData({ showUndo: false, undoBillId: '' });
    }, UNDO_DURATION);
  },

  onUndo() {
    const billId = this.data.undoBillId;
    if (this._undoTimer) {
      clearTimeout(this._undoTimer);
      this._undoTimer = null;
    }
    this.setData({ showUndo: false, undoBillId: '' });
    if (!billId) {
      return;
    }
    billApi.remove(billId)
      .then(() => {
        wx.showToast({ title: '已撤销', icon: 'success' });
        this.loadList(true);
      })
      .catch((err) => toastBizError(err, '撤销失败，请稍后再试'));
  },

  /* ---------- 删除 ---------- */

  onDelete(e) {
    const template = e.detail && e.detail.template;
    if (!template) {
      return;
    }
    wx.showModal({
      title: '删除模板',
      content: '删除后不可恢复，确定删除「' + (template.title || '') + '」？',
      confirmText: '删除',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          this.doRemove(template.templateId);
        }
      }
    });
  },

  doRemove(templateId) {
    templateApi.remove(templateId)
      .then(() => {
        wx.showToast({ title: '已删除', icon: 'success' });
        this.loadList(true);
      })
      .catch((err) => toastBizError(err, '删除失败，请稍后再试'));
  },

  /* ---------- 新建 / 编辑弹窗 ---------- */

  emptyFormValue() {
    return {
      amount: '',
      billType: BILL_TYPE.EXPENSE,
      payChannelCode: '',
      payChannelName: '',
      categoryCode: '',
      categoryName: '',
      recordDate: format.todayStr(),
      remark: ''
    };
  },

  onNew() {
    this.setData({
      showPanel: true,
      editing: false,
      editingId: '',
      formName: '',
      formValue: this.emptyFormValue(),
      formMarks: {},
      saving: false
    });
  },

  onEdit(e) {
    const template = e.detail && e.detail.template;
    if (!template) {
      return;
    }
    this.setData({
      showPanel: true,
      editing: true,
      editingId: template.templateId,
      formName: template.title || '',
      formValue: {
        amount: template.amount === null || template.amount === undefined
          ? ''
          : String(template.amount),
        billType: template.billType === null || template.billType === undefined
          ? BILL_TYPE.EXPENSE
          : Number(template.billType),
        payChannelCode: template.payChannelCode || '',
        payChannelName: template.payChannelName || '',
        categoryCode: template.categoryCode || '',
        categoryName: template.categoryName || '',
        recordDate: format.todayStr(),
        remark: template.remark || ''
      },
      formMarks: {},
      saving: false
    });
  },

  onPanelClose() {
    if (this.data.saving) {
      return;
    }
    this.setData({ showPanel: false });
  },

  onNameInput(e) {
    this.setData({ formName: e.detail.value });
  },

  onFormChange(e) {
    this.setData({ formValue: e.detail });
  },

  onSave() {
    if (this.data.saving) {
      return;
    }
    const name = (this.data.formName || '').trim();
    if (!name) {
      request.toast('请输入模板名称');
      return;
    }
    if (name.length > 12) {
      request.toast('模板名称不超过 12 字');
      return;
    }
    const form = this.data.formValue || {};
    const error = this.validate(form);
    if (error) {
      request.toast(error);
      return;
    }

    const payload = {
      title: name,
      billType: Number(form.billType),
      amount: Number(form.amount),
      payChannelCode: form.payChannelCode,
      categoryCode: form.categoryCode,
      remark: form.remark || ''
    };
    const editing = this.data.editing;
    if (editing) {
      payload.templateId = this.data.editingId;
    }

    this.setData({ saving: true });
    const action = editing ? templateApi.update(payload) : templateApi.create(payload);
    action
      .then(() => {
        this.setData({ saving: false, showPanel: false });
        wx.showToast({ title: editing ? '已保存' : '已创建', icon: 'success' });
        this.loadList(true);
      })
      .catch((err) => {
        this.setData({ saving: false });
        toastBizError(err, '保存失败，请稍后再试');
      });
  },

  /** 前端校验，仅做体验优化（§6.4） */
  validate(form) {
    const amountText = String(
      form.amount === undefined || form.amount === null ? '' : form.amount
    ).trim();
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
  },

  noop() {}
});