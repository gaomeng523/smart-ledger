/**
 * P-09 别名词典页（V2，§4.9）
 * 关键词 → 映射目标（分类 / 渠道）；行内编辑、删除（二次确认）。
 */
const aliasApi = require('../../../api/alias');
const store = require('../../../store/index');
const request = require('../../../utils/request');
const { CODE } = require('../../../config/const');

/** 映射目标类型 */
const TARGET_TYPE = {
  CATEGORY: 1,
  CHANNEL: 2
};

/** request 层已自动 toast 的错误码，页面不再重复提示 */
const AUTO_TOASTED = [
  CODE.PARAM_ERROR,
  CODE.TOO_MANY_REQUESTS,
  CODE.SYSTEM_ERROR,
  CODE.UNAUTHORIZED,
  CODE.WX_LOGIN_FAILED,
  CODE.NETWORK_ERROR
];

function toastBizError(err, fallback) {
  const code = err && err.code;
  if (AUTO_TOASTED.indexOf(code) < 0) {
    request.toast((err && err.message) || fallback);
  }
}

Page({
  data: {
    // 列表
    aliases: [],
    firstLoading: true,
    empty: false,
    // 字典
    dict: null,
    // 新增 / 编辑弹窗
    showPanel: false,
    editing: false,
    editingId: '',
    formKeyword: '',
    formTargetType: TARGET_TYPE.CATEGORY,
    formTargetCode: '',
    formTargetName: '',
    saving: false
  },

  onLoad() {
    this.loadDict();
    this.loadList();
  },

  onPullDownRefresh() {
    this.loadList(true, () => wx.stopPullDownRefresh());
  },

  /* ---------- 数据加载 ---------- */

  loadDict() {
    const cached = store.getDictCache();
    if (cached) {
      this.setData({ dict: cached });
    }
    store.loadDict()
      .then((dict) => this.setData({ dict }))
      .catch(() => {});
  },

  loadList(refresh, callback) {
    if (!refresh) {
      this.setData({ firstLoading: true });
    }
    return aliasApi.list({ silent: true })
      .then((list) => {
        const aliases = list || [];
        this.setData({
          aliases,
          firstLoading: false,
          empty: aliases.length === 0
        });
        if (callback) callback();
      })
      .catch(() => {
        this.setData({
          firstLoading: false,
          empty: this.data.aliases.length === 0
        });
        request.toast('加载失败，请下拉/稍后重试');
        if (callback) callback();
      });
  },

  /* ---------- 删除 ---------- */

  onDelete(e) {
    const row = e.currentTarget.dataset.row;
    if (!row) {
      return;
    }
    wx.showModal({
      title: '删除别名',
      content: '删除后不可恢复，确定删除「' + (row.keyword || '') + '」？',
      confirmText: '删除',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          this.doRemove(row.aliasId);
        }
      }
    });
  },

  doRemove(aliasId) {
    aliasApi.remove(aliasId)
      .then(() => {
        wx.showToast({ title: '已删除', icon: 'success' });
        this.loadList(true);
      })
      .catch((err) => toastBizError(err, '删除失败，请稍后再试'));
  },

  /* ---------- 新增 / 编辑弹窗 ---------- */

  onNew() {
    this.setData({
      showPanel: true,
      editing: false,
      editingId: '',
      formKeyword: '',
      formTargetType: TARGET_TYPE.CATEGORY,
      formTargetCode: '',
      formTargetName: '',
      saving: false
    });
  },

  onEdit(e) {
    const row = e.currentTarget.dataset.row;
    if (!row) {
      return;
    }
    this.setData({
      showPanel: true,
      editing: true,
      editingId: row.aliasId,
      formKeyword: row.keyword || '',
      formTargetType: Number(row.targetType) === TARGET_TYPE.CHANNEL
        ? TARGET_TYPE.CHANNEL
        : TARGET_TYPE.CATEGORY,
      formTargetCode: row.targetCode || '',
      formTargetName: row.targetName || '',
      saving: false
    });
  },

  onPanelClose() {
    if (this.data.saving) {
      return;
    }
    this.setData({ showPanel: false });
  },

  onKeywordInput(e) {
    this.setData({ formKeyword: e.detail.value });
  },

  onTypeChange(e) {
    const targetType = Number(e.currentTarget.dataset.type);
    if (targetType === Number(this.data.formTargetType)) {
      return;
    }
    // 切换目标类型后原目标不再适用，清空待重选
    this.setData({
      formTargetType: targetType,
      formTargetCode: '',
      formTargetName: ''
    });
  },

  onTargetChange(e) {
    this.setData({
      formTargetCode: e.detail.code,
      formTargetName: e.detail.name
    });
  },

  onSave() {
    if (this.data.saving) {
      return;
    }
    const keyword = (this.data.formKeyword || '').trim();
    if (!keyword) {
      request.toast('请输入关键词');
      return;
    }
    if (keyword.length > 20) {
      request.toast('关键词不超过 20 字');
      return;
    }
    if (!this.data.formTargetCode) {
      request.toast('请选择映射目标');
      return;
    }

    const payload = {
      keyword,
      targetType: Number(this.data.formTargetType),
      targetCode: this.data.formTargetCode
    };
    const editing = this.data.editing;
    if (editing) {
      payload.aliasId = this.data.editingId;
    }

    this.setData({ saving: true });
    const action = editing ? aliasApi.update(payload) : aliasApi.create(payload);
    action
      .then(() => {
        this.setData({ saving: false, showPanel: false });
        wx.showToast({ title: editing ? '已保存' : '已添加', icon: 'success' });
        this.loadList(true);
      })
      .catch((err) => {
        this.setData({ saving: false });
        toastBizError(err, '保存失败，请稍后再试');
      });
  },

  noop() {}
});