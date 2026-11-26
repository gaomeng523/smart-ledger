/**
 * 统计 · 月度报告（设计稿 03 屏）
 * KPI + 每日支出柱图 + 分类构成环图（可下钻）+ 消费洞察。
 */
const statApi = require('../../../api/stat');
const billApi = require('../../../api/bill');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const palette = require('../../../utils/palette');
const { splitPercent } = require('../../../utils/percent');
const auth = require('../../../utils/auth');
const { BILL_TYPE } = require('../../../config/const');
const { isParentCode } = require('../../../utils/category-icon');

/** 分类色板统一取自全局色板；未知分类用石墨色兜底 */
const COLOR_OTHER = '#9AA0A9';

/** 柱图最大高度（rpx） */
const BAR_MAX_H = 216;

/** 月账单拉取上限页数 */
const MAX_PAGES = 5;

/** 金额拆成整数 / 小数两段，便于分字号展示 */
function moneyParts(value) {
  const text = format.formatMoney(value);
  const index = text.indexOf('.');
  return { int: text.slice(0, index), dec: text.slice(index + 1) };
}

Page({
  data: {
    month: '',
    monthNum: 0,
    yearText: '',
    daysInMonth: 30,
    updatedText: '',

    expenseText: '0.00',
    incomeText: '0.00',
    balanceText: '0.00',
    expenseInt: '0',
    expenseDec: '00',
    incomeInt: '0',
    incomeDec: '00',
    balanceInt: '0',
    balanceDec: '00',
    balanceNegative: false,
    billCount: 0,
    diffText: '',
    expenseDiffUp: false,

    seg: BILL_TYPE.EXPENSE,
    bars: [],
    dailyAvgText: '0.00',
    avgLineBottom: 0,
    todayBar: 0,

    chartItems: [],
    legend: [],
    totalText: '0.00',
    drillCode: '',
    drillName: '',

    insights: [],
    loading: true,
    error: false
  },

  /** 当月账单（用于每日柱子） */
  records: [],

  /** 与上月同期的对比结果 */
  mom: null,

  onLoad() {
    this.setMonth(format.currentMonthStr(), true);
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(2);
    }
    // 游客态不拉数据，展示空统计；需要看自己的数据时去登录
    if (!auth.guard()) {
      this.setData({ loading: false });
      return;
    }
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll(() => wx.stopPullDownRefresh());
  },

  setMonth(month, initial) {
    const parts = month.split('-');
    this.setData({
      month,
      monthNum: Number(parts[1]),
      yearText: parts[0],
      daysInMonth: new Date(Number(parts[0]), Number(parts[1]), 0).getDate(),
      drillCode: '',
      drillName: ''
    });
    if (!initial) {
      this.setData({ loading: true, error: false, bars: [], chartItems: [], legend: [] });
    }
  },

  onPrevMonth() {
    this.shiftMonth(-1);
  },

  onNextMonth() {
    this.shiftMonth(1);
  },

  shiftMonth(delta) {
    const parts = this.data.month.split('-');
    this.setMonth(format.toMonthStr(new Date(Number(parts[0]), Number(parts[1]) - 1 + delta, 1)));
    this.loadAll();
  },

  /* ---------- 加载 ---------- */

  loadAll(callback) {
    this.setData({ loading: true, error: false, drillCode: '', drillName: '' });
    Promise.all([
      statApi.month(this.data.month, { silent: true }),
      statApi.category(this.data.month, this.data.seg, { silent: true }),
      this.fetchMonthBills(this.data.month),
      this.fetchMonthBills(this.prevMonth())
    ])
      .then((res) => {
        const stat = res[0] || {};
        const cat = res[1] || {};
        this.records = res[2] || [];
        // 环比统一按「上月同期」口径：上月只统计到和本月相同的那一天
        this.mom = this.computeMom(this.records, res[3] || []);

        const expense = moneyParts(stat.expense);
        const income = moneyParts(stat.income);
        const balance = moneyParts(stat.balance);

        this.setData({
          loading: false,
          expenseText: format.formatMoney(stat.expense),
          incomeText: format.formatMoney(stat.income),
          balanceText: format.formatMoney(stat.balance),
          expenseInt: expense.int,
          expenseDec: expense.dec,
          incomeInt: income.int,
          incomeDec: income.dec,
          balanceInt: balance.int,
          balanceDec: balance.dec,
          balanceNegative: (Number(stat.balance) || 0) < 0,
          billCount: stat.billCount || 0,
          totalText: format.formatMoney(cat.total),
          updatedText: this.updatedText(),
          dailyAvgText: format.formatMoney(this.dailyAvg(stat)),
          todayBar: this.isCurrentMonth() ? new Date().getDate() : 0
        });
        this.buildBars(stat);
        this.buildLegend(cat);
        this.buildInsights(stat, cat);
        if (callback) callback();
      })
      .catch(() => {
        this.setData({ loading: false, error: true });
        request.toast('统计加载失败，请下拉重试');
        if (callback) callback();
      });
  },

  isCurrentMonth() {
    return this.data.month === format.currentMonthStr();
  },

  updatedText() {
    const parts = this.data.month.split('-');
    const days = new Date(Number(parts[0]), Number(parts[1]), 0).getDate();
    const day = this.isCurrentMonth() ? new Date().getDate() : days;
    return Number(parts[1]) + '月' + day + '日';
  },

  fetchMonthBills(month) {
    const target = month || this.data.month;
    return billApi.list({ page: 1, size: 200, month: target }, { silent: true })
      .then((res) => {
        let records = (res && res.records) || [];
        const pages = Math.min((res && res.pages) || 1, MAX_PAGES);
        if (pages <= 1) {
          return records;
        }
        const tasks = [];
        for (let page = 2; page <= pages; page += 1) {
          tasks.push(billApi.list({ page, size: 200, month: target }, { silent: true }));
        }
        return Promise.all(tasks).then((list) => {
          list.forEach((p) => {
            records = records.concat((p && p.records) || []);
          });
          return records;
        });
      })
      .catch(() => []);
  },

  dailyAvg(stat) {
    const used = this.periodDay();
    return used > 0 ? (Number(stat.expense) || 0) / used : 0;
  },

  /** 当前月的统计截止日：本月为今天，历史月份为月末 */
  periodDay() {
    const parts = this.data.month.split('-');
    const days = new Date(Number(parts[0]), Number(parts[1]), 0).getDate();
    return this.isCurrentMonth() ? new Date().getDate() : days;
  },

  /** 汇总支出金额与笔数；maxDay > 0 时只统计到该日（上月同期） */
  sumExpense(records, maxDay) {
    const result = { amount: 0, count: 0 };
    (records || []).forEach((r) => {
      if (Number(r.billType) === BILL_TYPE.INCOME) {
        return;
      }
      const day = Number((r.recordDate || '').slice(8, 10));
      if (!day || (maxDay > 0 && day > maxDay)) {
        return;
      }
      result.amount += Number(r.amount) || 0;
      result.count += 1;
    });
    return result;
  },

  computeMom(records, prevRecords) {
    const cur = this.sumExpense(records, 0);
    const prev = this.sumExpense(prevRecords, this.periodDay());
    const diff = cur.amount - prev.amount;
    const pct = prev.amount > 0 ? Math.round((Math.abs(diff) / prev.amount) * 100) : 0;
    return {
      amount: cur.amount,
      count: cur.count,
      prevAmount: prev.amount,
      prevCount: prev.count,
      diff,
      pct,
      // 本月与上月同期都有 5 笔以上、且差额超过 ¥200 时才做环比提示
      enough: cur.count >= 5 && prev.count >= 5 && Math.abs(diff) >= 200 && pct > 0
    };
  },

  /* ---------- 每日柱图 ---------- */

  buildBars(stat) {
    const parts = this.data.month.split('-');
    const days = new Date(Number(parts[0]), Number(parts[1]), 0).getDate();
    const map = {};
    this.records.forEach((r) => {
      if (Number(r.billType) === BILL_TYPE.INCOME) {
        return;
      }
      const day = Number((r.recordDate || '').slice(8, 10));
      if (!day) {
        return;
      }
      map[day] = (map[day] || 0) + (Number(r.amount) || 0);
    });

    const peak = Math.max.apply(null, [0].concat(Object.keys(map).map((k) => map[k])));
    // y 轴上限取最大值的 1.15 倍，柱顶留白且给当天金额标签留位置
    const yMax = peak > 0 ? peak * 1.15 : 1;
    const today = this.periodDay();
    const avg = Number(this.dailyAvg(stat)) || 0;
    const bars = [];

    for (let day = 1; day <= days; day += 1) {
      const value = map[day] || 0;
      const future = day > today;
      bars.push({
        day,
        value,
        today: day === today,
        h: value && !future ? Math.max(8, Math.round((value / yMax) * BAR_MAX_H)) : 2,
        isEmpty: !value && !future,
        isFuture: future,
        valueText: format.formatMoney(value)
      });
    }

    this.setData({
      bars,
      avgLineBottom: avg > 0 ? Math.round((avg / yMax) * BAR_MAX_H) : 0
    });
  },

  onBarTap(e) {
    const { day, text } = e.currentTarget.dataset;
    if (!day || text === undefined) {
      return;
    }
    wx.showToast({
      title: Number(day) + '日 支出 ¥' + text,
      icon: 'none'
    });
  },

  /* ---------- 分类构成 ---------- */

  buildLegend(cat) {
    const items = ((cat && cat.items) || []).slice().sort((a, b) => Number(b.amount) - Number(a.amount));
    const total = Number(cat && cat.total) || 0;
    const percents = splitPercent(items.map((it) => Number(it.amount) || 0));

    const legend = items.map((it, index) => ({
      code: it.categoryCode,
      name: it.categoryName,
      amount: Number(it.amount) || 0,
      amountText: format.formatMoney(it.amount),
      ratioValue: percents[index],
      ratioText: percents[index] + '%',
      color: palette.colorFor(it.categoryName),
      drillable: isParentCode(it.categoryCode)
    }));

    let chartItems = legend.slice(0, 5).map((it) => ({
      name: it.name,
      amount: it.amount,
      color: it.color
    }));
    if (legend.length > 5) {
      const rest = legend.slice(5).reduce((sum, it) => sum + it.amount, 0);
      chartItems.push({ name: '其他', amount: rest, color: COLOR_OTHER });
    }
    if (!chartItems.length && total > 0) {
      chartItems = [{ name: '其他', amount: total, color: COLOR_OTHER }];
    }

    this.setData({ chartItems, legend });
  },

  onDrillTap(e) {
    const code = e.currentTarget.dataset.code;
    if (!code) {
      return;
    }
    statApi.category(this.data.month, this.data.seg, { silent: true, parentCode: code })
      .then((cat) => {
        const name = (this.data.legend.find((it) => it.code === code) || {}).name || '分类明细';
        this.setData({ drillCode: code, drillName: name, totalText: format.formatMoney(cat.total) });
        this.buildLegend(cat);
      })
      .catch(() => request.toast('明细加载失败，请稍后再试'));
  },

  onDrillBack() {
    this.setData({ drillCode: '', drillName: '' });
    statApi.category(this.data.month, this.data.seg, { silent: true })
      .then((cat) => {
        this.setData({ totalText: format.formatMoney(cat.total) });
        this.buildLegend(cat);
      })
      .catch(() => {});
  },

  onSegTap(e) {
    const seg = Number(e.currentTarget.dataset.seg);
    if (seg === this.data.seg) {
      return;
    }
    this.setData({ seg, drillCode: '', drillName: '' });
    statApi.category(this.data.month, seg, { silent: true })
      .then((cat) => {
        this.setData({ totalText: format.formatMoney(cat.total) });
        this.buildLegend(cat);
      })
      .catch(() => {});
  },

  /* ---------- 消费洞察 ---------- */

  buildInsights(stat, cat) {
    const list = [];
    const expense = Number(stat.expense) || 0;
    const income = Number(stat.income) || 0;
    const mom = this.mom;
    const enough = !!(mom && mom.enough);
    const top = (this.data.legend || [])[0];

    if (expense <= 0) {
      this.setData({ diffText: '', expenseDiffUp: false, insights: [] });
      return;
    }

    // 1. 环比：样本足够时才提示涨跌，否则只做中性描述
    if (enough) {
      const up = mom.diff > 0;
      list.push({
        key: 'mom',
        text: '较上月同期' + (up ? '多支出 ¥' : '少支出 ¥') + format.formatMoney(Math.abs(mom.diff)) +
          '（' + (up ? '↑' : '↓') + mom.pct + '%），' +
          (up ? '留意大额支出。' : '节奏控制得不错。')
      });
      this.setData({
        diffText: '较上月同期 ' + (up ? '↑' : '↓') + mom.pct + '%',
        expenseDiffUp: up
      });
    } else {
      list.push({
        key: 'sum',
        text: '本月已支出 ¥' + format.formatMoney(expense) +
          (top && top.ratioValue ? '，' + top.name + '占 ' + top.ratioValue + '%' : '') + '。'
      });
      this.setData({ diffText: '', expenseDiffUp: false });
    }

    // 2. 最大分类
    if (enough && top && top.ratioValue) {
      list.push({
        key: 'top',
        text: top.name + '占支出的 ' + top.ratioValue + '%，是本月最大的一类开销。'
      });
    }

    // 3. 日均
    const days = this.periodDay();
    const avg = days > 0 ? expense / days : 0;
    if (avg > 0) {
      let text = '日均 ¥' + format.formatMoney(avg);
      if (enough && days > 0) {
        const prevAvg = mom.prevAmount / days;
        const gap = Math.abs(avg - prevAvg);
        text += '，较上月同期' + (avg > prevAvg ? '多 ' : '少 ') + '¥' + format.formatMoney(gap);
      }
      list.push({ key: 'avg', text: text + '。' });
    }

    // 4. 结余率
    if (income > 0) {
      const rate = Math.max(0, Math.round((1 - expense / income) * 100));
      list.push({ key: 'rate', text: '本月结余率 ' + rate + '%，' + (rate >= 30 ? '攒钱状态良好。' : '可以给自己定个小目标。') });
    }

    this.setData({ insights: list.slice(0, 3) });
  },

  prevMonth() {
    const parts = this.data.month.split('-');
    return format.toMonthStr(new Date(Number(parts[0]), Number(parts[1]) - 2, 1));
  },

  /* ---------- 其他 ---------- */

  goAI() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  onRetry() {
    this.loadAll();
  }
});
