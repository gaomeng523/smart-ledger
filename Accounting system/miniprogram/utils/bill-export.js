/**
 * 账单导出（CSV）：选择时间区间 → 下载 → 打开文件。
 * 抽屉菜单与「设置」页共用，避免两处维护同一段导出逻辑。
 */
const exportApi = require('../api/export');
const request = require('./request');
const format = require('./format');

const RANGES = ['本月', '近三个月', '今年以来', '近一年'];

function buildRange(index) {
  const now = new Date();
  const today = format.toDateStr(now);
  let startDate;
  if (index === 0) {
    startDate = format.toDateStr(new Date(now.getFullYear(), now.getMonth(), 1));
  } else if (index === 1) {
    startDate = format.toDateStr(new Date(now.getFullYear(), now.getMonth() - 2, 1));
  } else if (index === 2) {
    startDate = now.getFullYear() + '-01-01';
  } else if (index === 3) {
    startDate = format.toDateStr(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 365));
  } else {
    return null;
  }
  return { startDate, endDate: today };
}

function doExport(range) {
  if (!range) {
    return;
  }
  wx.showLoading({ title: '正在导出…', mask: true });
  exportApi.downloadBill(range)
    .then((res) => {
      wx.hideLoading();
      wx.openDocument({
        filePath: res.tempFilePath,
        showMenu: true,
        fail: () => {
          wx.showModal({
            title: '导出成功',
            content: 'CSV 已生成，可在真机上用 WPS / 表格打开，或转发到「文件传输助手」后用电脑查看。',
            showCancel: false,
            confirmText: '我知道了'
          });
        }
      });
    })
    .catch((err) => {
      wx.hideLoading();
      request.toast((err && err.message) || '导出失败，请稍后再试');
    });
}

/** 弹出区间选择并导出 */
function chooseRange() {
  wx.showActionSheet({
    itemList: RANGES,
    success: (res) => {
      doExport(buildRange(res.tapIndex));
    }
  });
}

module.exports = {
  RANGES,
  buildRange,
  doExport,
  chooseRange
};
