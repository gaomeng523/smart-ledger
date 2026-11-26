/**
 * 占比计算（全局唯一）
 * 最大余数法：把各项占比取整后，把剩余的 1% 依次补给小数部分最大的项，
 * 保证所有整数百分比之和正好等于 100。统计页与分类管理页共用。
 */
function splitPercent(values) {
  const nums = (values || []).map((v) => Math.max(0, Number(v) || 0));
  const total = nums.reduce((sum, v) => sum + v, 0);
  if (!(total > 0)) {
    return nums.map(() => 0);
  }

  const raw = nums.map((v) => (v / total) * 100);
  const result = raw.map((r) => Math.floor(r));
  let rest = 100 - result.reduce((sum, v) => sum + v, 0);

  const order = raw
    .map((r, index) => ({ index, frac: r - Math.floor(r) }))
    .sort((a, b) => (b.frac - a.frac) || (a.index - b.index));

  for (let i = 0; i < order.length && rest > 0; i += 1) {
    result[order[i].index] += 1;
    rest -= 1;
  }
  return result;
}

module.exports = {
  splitPercent
};
