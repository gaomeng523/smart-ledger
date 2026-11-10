/**
 * 日历账本（设计稿 02 屏）
 * 月历按日汇总 → 选日看流水 → AI 洞察；搜索 + 高级筛选保留。
 */
const billApi = require('../../../api/bill');
const statApi = require('../../../api/stat');
const request = require('../../../utils/request');
const format = require('../../../utils/format');
const auth = require('../../../utils/auth');
const categoryIcon = require('../../../utils/category-icon');
const { BILL_TYPE } = require('../../../config/const');

const WEEK_HEAD = ['一', '二', '三', '四', '五', '六', '日'];

/** 月账单一次性拉取的最大页数（每页 200） */
const MAX_PAGES = 5;

Page({
  data: {
    month: '',
    monthText: '',
    monthNum: 0,
    yearText: '',

    weekHead: WEEK_HEAD,
    cells: [],
    seg: 'out',
    segOptions: [
      { value: 'out', label: '支出' },
      { value: 'in', label: '收入' },
      { value: 'net', label: '结余' }
    ],

    selectedDate: '',
    selectedLabel: '',
    selectedWeek: '',
    dayBills: [],
    dayCount: 0,
    dayOutText: '0.00',

    monthCount: 0,
    monthOutText: '0.00',
    monthInText: '0.00',

    insightPrefix: '',
    insightHighlight: '',
    insightSuffix: '',

    loading: true,
    error: false,

    // 搜索
    searchMode: false,
    keyword: '',
    searchLoading: false,
    searchGroups: [],
    searchTotal: 0,

    // 筛选
    filterVisible: false,
    filterValue: {},
    filterCount: -1,
    tagSuggestions: []
  },

  /** 当月全部账单 */
  records: [],
  /** 按日聚合 { 'yyyy-MM-dd': {expense, income, count} } */
  dayMap: {},

  onLoad() {
    this.setMonth(format.currentMonthStr(), true);
    this.loadTagSuggest();
  },

  onShow() {
    if (!auth.guard()) {
      return;
    }
    this.loadMonth();
  },

  onPullDownRefresh() {
    this.loadMonth(() => wx.stopPullDownRefresh());
  },

  setMonth(month, initial) {
    const parts = month.split('-');
    this.setData({
      month,
      monthNum: Number(parts[1]),
      yearText: parts[0],
      monthText: parts[0] + '年' + Number(parts[1]) + '月'
    });
    if (!initial) {
      this.setData({ selectedDate: '', searchMode: false, keyword: '', searchGroups: [] });
      this.records = [];
      this.dayMap = {};
      this.setData({ cells: [], dayBills: [], loading: true });
    }
  },

  /* ---------- 月份切换 ---------- */

  onPrevMonth() {
    this.shiftMonth(-1);
  },

  onNextMonth() {
    this.shiftMonth(1);
  },

  shiftMonth(delta) {
    const parts = this.data.month.split('-');
    const d = new Date(Number(parts[0]), Number(parts[1]) - 1 + delta, 1);
    this.setMonth(format.toMonthStr(d));
    this.loadMonth();
  },

  /* ---------- 数据 ---------- */

  loadMonth(callback) {
    this.setData({ loading: true, error: false });
    this.fetchMonthBills()
      .then((records) => {
        this.records = records;
        this.buildDayMap(records);
        const selected = this.pickSelectedDate();
        this.setData({
          loading: false,
          monthCount: records.length,
          monthOutText: format.formatMoney(this.sumMonth(records, 'expense')),
          monthInText: format.formatMoney(this.sumMonth(records, 'income'))
        });
        this.applySelection(selected);
        this.loadInsight();
        if (callback) callback();
      })
      .catch(() => {
        this.setData({ loading: false, error: true });
        request.toast('加载失败，请下拉重试');
        if (callback) callback();
      });
  },

  fetchMonthBills() {
    const first = billApi.list({ page: 1, size: 200, month: this.data.month }, { silent: true });
    return first.then((res) => {
      let records = (res && res.records) || [];
      const pages = Math.min((res && res.pages) || 1, MAX_PAGES);
      if (pages <= 1) {
        return records;
      }
      const rest = [];
      for (let page = 2; page <= pages; page += 1) {
        rest.push(billApi.list({ page, size: 200, month: this.data.month }, { silent: true }));
      }
      return Promise.all(rest).then((list) => {
        list.forEach((page) => {
          records = records.concat((page && page.records) || []);
        });
        return records;
      });
    });
  },

  sumMonth(records, key) {
    return records.reduce((sum, r) => {
      if (key === 'income' && Number(r.billType) !== BILL_TYPE.INCOME) return sum;
      if (key === 'expense' && Number(r.billType) === BILL_TYPE.INCOME) return sum;
      return sum + (Number(r.amount) || 0);
    }, 0);
  },

  buildDayMap(records) {
    const map = {};
    records.forEach((r) => {
      const date = r.recordDate || '';
      if (!map[date]) {
        map[date] = { expense: 0, income: 0, count: 0 };
      }
      const amount = Number(r.amount) || 0;
      if (Number(r.billType) === BILL_TYPE.INCOME) {
        map[date].income += amount;
      } else {
        map[date].expense += amount;
      }
      map[date].count += 1;
    });
    this.dayMap = map;
  },

  pickSelectedDate() {
    const today = format.todayStr();
    if (today.indexOf(this.data.month) === 0) {
      return today;
    }
    const dates = Object.keys(this.dayMap).sort();
    return dates.length ? dates[dates.length - 1] : this.data.month + '-01';
  },

  /* ---------- 月历 ---------- */

  buildCells(selectedDate) {
    const parts = this.data.month.split('-');
    const year = Number(parts[0]);
    const monthIndex = Number(parts[1]) - 1;
    const days = new Date(year, monthIndex + 1, 0).getDate();
    const firstWeekday = (new Date(year, monthIndex, 1).getDay() + 6) % 7;
    const today = format.todayStr();
    const seg = this.data.seg;
    const cells = [];

    for (let i = 0; i < firstWeekday; i += 1) {
      cells.push({ key: 'blank' + i, blank: true });
    }

    for (let day = 1; day <= days; day += 1) {
      const date = this.data.month + '-' + format.pad2(day);
      const stat = this.dayMap[date];
      cells.push({
        key: date,
        date,
        day,
        count: stat ? stat.count : 0,
        amountText: this.cellAmount(stat, seg),
        isToday: date === today,
        isSelected: date === selectedDate
      });
    }

    this.setData({ cells });
  },

  cellAmount(stat, seg) {
    if (!stat) {
      return '';
    }
    let value = 0;
    if (seg === 'in') {
      value = stat.income;
    } else if (seg === 'net') {
      value = stat.expense - stat.income;
    } else {
      value = stat.expense;
    }
    if (Math.abs(value) < 0.005) {
      return '';
    }
    const n = Math.abs(value);
    const text = n >= 10000
      ? (n / 10000).toFixed(1) + 'w'
      : (n >= 1000 ? Math.round(n / 100) / 10 + 'k' : String(Math.round(n)));
    return (value < 0 ? '-' : '') + text;
  },

  onSegTap(e) {
    const seg = e.currentTarget.dataset.seg;
    if (seg === this.data.seg) {
      return;
    }
    this.setData({ seg });
    this.buildCells(this.data.selectedDate);
  },

  onDayTap(e) {
    const date = e.currentTarget.dataset.date;
    if (!date) {
      return;
    }
    this.applySelection(date);
  },

  applySelection(date) {
    const bills = this.records.filter((r) => r.recordDate === date);
    const out = bills.reduce((sum, r) => {
      return Number(r.billType) === BILL_TYPE.INCOME ? sum : sum + (Number(r.amount) || 0);
    }, 0);

    this.setData({
      selectedDate: date,
      selectedLabel: date ? Number(date.split('-')[1]) + '月' + Number(date.split('-')[2]) + '日' : '',
      selectedWeek: date === format.todayStr() ? '今天 · ' + format.weekdayText(date) : format.weekdayText(date),
      dayCount: bills.length,
      dayOutText: format.formatMoney(out),
      dayBills: bills.map((r, index) => this.toRow(r, index))
    });
    this.buildCells(date);
  },

  toRow(record, index) {
    const income = Number(record.billType) === BILL_TYPE.INCOME;
    return {
      billId: record.billId,
      icon: categoryIcon.iconOf({ code: record.categoryCode, icon: record.categoryIcon }),
      name: record.remark || record.categoryName || '未分类',
      meta: [record.categoryName, record.payChannelName].filter(Boolean).join(' · '),
      amountText: (income ? '+' : '-') + '¥' + format.formatMoney(record.amount),
      income,
      tint: index % 3
    };
  },

  /* ---------- AI 洞察 ---------- */

  loadInsight() {
    if (!this.records.length) {
      this.setData({ insightPrefix: '', insightHighlight: '', insightSuffix: '' });
      return;
    }
    statApi.category(this.data.month, BILL_TYPE.EXPENSE, { silent: true })
      .then((res) => {
        const items = (res && res.items) || [];
        if (!items.length) {
          return;
        }
        const top = items[0];
        const ratio = Number(top.ratio) || 0;
        const ratioText = Math.round(ratio * 100) + '%';
        const prevMonth = this.prevMonth();
        statApi.category(prevMonth, BILL_TYPE.EXPENSE, { silent: true })
          .then((prevRes) => {
            const prev = ((prevRes && prevRes.items) || [])
              .find((it) => it.categoryCode === top.categoryCode);
            if (prev && Number(prev.amount) > 0) {
              const delta = (Number(top.amount) - Number(prev.amount)) / Number(prev.amount);
              if (Math.abs(delta) >= 0.03) {
                this.setData({
                  insightPrefix: '本月' + top.categoryName + '共 ¥' + format.formatMoney(top.amount) + '，比上月',
                  insightHighlight: (delta < 0 ? '少 ' : '多 ') + Math.round(Math.abs(delta) * 100) + '%',
                  insightSuffix: '。'
                });
                return;
              }
            }
            this.setData({
              insightPrefix: '本月' + top.categoryName + '支出 ¥' + format.formatMoney(top.amount),
              insightHighlight: '占 ' + ratioText,
              insightSuffix: '，是最大的一笔开销。'
            });
          })
          .catch(() => {
            this.setData({
              insightPrefix: '本月' + top.categoryName + '支出 ¥' + format.formatMoney(top.amount),
              insightHighlight: '占 ' + ratioText,
              insightSuffix: '。'
            });
          });
      })
      .catch(() => {
        this.setData({ insightPrefix: '', insightHighlight: '', insightSuffix: '' });
      });
  },

  prevMonth() {
    const parts = this.data.month.split('-');
    const d = new Date(Number(parts[0]), Number(parts[1]) - 2, 1);
    return format.toMonthStr(d);
  },

  /* ---------- 搜索 / 筛选 ---------- */

  onSearchOpen() {
    this.setData({
      searchMode: true,
      keyword: '',
      searchGroups: [],
      searchTotal: 0
    });
  },

  onSearchClose() {
    this.setData({ searchMode: false, keyword: '', searchGroups: [], searchTotal: 0 });
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value || '' });
  },

  onKeywordConfirm() {
    this.doSearch();
  },

  doSearch() {
    const keyword = (this.data.keyword || '').trim();
    if (!keyword && !this.hasFilter()) {
      request.toast('输入关键词，或设置筛选条件');
      return;
    }
    this.setData({ searchLoading: true });
    billApi.list(this.buildQuery(1, 50), { silent: true })
      .then((res) => {
        const records = (res && res.records) || [];
        this.setData({
          searchLoading: false,
          searchTotal: (res && res.total) || 0,
          searchGroups: this.groupByDate(records)
        });
      })
      .catch(() => {
        this.setData({ searchLoading: false });
        request.toast('搜索失败，请稍后再试');
      });
  },

  hasFilter() {
    const f = this.data.filterValue || {};
    return !!(f.amountMin || f.amountMax || f.dateStart || f.dateEnd ||
      (f.tags && f.tags.length) || (f.source !== '' && f.source !== undefined && f.source !== null));
  },

  buildQuery(page, size, filter) {
    const f = filter || this.data.filterValue || {};
    const query = { page, size, month: this.data.month };
    const keyword = (this.data.keyword || '').trim();
    if (keyword) {
      query.keyword = keyword;
    }
    ['amountMin', 'amountMax', 'dateStart', 'dateEnd'].forEach((k) => {
      if (f[k] !== '' && f[k] !== undefined && f[k] !== null) {
        query[k] = k === 'dateStart' || k === 'dateEnd' ? f[k] : Number(f[k]);
      }
    });
    if (f.source !== '' && f.source !== undefined && f.source !== null) {
      query.source = Number(f.source);
    }
    if (f.tags && f.tags.length) {
      query.tag = f.tags.join(',');
    }
    return query;
  },

  groupByDate(records) {
    const map = {};
    const order = [];
    records.forEach((r) => {
      const date = r.recordDate || '';
      if (!map[date]) {
        map[date] = { date, label: format.shortDate(date) + ' ' + format.weekdayText(date), items: [] };
        order.push(date);
      }
      map[date].items.push(this.toRow(r, map[date].items.length));
    });
    return order.map((date) => map[date]);
  },

  onFilterOpen() {
    this.setData({ filterVisible: true, filterCount: -1 });
    this.requestFilterCount();
  },

  onFilterClose() {
    this.setData({ filterVisible: false });
  },

  onFilterChange(e) {
    this.filterDraft = e.detail || {};
    if (this._countTimer) {
      clearTimeout(this._countTimer);
    }
    this._countTimer = setTimeout(() => {
      this._countTimer = null;
      this.requestFilterCount();
    }, 320);
  },

  onFilterConfirm(e) {
    this.setData({ filterVisible: false, filterValue: e.detail || {} });
    this.doSearch();
  },

  onFilterReset() {
    this.setData({ filterValue: {}, filterVisible: false });
    this.doSearch();
  },

  requestFilterCount() {
    billApi.list(this.buildQuery(1, 1, this.filterDraft || {}), { silent: true })
      .then((res) => this.setData({ filterCount: (res && res.total) || 0 }))
      .catch(() => this.setData({ filterCount: -1 }));
  },

  loadTagSuggest() {
    billApi.tagSuggest({ silent: true })
      .then((res) => this.setData({ tagSuggestions: (res && res.tags) || [] }))
      .catch(() => {});
  },

  /* ---------- 跳转 ---------- */

  onBillTap(e) {
    const billId = e.currentTarget.dataset.id;
    if (!billId) {
      return;
    }
    wx.navigateTo({ url: '/pages/bill/detail/detail?billId=' + billId });
  },

  onRetry() {
    this.loadMonth();
  }
});
