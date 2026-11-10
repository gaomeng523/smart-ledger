/**
 * 统计 · 月度报告（设计稿 03 屏）
 * KPI + 每日支出柱图 + 分类构成环图（可下钻）+ AI 洞察。
 */
const statApi = require('../../../api/stat');
const billApi = require('../../../api/bill');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const auth = require('../../../utils/auth');
const { BILL_TYPE } = require('../../../config/const');
const { isParentCode } = require('../../../utils/category-icon');

/** 分类色板（与设计稿一致） */
const PALETTE = ['#3D8BFF', '#FF6B6B', '#57D9A3', '#8B6CFF', '#FFB020'];
const COLOR_OTHER = '#3A3B42';

/** 柱图最大高度（rpx） */
const BAR_MAX_H = 168;

/** 月账单拉取上限页数 */
const MAX_PAGES = 5;

Page({
  data: {
    month: '',
    monthNum: 0,
    yearText: '',
    monthCn: '',
    monthEn: '',
    daysInMonth: 30,
    updatedText: '',

    expenseText: '0.00',
    incomeText: '0.00',
    balanceText: '0.00',
    billCount: 0,
    expenseDiff: '',
    expenseDiffUp: false,

    seg: BILL_TYPE.EXPENSE,
    bars: [],
    barMax: BAR_MAX_H,
    dailyAvgText: '0.00',
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
  prevStat: null,

  onLoad() {
    this.setMonth(format.currentMonthStr(), true);
  },

  onShow() {
    if (!auth.guard()) {
      return;
    }
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll(() => wx.stopPullDownRefresh());
  },

  setMonth(month, initial) {
    const parts = month.split('-');
    const EN = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY',
      'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];
    this.setData({
      month,
      monthNum: Number(parts[1]),
      yearText: parts[0],
      monthCn: this.cnMonth(Number(parts[1])),
      monthEn: EN[Number(parts[1]) - 1] || '',
      daysInMonth: new Date(Number(parts[0]), Number(parts[1]), 0).getDate(),
      drillCode: '',
      drillName: ''
    });
    if (!initial) {
      this.setData({ loading: true, error: false, bars: [], chartItems: [], legend: [] });
    }
  },

  cnMonth(n) {
    const cn = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二'];
    return (cn[n - 1] || n) + '月';
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
      this.fetchMonthBills(),
      statApi.month(this.prevMonth(), { silent: true }).catch(() => null)
    ])
      .then((res) => {
        const stat = res[0] || {};
        const cat = res[1] || {};
        this.records = res[2] || [];
        this.prevStat = res[3];

        this.setData({
          loading: false,
          expenseText: format.formatMoney(stat.expense),
          incomeText: format.formatMoney(stat.income),
          balanceText: format.formatMoney(stat.balance),
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

  fetchMonthBills() {
    return billApi.list({ page: 1, size: 200, month: this.data.month }, { silent: true })
      .then((res) => {
        let records = (res && res.records) || [];
        const pages = Math.min((res && res.pages) || 1, MAX_PAGES);
        if (pages <= 1) {
          return records;
        }
        const tasks = [];
        for (let page = 2; page <= pages; page += 1) {
          tasks.push(billApi.list({ page, size: 200, month: this.data.month }, { silent: true }));
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
    const parts = this.data.month.split('-');
    const days = new Date(Number(parts[0]), Number(parts[1]), 0).getDate();
    const used = this.isCurrentMonth() ? new Date().getDate() : days;
    return used > 0 ? (Number(stat.expense) || 0) / used : 0;
  },

  /* ---------- 每日柱图 ---------- */

  buildBars() {
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

    const max = Math.max.apply(null, [1].concat(Object.keys(map).map((k) => map[k])));
    const bars = [];
    for (let day = 1; day <= days; day += 1) {
      const value = map[day] || 0;
      bars.push({
        day,
        h: value ? Math.max(8, Math.round((value / max) * BAR_MAX_H)) : 3,
        empty: !value
      });
    }
    this.setData({ bars });
  },

  /* ---------- 分类构成 ---------- */

  buildLegend(cat) {
    const items = ((cat && cat.items) || []).slice().sort((a, b) => Number(b.amount) - Number(a.amount));
    const total = Number(cat && cat.total) || 0;

    const legend = items.map((it, index) => ({
      code: it.categoryCode,
      name: it.categoryName,
      amount: Number(it.amount) || 0,
      amountText: format.formatMoney(it.amount),
      ratioText: Math.round((Number(it.ratio) || 0) * 100) + '%',
      color: PALETTE[index] || COLOR_OTHER,
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

  /* ---------- AI 洞察 ---------- */

  buildInsights(stat, cat) {
    const list = [];
    const expense = Number(stat.expense) || 0;
    const income = Number(stat.income) || 0;
    const prev = this.prevStat;

    if (prev && Number(prev.expense) > 0) {
      const delta = (expense - Number(prev.expense)) / Number(prev.expense);
      const pct = Math.round(Math.abs(delta) * 100);
      if (pct > 0) {
        list.push({
          key: 'mom',
          text: '支出比上月' + (delta < 0 ? '少 ' : '多 ') + pct + '%' +
            (delta < 0 ? '，节奏控制得不错。' : '，留意大额支出。')
        });
        this.setData({
          expenseDiff: (delta < 0 ? '↓ ' : '↑ ') + pct + '%',
          expenseDiffUp: delta > 0
        });
      }
    } else {
      this.setData({ expenseDiff: '', expenseDiffUp: false });
    }

    const items = ((cat && cat.items) || []).slice().sort((a, b) => Number(b.amount) - Number(a.amount));
    if (items.length) {
      const top = items[0];
      list.push({
        key: 'top',
        text: (top.categoryName || '最大分类') + ' 占支出的 ' +
          Math.round((Number(top.ratio) || 0) * 100) + '%，本月最大一笔开销。'
      });
    }

    if (prev && Number(prev.expense) > 0) {
      const parts = this.data.month.split('-');
      const days = new Date(Number(parts[0]), Number(parts[1]), 0).getDate();
      const prevDays = new Date(Number(parts[0]), Number(parts[1]) - 1, 0).getDate();
      const avg = expense / days;
      const prevAvg = Number(prev.expense) / prevDays;
      if (prevAvg > 0) {
        list.push({
          key: 'avg',
          text: '日均 ¥' + format.formatMoney(avg) + '，' +
            (avg <= prevAvg ? '低于' : '高于') + '上月日均 ¥' + format.formatMoney(prevAvg) + '。'
        });
      }
    }

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
