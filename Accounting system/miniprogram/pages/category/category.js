/**
 * 分类管理（设计稿 06 屏）
 * 一级分类 + 二级明细（按需下钻）+ AI 学习规则入口。
 */
const statApi = require('../../api/stat');
const aliasApi = require('../../api/alias');
const store = require('../../store/index');
const request = require('../../utils/request');
const format = require('../../utils/format');
const categoryIcon = require('../../utils/category-icon');
const { BILL_TYPE } = require('../../config/const');

const PALETTE = ['#3D8BFF', '#FF6B6B', '#57D9A3', '#8B6CFF', '#FFB020', '#6C8CFF', '#FF8FA3', '#7FD1C7'];

Page({
  data: {
    month: '',
    monthText: '',
    totalText: '0.00',
    billCount: 0,
    usedCount: 0,
    parents: [],
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
    let total = 0;
    let used = 0;
    const parents = this.tree.map((p, index) => {
      const hit = map[p.code];
      const amount = hit ? Number(hit.amount) || 0 : 0;
      total += amount;
      if (amount > 0) {
        used += 1;
      }
      return {
        code: p.code,
        name: p.name,
        icon: categoryIcon.iconOf({ code: p.code, icon: p.icon }),
        color: PALETTE[index % PALETTE.length],
        amountText: format.formatMoney(amount),
        ratio: hit ? Math.round((Number(hit.ratio) || 0) * 100) : 0,
        children: p.children || []
      };
    });

    this.setData({
      parents,
      totalText: format.formatMoney(total),
      usedCount: used,
      loading: false
    });
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
          return {
            code: child.code,
            name: child.name,
            icon: categoryIcon.iconOf({ code: child.code, icon: child.icon }),
            amountText: amount > 0 ? format.formatMoney(amount) : '—',
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
