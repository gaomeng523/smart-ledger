/**
 * P-08 导入账单（V2，§4.8）
 * 流程：选 CSV（chooseMessageFile）→ 试算 dryRun=true 预览 → 确认导入 dryRun=false → 可撤销
 * 导入账单 source=4，由后端处理，前端不传。
 */
const importApi = require('../../../api/import');
const request = require('../../../utils/request');
const { CODE } = require('../../../config/const');

/** 文件大小上限 5MB（§4.8-1） */
const MAX_SIZE = 5 * 1024 * 1024;

Page({
  data: {
    sourceType: 'WECHAT',
    sources: [
      { value: 'WECHAT', label: '微信支付' },
      { value: 'ALIPAY', label: '支付宝' }
    ],

    file: null,
    previewing: false,
    result: null,
    showFailDetail: false,

    importing: false,
    importedId: null,
    rollingBack: false,

    showFormatTip: false,
    formatExpanded: false
  },

  /* ---------- ① 来源 ---------- */

  onSourceTap(e) {
    const value = e.currentTarget.dataset.value;
    if (value === this.data.sourceType || this.data.importedId) {
      return;
    }
    this.setData({ sourceType: value, showFormatTip: false });
    // 已选文件时，切换来源需按新来源重新试算
    if (this.data.file) {
      this.doPreview();
    }
  },

  /* ---------- ② 文件 ---------- */

  onChooseFile() {
    if (this.data.previewing || this.data.importing) {
      return;
    }
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['csv'],
      success: (res) => {
        const f = res && res.tempFiles && res.tempFiles[0];
        if (!f) {
          return;
        }
        if (Number(f.size) > MAX_SIZE) {
          request.toast('文件不能超过 5MB');
          return;
        }
        const sizeKb = Math.max(1, Math.round(Number(f.size || 0) / 1024));
        this.setData({
          file: { path: f.path, name: f.name || '账单.csv', sizeText: sizeKb + ' KB' },
          result: null,
          importedId: null,
          showFailDetail: false,
          showFormatTip: false,
          formatExpanded: false
        });
        // 选完立即试算（不入库）
        this.doPreview();
      },
      fail: (err) => {
        const msg = (err && err.errMsg) || '';
        if (msg.indexOf('cancel') > -1) {
          return;
        }
        request.toast('选择文件失败，请重试');
      }
    });
  },

  /* ---------- ③ 预览（dryRun=true） ---------- */

  doPreview() {
    if (!this.data.file) {
      return;
    }
    this.setData({ previewing: true, showFailDetail: false });
    importApi.preview(this.data.file.path, this.data.sourceType, { silent: true })
      .then((res) => {
        this.setData({ previewing: false, result: res, showFailDetail: false });
      })
      .catch((err) => {
        this.setData({ previewing: false, result: null });
        this.handleError(err);
      });
  },

  /* ---------- 确认导入（dryRun=false） ---------- */

  onConfirm() {
    if (this.data.importing || this.data.previewing || !this.data.file || !this.data.result) {
      return;
    }
    this.setData({ importing: true });
    importApi.confirm(this.data.file.path, this.data.sourceType, { silent: true })
      .then((res) => {
        this.setData({
          importing: false,
          result: res,
          importedId: (res && res.importId) || null,
          showFailDetail: false
        });
        wx.showToast({
          title: '成功导入 ' + ((res && res.successCount) || 0) + ' 条',
          icon: 'success'
        });
      })
      .catch((err) => {
        this.setData({ importing: false });
        this.handleError(err);
      });
  },

  /* ---------- 撤销本次导入（二次确认） ---------- */

  onRollback() {
    if (this.data.rollingBack || !this.data.importedId) {
      return;
    }
    wx.showModal({
      title: '撤销导入',
      content: '撤销后将删除本次导入的全部账单，确定撤销？',
      confirmText: '撤销',
      confirmColor: '#E8454F',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        this.setData({ rollingBack: true });
        importApi.rollback(this.data.importedId)
          .then(() => {
            this.setData({ rollingBack: false });
            wx.showToast({ title: '已撤销', icon: 'success' });
            this.reset();
          })
          .catch((err) => {
            this.setData({ rollingBack: false });
            this.toastIfNeeded(err);
          });
      }
    });
  },

  /* ---------- 错误处理（§4.8-4、§6.2） ---------- */

  /** preview/confirm 走 {silent:true}，由页面提示；1006 走顶部提示条 */
  handleError(err) {
    const code = err && err.code;
    if (code === CODE.IMPORT_FORMAT_ERROR) {
      this.setData({ showFormatTip: true, formatExpanded: false });
      return;
    }
    request.toast((err && err.message) || '导入失败，请稍后再试');
  },

  /** rollback 非 silent，通用错误已由 request 层提示，仅补提示剩余错误码 */
  toastIfNeeded(err) {
    const code = err && err.code;
    if (code === CODE.PARAM_ERROR ||
        code === CODE.SYSTEM_ERROR ||
        code === CODE.TOO_MANY_REQUESTS ||
        code === CODE.NETWORK_ERROR ||
        code === CODE.WX_LOGIN_FAILED) {
      return;
    }
    request.toast((err && err.message) || '撤销失败，请稍后再试');
  },

  /* ---------- 交互 ---------- */

  toggleFailDetail() {
    this.setData({ showFailDetail: !this.data.showFailDetail });
  },

  toggleFormat() {
    this.setData({ formatExpanded: !this.data.formatExpanded });
  },

  reset() {
    this.setData({
      file: null,
      previewing: false,
      result: null,
      showFailDetail: false,
      importing: false,
      importedId: null,
      rollingBack: false,
      showFormatTip: false,
      formatExpanded: false
    });
  }
});