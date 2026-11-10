/**
 * 心愿储蓄（V3 阶段二）
 * 数据来源：GET /api/savings（含已存/还差/进度/状态）
 * 业务：新建/编辑心愿、存一笔、取出、删除。
 */
const savingsApi = require('../../../api/savings');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const { CODE } = require('../../../config/const');

/** 可选心愿图标（与 H5 一致） */
const ICONS = ['🎯', '✈️', '📷', '💻', '🏠', '🚗', '💍', '🎓', '🎁', '🐱'];

/** 存入 / 取出快捷金额 */
const QUICK_ADD = [100, 500, 1000, 2000];
const QUICK_TAKE = [100, 500, 1000];

Page({
  data: {
    loading: true,
    loaded: false,
    items: [],

    // 汇总
    totalSavedText: '0.00',
    count: 0,
    achieved: 0,

    icons: ICONS,

    // 新建 / 编辑弹层
    sheetVisible: false,
    sheetMode: 'new', // new | edit
    form: {
      title: '',
      targetAmount: '',
      dueDate: ''
    },
    selectedIcon: ICONS[0],
    saving: false,

    // 存 / 取金额弹层
    amountVisible: false,
    amountWithdraw: false,
    amountTitle: '',
    amountValue: '',
    quickAmounts: [],
    amountSaving: false
  },

  /** 原始心愿列表（编辑 / 存取用原始数值） */
  rawList: [],
  /** 当前编辑的心愿（编辑模式 / 存取目标） */
  editing: null,
  amountGoal: null,

  onLoad() {
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

  /* ---------- 数据加载 ---------- */

  load() {
    if (!this.data.loaded) {
      this.setData({ loading: true });
    }
    return savingsApi.list({ silent: true })
      .then((list) => {
        this.rawList = list || [];
        this.setData(Object.assign({ loaded: true }, this.buildView(list)));
      })
      .catch((err) => {
        this.setData({ loading: false, loaded: true });
        request.toast((err && err.message) || '加载失败，请稍后再试');
      });
  },

  buildView(list) {
    const raw = list || [];
    let totalSaved = 0;
    let achieved = 0;

    const items = raw.map((g) => {
      const saved = Number(g.savedAmount) || 0;
      const target = Number(g.targetAmount) || 0;
      const done = Number(g.status) === 1;
      totalSaved += saved;
      if (done) {
        achieved += 1;
      }
      const pct = Math.max(0, Math.min(100, Number(g.progressPercent) || 0));
      return {
        id: g.id,
        icon: g.icon || '🎯',
        title: g.title,
        done,
        abandoned: Number(g.status) === 2,
        statusText: g.statusText || '',
        targetText: format.formatMoney(target),
        savedText: format.formatMoney(saved),
        percent: pct,
        subText: this.subText(g, done),
        canWithdraw: saved > 0
      };
    });

    return {
      loading: false,
      items,
      count: raw.length,
      achieved,
      totalSavedText: format.formatMoney(totalSaved)
    };
  },

  subText(g, done) {
    const parts = ['目标 ¥' + format.formatMoney(g.targetAmount)];
    if (done) {
      parts.push('已达成 🎉');
    } else {
      parts.push('还差 ¥' + format.formatMoney(g.remainAmount));
      if (g.dueDate && g.daysLeft !== null && g.daysLeft !== undefined) {
        const d = Number(g.daysLeft);
        if (d < 0) {
          parts.push('已逾期 ' + (-d) + ' 天');
        } else if (d === 0) {
          parts.push('今天到期');
        } else {
          parts.push('剩 ' + d + ' 天');
        }
      }
    }
    return parts.join(' · ');
  },

  /* ---------- 新建 / 编辑弹层 ---------- */

  openNew() {
    this.editing = null;
    this.setData({
      sheetVisible: true,
      sheetMode: 'new',
      selectedIcon: ICONS[0],
      form: { title: '', targetAmount: '', dueDate: '' }
    });
  },

  openEdit(e) {
    const goal = this.find(e.currentTarget.dataset.id);
    if (!goal) {
      return;
    }
    this.editing = goal;
    this.setData({
      sheetVisible: true,
      sheetMode: 'edit',
      selectedIcon: goal.icon || ICONS[0],
      form: {
        title: goal.title || '',
        targetAmount: goal.targetAmount === null || goal.targetAmount === undefined
          ? '' : String(goal.targetAmount),
        dueDate: goal.dueDate || ''
      }
    });
  },

  closeSheet() {
    if (this.data.saving) {
      return;
    }
    this.setData({ sheetVisible: false });
  },

  onIconSelect(e) {
    this.setData({ selectedIcon: e.currentTarget.dataset.icon });
  },

  onTitleInput(e) {
    this.setData({ 'form.title': e.detail.value });
  },

  onTargetInput(e) {
    this.setData({ 'form.targetAmount': e.detail.value });
  },

  onDueChange(e) {
    this.setData({ 'form.dueDate': e.detail.value });
  },

  onSheetSave() {
    if (this.data.saving) {
      return;
    }
    const form = this.data.form;
    const title = String(form.title || '').trim();
    const targetText = String(form.targetAmount === undefined || form.targetAmount === null
      ? '' : form.targetAmount).trim();

    if (!title) {
      request.toast('请填写心愿名称');
      return;
    }
    if (title.length > 20) {
      request.toast('心愿名称最多 20 个字');
      return;
    }
    if (!/^\d+(\.\d{1,2})?$/.test(targetText) || Number(targetText) <= 0) {
      request.toast('请输入正确的目标金额');
      return;
    }

    const payload = {
      title,
      icon: this.data.selectedIcon,
      targetAmount: Number(targetText),
      dueDate: form.dueDate || null
    };

    let req;
    if (this.editing) {
      payload.id = this.editing.id;
      req = savingsApi.update(payload);
    } else {
      req = savingsApi.create(payload);
    }

    this.setData({ saving: true });
    req
      .then(() => {
        this.setData({ saving: false, sheetVisible: false });
        wx.showToast({ title: this.editing ? '已保存' : '心愿已创建', icon: 'success' });
        this.load();
      })
      .catch((err) => {
        this.setData({ saving: false });
        if (this.shouldToast(err)) {
          request.toast((err && err.message) || '保存失败，请稍后再试');
        }
      });
  },

  /* ---------- 存 / 取弹层 ---------- */

  openAdd(e) {
    this.openAmount(this.find(e.currentTarget.dataset.id), false);
  },

  openTake(e) {
    this.openAmount(this.find(e.currentTarget.dataset.id), true);
  },

  openAmount(goal, withdraw) {
    if (!goal) {
      return;
    }
    this.amountGoal = goal;
    const quick = withdraw
      ? QUICK_TAKE.concat([Math.floor(Number(goal.savedAmount) || 0)])
      : QUICK_ADD.slice();
    this.setData({
      amountVisible: true,
      amountWithdraw: !!withdraw,
      amountTitle: (withdraw ? '取出 · ' : '存一笔 · ') + goal.title,
      amountValue: '',
      quickAmounts: quick.filter((v) => v > 0)
    });
  },

  closeAmount() {
    if (this.data.amountSaving) {
      return;
    }
    this.setData({ amountVisible: false });
  },

  onAmountInput(e) {
    this.setData({ amountValue: e.detail.value });
  },

  onQuickTap(e) {
    this.setData({ amountValue: String(e.currentTarget.dataset.value) });
  },

  onAmountSave() {
    if (this.data.amountSaving || !this.amountGoal) {
      return;
    }
    const amountText = String(this.data.amountValue || '').trim();
    if (!/^\d+(\.\d{1,2})?$/.test(amountText) || Number(amountText) <= 0) {
      request.toast('请输入正确的金额');
      return;
    }
    const withdraw = this.data.amountWithdraw;
    this.setData({ amountSaving: true });

    savingsApi.changeAmount({
      id: this.amountGoal.id,
      amount: Number(amountText),
      withdraw
    })
      .then((res) => {
        this.setData({ amountSaving: false, amountVisible: false });
        if (res && Number(res.status) === 1) {
          wx.showToast({ title: '🎉 心愿达成啦', icon: 'none' });
        } else {
          wx.showToast({ title: withdraw ? '已取出' : '已存入', icon: 'success' });
        }
        this.load();
      })
      .catch((err) => {
        this.setData({ amountSaving: false });
        if (this.shouldToast(err)) {
          request.toast((err && err.message) || '操作失败，请稍后再试');
        }
      });
  },

  /* ---------- 删除 ---------- */

  onDelete(e) {
    const goal = this.find(e.currentTarget.dataset.id);
    const self = this;
    wx.showModal({
      title: '删除心愿',
      content: goal ? '「' + goal.title + '」将被删除，已存金额仅作记录、不影响账单' : '删除后不可恢复',
      confirmText: '删除',
      confirmColor: '#E8454F',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        savingsApi.remove(goal.id)
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

  find(id) {
    const target = String(id);
    return this.rawList.filter((it) => String(it.id) === target)[0] || null;
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