/**
 * 格式化工具：金额、日期、月份、UUID（§2.3：金额等宽对齐）
 */
const { BILL_TYPE } = require('../config/const');

function pad2(n) {
  return n < 10 ? '0' + n : '' + n;
}

/** 千分位 + 两位小数，不带符号：1234.5 → "1,234.50" */
function formatMoney(value) {
  const n = Number(value);
  if (!isFinite(n)) return '0.00';
  const fixed = Math.abs(n).toFixed(2);
  const parts = fixed.split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return parts.join('.');
}

/** 带符号金额：支出 "-100.00" / 收入 "+100.00" */
function signedAmount(amount, billType) {
  const sign = Number(billType) === BILL_TYPE.INCOME ? '+' : '-';
  return sign + formatMoney(amount);
}

/** Date 或 yyyy-MM-dd 字符串 → yyyy-MM-dd */
function toDateStr(date) {
  if (date instanceof Date) {
    return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate());
  }
  return date || '';
}

/** 今天 yyyy-MM-dd */
function todayStr() {
  return toDateStr(new Date());
}

/** Date 或 yyyy-MM-dd → yyyy-MM（当月） */
function toMonthStr(date) {
  if (date instanceof Date) {
    return date.getFullYear() + '-' + pad2(date.getMonth() + 1);
  }
  return (date || '').slice(0, 7);
}

/** 当前月 yyyy-MM */
function currentMonthStr() {
  return toMonthStr(new Date());
}

/** 近 n 个月（含当月），倒序：['2026-10','2026-09',...] */
function recentMonths(n) {
  const list = [];
  const now = new Date();
  for (let i = 0; i < n; i++) {
    list.push(toMonthStr(new Date(now.getFullYear(), now.getMonth() - i, 1)));
  }
  return list;
}

/** '2026-10' → '2026年10月' */
function monthToText(month) {
  if (!month) return '';
  const parts = month.split('-');
  return parts[0] + '年' + Number(parts[1]) + '月';
}

/** '2026-10-04' → '10-04' */
function shortDate(dateStr) {
  return (dateStr || '').slice(5);
}

/** '2026-10-04' → '周六' */
function weekdayText(dateStr) {
  if (!dateStr) return '';
  const parts = dateStr.split('-');
  const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  if (isNaN(d.getTime())) return '';
  return '周' + '日一二三四五六'.charAt(d.getDay());
}

/** 金额/占比 → 百分比文本：0.3692 → "36.92%" */
function ratioToPercent(ratio, digits) {
  const n = Number(ratio);
  if (!isFinite(n)) return '0%';
  return (n * 100).toFixed(digits === undefined ? 2 : digits) + '%';
}

/** 简易 UUID v4 */
function uuid() {
  let t = Date.now();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (t + Math.random() * 16) % 16 | 0;
    t = Math.floor(t / 16);
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

/** 文本截断（列表备注用） */
function truncate(text, max) {
  const s = text || '';
  const limit = max || 20;
  return s.length > limit ? s.slice(0, limit) + '…' : s;
}

/** 取名称首字（分类圆标兜底） */
function firstChar(name) {
  return name ? String(name).charAt(0) : '记';
}

module.exports = {
  pad2,
  formatMoney,
  signedAmount,
  toDateStr,
  todayStr,
  toMonthStr,
  currentMonthStr,
  recentMonths,
  monthToText,
  shortDate,
  weekdayText,
  ratioToPercent,
  uuid,
  truncate,
  firstChar
};