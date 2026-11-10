/**
 * 订阅管理（V3 阶段一）
 * 数据来源：GET /api/subscription（我的订阅）+ GET /api/subscription/suggest（候选识别）
 * 业务：确认候选 / 手动添加、编辑、暂停恢复、删除。
 */
const subscriptionApi = require('../../../api/subscription');
const store = require('../../../store/index');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const { CODE } = require('../../../config/const');

/** 忽略候选的本地存储 Key */
const SKIP_KEY = 'sub_skipped';

/** 周期选项 */
const CYCLES = [
  { value: 30, label: '月付' },
  { value: 90, label: '季付' },
  { value: 365, label: '年付' }
];

Page({
  data: {
    loading: true,
    loaded: false,
    dict: null,

    items: [],
    candidates: [],

    cycles: CYCLES,

    // 新建 / 确认 / 编辑弹层
    sheetVisible: false,
    sheetMode: 'new', // new | confirm | edit
    form: {
      title: '',
      avgAmount: '',
      cycleDays: 30,
      nextDate: '',
      status: 1,
      categoryCode: '',
      categoryName: '',
      payChannelCode: '',
      payChannelName: ''
    },
    editing: false,
    saving: false
  },

  /** 原始订阅列表（编辑 / 暂停用原始数值） */
  rawList: [],
  /** 原始候选列表 */
  rawCandidates: [],
  /** 编辑中的订阅 / 确认中的候选 */
  current: null,
  confirmCandidate: null,

  onLoad() {
    this.loadDict();
    this.load();
  },

  onShow() {
    if (this.data.loaded) {
      this.load();
    }
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
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

  /* ---------- 数据加载 ---------- */

  load() {
    if (!this.data.loaded) {
      this.setData({ loading: true });
    }
    return Promise.all([
      subscriptionApi.list({ silent: true }).catch(() => []),
      subscriptionApi.suggest({ silent: true }).catch(() => [])
    ])
      .then((res) => {
        this.rawList = res[0] || [];
        this.rawCandidates = (res[1] || []).filter((c) => !this.isSkipped(c));
        this.setData(Object.assign({ loaded: true, loading: false }, this.buildList(), this.buildCandidates()));
      })
      .catch((err) => {
        this.setData({ loading: false, loaded: true });
        request.toast((err && err.message) || '加载失败，请稍后再试');
      });
  },

  buildList() {
    const items = (this.rawList || []).map((s) => {
      const due = this.dueText(s);
      const paused = Number(s.status) === 0;
      return {
        id: s.id,
        title: s.title,
        amountText: format.formatMoney(s.avgAmount),
        cycleText: s.cycleText || '',
        categoryName: s.categoryName || '未分类',
        payChannelName: s.payChannelName || '未选渠道',
        dueText: due.text,
        dueCls: due.cls,
        paused
      };
    });
    return { items };
  },

  buildCandidates() {
    const candidates = (this.rawCandidates || []).map((c, i) => ({
      index: i,
      title: c.title,
      amountText: format.formatMoney(c.avgAmount),
      cycleText: c.cycleText || '',
      categoryName: c.categoryName || '未分类',
      payChannelName: c.payChannelName || '未选渠道',
      occurrences: c.occurrences || 0
    }));
    return { candidates };
  },

  /** 距下次扣费文案（0=今天，负数=逾期） */
  dueText(s) {
    if (Number(s.status) === 0) {
      return { cls: 'is-paused', text: '已暂停' };
    }
    const d = Number(s.daysToNext);
    if (!isFinite(d)) {
      return { cls: 'is-normal', text: '待扣费' };
    }
    if (d < 0) {
      return { cls: 'is-overdue', text: '已逾期 ' + (-d) + ' 天' };
    }
    if (d === 0) {
      return { cls: 'is-today', text: '今天扣费' };
    }
    if (d <= 3) {
      return { cls: 'is-soon', text: d + ' 天后扣费' };
    }
    const p = String(s.nextDate || '').split('-');
    return { cls: 'is-normal', text: Number(p[1]) + '月' + Number(p[2]) + '日扣费' };
  },

  /* ---------- 候选：忽略 / 确认 ---------- */

  candidateKey(c) {
    return [c.title, c.categoryCode, c.payChannelCode, format.formatMoney(c.avgAmount)].join('|');
  },

  isSkipped(c) {
    const raw = wx.getStorageSync(SKIP_KEY) || '';
    return raw.split(';;').indexOf(this.candidateKey(c)) >= 0;
  },

  onSkipCandidate(e) {
    const c = this.rawCandidates[e.currentTarget.dataset.index];
    if (!c) {
      return;
    }
    const raw = wx.getStorageSync(SKIP_KEY) || '';
    const arr = raw ? raw.split(';;') : [];
    arr.push(this.candidateKey(c));
    wx.setStorageSync(SKIP_KEY, arr.slice(-50).join(';;'));
    this.rawCandidates.splice(e.currentTarget.dataset.index, 1);
    this.setData(this.buildCandidates());
  },

  onConfirmCandidate(e) {
    const c = this.rawCandidates[e.currentTarget.dataset.index];
    if (!c) {
      return;
    }
    this.current = null;
    this.confirmCandidate = c;
    this.setData({
      sheetVisible: true,
      sheetMode: 'confirm',
      editing: false,
      form: {
        title: c.title || '',
        avgAmount: c.avgAmount === null || c.avgAmount === undefined ? '' : String(c.avgAmount),
        cycleDays: this.normalizeCycle(c.cycleDays),
        nextDate: c.nextDate || this.shiftToday(30),
        status: 1,
        categoryCode: c.categoryCode || '',
        categoryName: c.categoryName || '',
        payChannelCode: c.payChannelCode || '',
        payChannelName: c.payChannelName || ''
      }
    });
  },

  /* ---------- 手动新建 ---------- */

  openNew() {
    this.current = null;
    this.confirmCandidate = null;
    this.setData({
      sheetVisible: true,
      sheetMode: 'new',
      editing: false,
      form: {
        title: '',
        avgAmount: '',
        cycleDays: 30,
        nextDate: this.shiftToday(30),
        status: 1,
        categoryCode: '',
        categoryName: '',
        payChannelCode: '',
        payChannelName: ''
      }
    });
  },

  /* ---------- 编辑 ---------- */

  openEdit(e) {
    const s = this.find(e.currentTarget.dataset.id);
    if (!s) {
      return;
    }
    this.current = s;
    this.confirmCandidate = null;
    this.setData({
      sheetVisible: true,
      sheetMode: 'edit',
      editing: true,
      form: {
        title: s.title || '',
        avgAmount: s.avgAmount === null || s.avgAmount === undefined ? '' : String(s.avgAmount),
        cycleDays: this.normalizeCycle(s.cycleDays),
        nextDate: s.nextDate || this.shiftToday(30),
        status: Number(s.status) === 0 ? 0 : 1,
        categoryCode: s.categoryCode || '',
        categoryName: s.categoryName || '',
        payChannelCode: s.payChannelCode || '',
        payChannelName: s.payChannelName || ''
      }
    });
  },

  closeSheet() {
    if (this.data.saving) {
      return;
    }
    this.setData({ sheetVisible: false });
  },

  /* ---------- 表单交互 ---------- */

  onTitleInput(e) {
    this.setData({ 'form.title': e.detail.value });
  },

  onAmountInput(e) {
    this.setData({ 'form.avgAmount': e.detail.value });
  },

  onCycleSelect(e) {
    this.setData({ 'form.cycleDays': Number(e.currentTarget.dataset.value) });
  },

  onNextDateChange(e) {
    this.setData({ 'form.nextDate': e.detail.value });
  },

  onStatusSelect(e) {
    this.setData({ 'form.status': Number(e.currentTarget.dataset.value) });
  },

  onCategoryChange(e) {
    this.setData({
      'form.categoryCode': e.detail.code,
      'form.categoryName': e.detail.name
    });
  },

  onChannelChange(e) {
    this.setData({
      'form.payChannelCode': e.detail.code,
      'form.payChannelName': e.detail.name
    });
  },

  onSheetSave() {
    if (this.data.saving) {
      return;
    }
    const form = this.data.form;
    const title = String(form.title || '').trim();
    const amountText = String(form.avgAmount === undefined || form.avgAmount === null
      ? '' : form.avgAmount).trim();

    if (!title) {
      request.toast('请填写订阅名称');
      return;
    }
    if (!/^\d+(\.\d{1,2})?$/.test(amountText) || Number(amountText) <= 0) {
      request.toast('请输入正确的金额');
      return;
    }
    if (!form.nextDate) {
      request.toast('请选择下次扣费日');
      return;
    }

    const payload = {
      title,
      avgAmount: Number(amountText),
      cycleDays: form.cycleDays,
      nextDate: form.nextDate
    };

    let req;
    if (this.data.editing && this.current) {
      payload.id = this.current.id;
      payload.status = form.status;
      req = subscriptionApi.update(payload);
    } else {
      if (this.confirmCandidate) {
        payload.categoryCode = this.confirmCandidate.categoryCode;
        payload.payChannelCode = this.confirmCandidate.payChannelCode;
      } else {
        payload.categoryCode = form.categoryCode;
        payload.payChannelCode = form.payChannelCode;
      }
      if (!payload.categoryCode) {
        request.toast('请选择分类');
        return;
      }
      if (!payload.payChannelCode) {
        request.toast('请选择支付渠道');
        return;
      }
      req = subscriptionApi.create(payload);
    }

    this.setData({ saving: true });
    req
      .then(() => {
        this.setData({ saving: false, sheetVisible: false });
        wx.showToast({ title: this.data.editing ? '已保存' : '已加入订阅', icon: 'success' });
        this.load();
      })
      .catch((err) => {
        this.setData({ saving: false });
        if (this.shouldToast(err)) {
          request.toast((err && err.message) || '保存失败，请稍后再试');
        }
      });
  },

  /* ---------- 暂停 / 恢复 ---------- */

  onToggle(e) {
    const s = this.find(e.currentTarget.dataset.id);
    if (!s) {
      return;
    }
    const nextStatus = Number(s.status) === 1 ? 0 : 1;
    subscriptionApi.update({
      id: s.id,
      title: s.title,
      avgAmount: s.avgAmount,
      cycleDays: s.cycleDays,
      nextDate: s.nextDate,
      status: nextStatus
    })
      .then(() => {
        wx.showToast({ title: nextStatus === 1 ? '已恢复提醒' : '已暂停', icon: 'success' });
        this.load();
      })
      .catch((err) => {
        if (this.shouldToast(err)) {
          request.toast((err && err.message) || '操作失败，请稍后再试');
        }
      });
  },

  /* ---------- 删除 ---------- */

  onDelete(e) {
    const s = this.find(e.currentTarget.dataset.id);
    const self = this;
    wx.showModal({
      title: '删除订阅',
      content: s ? '「' + s.title + '」的续费提醒将被删除，历史账单不受影响' : '删除后不可恢复',
      confirmText: '删除',
      confirmColor: '#E8454F',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        subscriptionApi.remove(s.id)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            self.load();
          })
          .catch((err) => {
            if (self.shouldToast(err)) {
              request.toast((err && err.message) || '删除失败，请稍后再试');
            }
          });
      }
    });
  },

  /* ---------- 工具 ---------- */

  find(id) {
    const target = String(id);
    return this.rawList.filter((s) => String(s.id) === target)[0] || null;
  },

  normalizeCycle(value) {
    const n = Number(value);
    return n === 90 || n === 365 ? n : 30;
  },

  pad2(n) {
    return n < 10 ? '0' + n : '' + n;
  },

  shiftToday(days) {
    const d = new Date();
    d.setDate(d.getDate() + (days || 0));
    return d.getFullYear() + '-' + this.pad2(d.getMonth() + 1) + '-' + this.pad2(d.getDate());
  },

  /** 400/500/429/网络/登录 已由 request 层统一提示，避免重复 */
  shouldToast(err) {
    const code = err && err.code;
    return code !== CODE.PARAM_ERROR &&
      code !== CODE.SYSTEM_ERROR &&
      code !== CODE.TOO_MANY_REQUESTS &&
      code !== CODE.NETWORK_ERROR &&
      code !== CODE.WX_LOGIN_FAILED;
  },

  noop() {}
});