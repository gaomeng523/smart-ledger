/**
 * 资产账户本机存储（资产体检 / 设置页共用，保证账户数一致）
 */
const storage = require('./storage');

const STORE_KEY = 'assetAccounts';

/** 首次进入的预置账户，余额由用户手工填写 */
const DEFAULTS = [
  { id: 'a_cmb', name: '招商银行储蓄卡', kind: 'asset', liquid: false, amount: 0, icon: 'card' },
  { id: 'a_wechat', name: '微信零钱', kind: 'asset', liquid: true, amount: 0, icon: 'wallet' },
  { id: 'a_alipay', name: '支付宝余额', kind: 'asset', liquid: true, amount: 0, icon: 'wallet' },
  { id: 'a_credit', name: '信用卡待还', kind: 'debt', liquid: false, amount: 0, icon: 'card' }
];

function load() {
  const saved = storage.get(STORE_KEY, null);
  // 只有「从未保存过」才给预置账户；用户主动删空（[]）必须保持为空，
  // 否则删完再进页面会重新冒出默认账户
  return Array.isArray(saved) ? saved : DEFAULTS.slice();
}

function save(list) {
  storage.set(STORE_KEY, list);
}

module.exports = {
  STORE_KEY,
  DEFAULTS,
  load,
  save
};
