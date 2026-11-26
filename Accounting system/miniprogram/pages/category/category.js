/**
 * 分类管理（设计稿 06 屏）
 * 一级分类 + 二级明细（按需下钻）+ 学习规则入口。
 */
const statApi = require('../../api/stat');
const aliasApi = require('../../api/alias');
const store = require('../../store/index');
const request = require('../../utils/request');
const format = require('../../utils/format');
const categoryIcon = require('../../utils/category-icon');
const palette = require('../../utils/palette');
const { splitPercent } = require('../../utils/percent');
const { BILL_TYPE } = require('../../config/const');

Page({
  data: {
    month: '',
    monthText: '',
    totalText: '0.00',
    billCount: 0,
    usedCount: 0,
    parents: [],
    unusedParents: [],
    unusedCount: 0,
    showUnused: false,
    expanded: '',
    children: [],
    ruleCount: 0,
    loading: true
  },

  tree: [],

  onLoad() {
    const month = format.currentMonthStr();
    this.setData({
      month,
      monthText: month.split('-')[0] + '年' + Number(month.split('-')[1]) + '月'
    });
    this.loadDict();
    this.loadStat();
    this.loadRules();
  },

  loadDict() {
    const cached = store.getDictCache();
    if (cached) {
      this.tree = (cached && cached.categoryTree) || [];
    }
    store.loadDict()
      .then((dict) => {
        this.tree = (dict && dict.categoryTree) || [];
        this.renderParents(this.lastItems || []);
      })
      .catch(() => {});
  },

  loadStat() {
    statApi.category(this.data.month, BILL_TYPE.EXPENSE, { silent: true })
      .then((res) => {
        const items = (res && res.items) || [];
        this.lastItems = items;
        // 合计直接用接口返回值，和统计页同源，避免只汇总树内分类导致少算
        this.apiTotal = res && res.total !== undefined ? Number(res.total) || 0 : null;
        this.renderParents(items);
      })
      .catch(() => {
        this.renderParents([]);
        request.toast('分类数据加载失败');
      });

    statApi.month(this.data.month, { silent: true })
      .then((stat) => this.setData({ billCount: (stat && stat.billCount) || 0, loading: false }))
      .catch(() => this.setData({ loading: false }));
  },

  loadRules() {
    aliasApi.list({ silent: true })
      .then((list) => this.setData({ ruleCount: (list || []).length }))
      .catch(() => {});
  },

  renderParents(items) {
    const map = {};
    (items || []).forEach((it) => {
      map[it.categoryCode] = it;
    });

    let sum = 0;
    let used = 0;
    const base = this.tree.map((p) => {
      const hit = map[p.code];
      const amount = hit ? Number(hit.amount) || 0 : 0;
      sum += amount;
      if (amount > 0) {
        used += 1;
      }
      const color = palette.colorFor(p.name);
      return {
        code: p.code,
        name: p.name,
        icon: categoryIcon.iconOf({ code: p.code, icon: p.icon }),
        color,
        bg: palette.rgba(color, 0.15),
        amount,
        amountText: format.formatMoney(amount),
        children: p.children || [],
        unused: amount <= 0
      };
    });

    // 与统计页共用最大余数法，保证分类占比合计正好 100%
    const percents = splitPercent(base.map((it) => it.amount));
    const all = base.map((it, index) => Object.assign({}, it, {
      ratio: percents[index],
      ratioText: percents[index] + '%'
    }));

    const total = this.apiTotal === null || this.apiTotal === undefined ? sum : this.apiTotal;

    this.setData({
      parents: all.filter((p) => !p.unused),
      unusedParents: all.filter((p) => p.unused),
      unusedCount: all.filter((p) => p.unused).length,
      totalText: format.formatMoney(total),
      usedCount: used,
      loading: false
    });
  },

  onToggleUnused() {
    this.setData({ showUnused: !this.data.showUnused });
  },

  onParentTap(e) {
    const code = e.currentTarget.dataset.code;
    if (this.data.expanded === code) {
      this.setData({ expanded: '', children: [] });
      return;
    }
    const parent = this.data.parents.find((p) => p.code === code);
    this.setData({ expanded: code, children: [] });
    statApi.category(this.data.month, BILL_TYPE.EXPENSE, { silent: true, parentCode: code })
      .then((res) => {
        const map = {};
        ((res && res.items) || []).forEach((it) => {
          map[it.categoryCode] = it;
        });
        const children = ((parent && parent.children) || []).map((child) => {
          const hit = map[child.code];
          const amount = hit ? Number(hit.amount) || 0 : 0;
          const color = palette.colorFor(child.name);
          return {
            code: child.code,
            name: child.name,
            icon: categoryIcon.iconOf({ code: child.code, icon: child.icon }),
            color,
            amountText: amount > 0 ? format.formatMoney(amount) : '0.00',
            zero: amount <= 0
          };
        });
        this.setData({ children });
      })
      .catch(() => request.toast('明细加载失败'));
  },

  goRules() {
    wx.navigateTo({ url: '/pages/alias/alias/alias' });
  },

  goAI() {
    wx.switchTab({ url: '/pages/index/index' });
  }
});
