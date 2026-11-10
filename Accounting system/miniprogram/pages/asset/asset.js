/**
 * 资产体检（设计稿 04 屏）
 * 账户余额手动维护（仅存本机），AI 体检结论由本地规则生成。
 */
const statApi = require('../../api/stat');
const storage = require('../../utils/storage');
const request = require('../../utils/request');
const format = require('../../utils/format');

const STORE_KEY = 'assetAccounts';

/** 默认账户（首次进入的预置账户，余额由用户手工填写） */
const DEFAULT_ACCOUNTS = [
  { id: 'a_cmb', name: '招商银行储蓄卡', kind: 'asset', liquid: false, amount: 0, icon: 'card', color: '#3D8BFF' },
  { id: 'a_wechat', name: '微信零钱', kind: 'asset', liquid: true, amount: 0, icon: 'wallet', color: '#57D9A3' },
  { id: 'a_alipay', name: '支付宝余额', kind: 'asset', liquid: true, amount: 0, icon: 'wallet', color: '#6C8CFF' },
  { id: 'a_credit', name: '信用卡待还', kind: 'debt', liquid: false, amount: 0, icon: 'card', color: '#FF6B6B' }
];

Page({
  data: {
    accounts: [],
    assetText: '0.00',
    debtText: '0.00',
    netText: '0.00',
    accountCount: 0,
    score: 0,
    scoreText: '',
    scoreColor: '#3D8BFF',
    findings: []
  },

  monthExpense: 0,

  onShow() {
    this.loadAccounts();
    this.loadMonth();
  },

  loadAccounts() {
    const saved = storage.get(STORE_KEY, null);
    const accounts = Array.isArray(saved) && saved.length
      ? saved
      : DEFAULT_ACCOUNTS.slice();

    this.render(accounts);
  },

  render(accounts) {
    let asset = 0;
    let debt = 0;
    const rows = accounts.map((acc) => {
      const amount = Number(acc.amount) || 0;
      if (acc.kind === 'debt') {
        debt += Math.abs(amount);
      } else {
        asset += amount;
      }
      return Object.assign({}, acc, {
        amountText: format.formatMoney(Math.abs(amount)),
        isDebt: acc.kind === 'debt'
      });
    });

    this.accounts = accounts;
    this.setData({
      accounts: rows,
      accountCount: rows.length,
      assetText: format.formatMoney(asset),
      debtText: format.formatMoney(debt),
      netText: format.formatMoney(asset - debt)
    });
    this.assetTotal = asset;
    this.debtTotal = debt;
    this.buildCheckup(rows, asset, debt);
  },

  save(accounts) {
    storage.set(STORE_KEY, accounts);
    this.render(accounts);
  },

  loadMonth() {
    statApi.month(format.currentMonthStr(), { silent: true })
      .then((stat) => {
        this.monthExpense = Number(stat.expense) || 0;
        this.buildCheckup(this.data.accounts, this.assetTotal, this.debtTotal);
      })
      .catch(() => {});
  },

  /* ---------- 编辑 ---------- */

  onAccountTap(e) {
    const id = e.currentTarget.dataset.id;
    const account = this.accounts.find((a) => a.id === id);
    if (!account) {
      return;
    }
    wx.showModal({
      title: account.name,
      editable: true,
      placeholderText: '输入当前余额',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        const value = Number(String(res.content || '').replace(/[¥,\s]/g, ''));
        if (!isFinite(value) || value < 0) {
          request.toast('请输入正确的金额');
          return;
        }
        const next = this.accounts.map((a) => (a.id === id ? Object.assign({}, a, { amount: value }) : a));
        this.save(next);
      }
    });
  },

  onAddAccount() {
    wx.showModal({
      title: '新增账户',
      editable: true,
      placeholderText: '账户名称，如「工资卡」',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        const name = (res.content || '').trim();
        if (!name) {
          request.toast('请输入账户名称');
          return;
        }
        const accounts = this.accounts.concat({
          id: 'a_' + Date.now(),
          name: name.slice(0, 12),
          kind: 'asset',
          liquid: true,
          amount: 0,
          icon: 'wallet',
          color: '#7B7C82'
        });
        this.save(accounts);
        request.toast('已添加，点它填写余额');
      }
    });
  },

  onAccountLongPress(e) {
    const id = e.currentTarget.dataset.id;
    const account = this.accounts.find((a) => a.id === id);
    if (!account) {
      return;
    }
    wx.showModal({
      title: '删除账户',
      content: '确定删除「' + account.name + '」吗？',
      confirmText: '删除',
      confirmColor: '#FF6B6B',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        this.save(this.accounts.filter((a) => a.id !== id));
      }
    });
  },

  /* ---------- 体检 ---------- */

  buildCheckup(rows, asset, debt) {
    const liquid = rows
      .filter((r) => r.liquid && r.isDebt !== true)
      .reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
    const runway = this.monthExpense > 0 ? liquid / this.monthExpense : 0;
    const debtRatio = asset > 0 ? debt / asset : (debt > 0 ? 1 : 0);

    let score = 55;
    if (asset > debt) score += 15;
    if (debtRatio < 0.2) score += 10;
    if (runway >= 3) score += 10;
    if (rows.length >= 3) score += 5;
    score = Math.min(96, score);

    const findings = [];
    findings.push({
      key: 'net',
      text: asset - debt >= 0
        ? '净资产为 ¥' + format.formatMoney(asset - debt) + '，整体为正。'
        : '净资产为负 ¥' + format.formatMoney(debt - asset) + '，建议先还清高息负债。'
    });

    if (debt > 0) {
      findings.push({
        key: 'debt',
        text: '待还负债 ¥' + format.formatMoney(debt) + '，占资产 ' + Math.round(debtRatio * 100) + '%。'
      });
    }

    if (this.monthExpense > 0 && liquid > 0) {
      findings.push({
        key: 'runway',
        text: '可随时动用的钱约 ¥' + format.formatMoney(liquid) + '，可覆盖 ' +
          (runway < 0.1 ? '不到 1 个月' : runway.toFixed(1) + ' 个月') + '的日常支出。'
      });
    } else if (this.monthExpense <= 0) {
      findings.push({
        key: 'tip',
        text: '记几笔之后，我会算出你的流动资金能撑多久。'
      });
    }

    const color = score >= 85 ? '#57D9A3' : (score >= 70 ? '#3D8BFF' : '#FFB020');
    const text = score >= 85 ? '状态稳健' : (score >= 70 ? '整体健康' : '需要关注');

    this.setData({
      score,
      scoreText: text,
      scoreColor: color,
      findings: findings.slice(0, 3)
    });
  },

  goCategory() {
    wx.navigateTo({ url: '/pages/category/category' });
  }
});
