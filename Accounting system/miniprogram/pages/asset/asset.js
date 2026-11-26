/**
 * 资产体检（设计稿 04 屏）
 * 账户余额手动维护（仅存本机），体检结论由本地规则生成。
 */
const statApi = require('../../api/stat');
const assetAccounts = require('../../utils/asset-accounts');
const request = require('../../utils/request');
const format = require('../../utils/format');

/** 金额拆分为整数 / 小数（整数带千分位，小数两位） */
function moneyParts(value) {
  const text = format.formatMoney(Math.abs(Number(value) || 0));
  const seg = text.split('.');
  return { int: seg[0], dec: seg[1] || '00' };
}

Page({
  data: {
    accounts: [],
    assetText: '0.00',
    debtText: '0.00',
    netInt: '0',
    netDec: '00',
    netNegative: false,
    debtDanger: false,
    accountCount: 0,
    hasData: false,
    score: 0,
    scoreText: '',
    scoreColor: '#34D399',
    findings: []
  },

  monthExpense: 0,

  onShow() {
    this.loadAccounts();
    this.loadMonth();
  },

  loadAccounts() {
    this.render(assetAccounts.load());
  },

  render(accounts) {
    let asset = 0;
    let debt = 0;
    const rows = accounts.map((acc) => {
      const amount = Number(acc.amount) || 0;
      const isDebt = acc.kind === 'debt';
      if (isDebt) {
        debt += Math.abs(amount);
      } else {
        asset += amount;
      }
      const showDebt = isDebt && amount > 0;
      return Object.assign({}, acc, {
        isDebt,
        showDebt,
        amountText: (showDebt ? '-' : '') + '¥' + format.formatMoney(amount)
      });
    });

    const net = asset - debt;
    const netParts = moneyParts(net);

    this.accounts = accounts;
    this.assetTotal = asset;
    this.debtTotal = debt;
    this.setData({
      accounts: rows,
      accountCount: rows.length,
      assetText: format.formatMoney(asset),
      debtText: format.formatMoney(debt),
      debtDanger: debt > 0,
      netInt: netParts.int,
      netDec: netParts.dec,
      netNegative: net < 0,
      hasData: asset > 0 || debt > 0
    });
    this.buildCheckup(rows, asset, debt);
  },

  save(accounts) {
    assetAccounts.save(accounts);
    this.render(accounts);
  },

  loadMonth() {
    statApi.month(format.currentMonthStr(), { silent: true })
      .then((stat) => {
        this.monthExpense = Number(stat.expense) || 0;
        if (this.data.hasData) {
          this.buildCheckup(this.data.accounts, this.assetTotal, this.debtTotal);
        }
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
          icon: 'wallet'
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
      content: '删除后「' + account.name + '」的余额记录会一并清空，且无法恢复。',
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
    // 无数据（全部账户余额为 0）时不出评分与结论
    if (!(asset > 0 || debt > 0)) {
      this.setData({ hasData: false, score: 0, scoreText: '', findings: [] });
      return;
    }

    const liquid = rows
      .filter((r) => r.liquid && r.isDebt !== true)
      .reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
    const runway = this.monthExpense > 0 ? liquid / this.monthExpense : 0;
    const debtRatio = asset > 0 ? debt / asset : (debt > 0 ? 1 : 0);
    const net = asset - debt;

    let score = 55;
    if (net > 0) score += 15;
    if (debtRatio < 0.2) score += 10;
    if (runway >= 3) score += 10;
    if (rows.length >= 3) score += 5;
    score = Math.min(96, score);

    const findings = [];
    if (net > 0) {
      findings.push({
        key: 'net',
        text: '净资产为 ¥' + format.formatMoney(net) + '，整体为正。'
      });
    } else if (net === 0) {
      findings.push({
        key: 'net',
        text: '净资产为 ¥0.00，资产与负债持平。'
      });
    } else {
      findings.push({
        key: 'net',
        text: '净资产为负 ¥' + format.formatMoney(-net) + '，建议先还清高息负债。'
      });
    }

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

    // 高 = 绿，中 = 黄，低 = 红
    const color = score >= 85 ? '#34D399' : (score >= 70 ? '#F5B544' : '#FF6B6B');
    const text = score >= 85 ? '状态稳健' : (score >= 70 ? '整体健康' : '需要关注');

    this.setData({
      hasData: true,
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
