/**
 * 首页 · AI 对话式记账
 * 打开即对话：一句话 / 一张小票 → 解析卡片 → 存入账本。
 * 左缘右滑唤出「我的」抽屉。
 */
const billApi = require('../../api/bill');
const statApi = require('../../api/stat');
const templateApi = require('../../api/template');
const subscriptionApi = require('../../api/subscription');
const uploadApi = require('../../api/upload');
const store = require('../../store/index');
const request = require('../../utils/request');
const format = require('../../utils/format');
const auth = require('../../utils/auth');
const avatar = require('../../utils/avatar');
const { CODE, BILL_TYPE, SOURCE } = require('../../config/const');

/** 无模板时的默认快捷短语 */
const QUICK_DEFAULT = ['早餐 12', '打车 32', '咖啡 18', '房租 3200'];

/** 抽屉宽度（px，与设计稿一致） */
const DRAWER_W = 312;

/** 左缘触发区（px） */
const EDGE = 36;

Page({
  data: {
    // 对话
    greeting: '你好，今天也记一笔吧。',
    greetingTime: '',
    messages: [],
    scrollInto: '',
    text: '',
    canSend: false,
    parsing: false,
    saving: false,
    quickChips: QUICK_DEFAULT,

    // 解析结果卡
    draft: null,
    draftView: null,
    splitCount: 1,

    // 当月概览（卡片底部）
    monthBalanceText: '0.00',
    monthBillCount: 0,

    // 抽屉
    drawerOpen: false,
    dragging: false,
    drawerX: -DRAWER_W,
    scrimOpacity: 0,

    // 顶部品牌位：已设置头像时显示头像，否则显示「记」
    avatarPath: '',

    // 续费提醒
    renewText: '',

    // 字典
    dict: null,

    // 编辑 / 手动录入半屏
    showPreview: false,
    previewDraft: null,

    // 拍照
    pendingImagePath: ''
  },

  /** 幂等 requestId（同一草稿重试复用） */
  requestId: '',
  /** 原始输入文本，落库时作为 recordText */
  sourceText: '',
  /** 上传后的图片地址 */
  pendingImageUrl: '',
  /** 分类查找表 */
  catMap: null,

  onLoad() {
    // 抽屉宽度按屏幕换算（与 wxss 的 624rpx 保持一致），避免不同机型盖不严
    let drawerW = DRAWER_W;
    try {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      drawerW = Math.round((info.windowWidth || 375) * 624 / 750);
    } catch (e) {
      drawerW = DRAWER_W;
    }
    this.drawerW = drawerW;
    this.setData({ greetingTime: this.nowTime(), drawerX: -drawerW });
  },

  onShow() {
    if (!auth.guard()) {
      return;
    }
    this.loadDict();
    this.loadStat();
    this.loadGreeting();
    this.loadTemplates();
    this.loadRenewReminders();
    this.setData({ avatarPath: avatar.read() });
    const menu = this.selectComponent('#menu');
    if (menu) {
      menu.refreshProfile();
    }
  },

  onUnload() {
    if (this._scrollTimer) {
      clearTimeout(this._scrollTimer);
      this._scrollTimer = null;
    }
  },

  /* ---------- 数据加载 ---------- */

  loadDict() {
    const cached = store.getDictCache();
    if (cached) {
      this.setData({ dict: cached });
      this.buildCatMap(cached);
    }
    store.loadDict()
      .then((dict) => {
        this.setData({ dict });
        this.buildCatMap(dict);
      })
      .catch(() => {});
  },

  buildCatMap(dict) {
    const map = {};
    ((dict && dict.categoryTree) || []).forEach((parent) => {
      map[parent.code] = { name: parent.name, parentName: '' };
      (parent.children || []).forEach((child) => {
        map[child.code] = { name: child.name, parentName: parent.name };
      });
    });
    this.catMap = map;
  },

  loadStat() {
    const month = format.currentMonthStr();
    statApi.month(month, { silent: true })
      .then((stat) => {
        store.setMonthStat(stat);
        this.setData({
          monthBalanceText: format.formatMoney(stat && stat.balance),
          monthBillCount: (stat && stat.billCount) || 0
        });
      })
      .catch(() => {});
  },

  /** 问候语：今天记了几笔、共多少 */
  loadGreeting() {
    const today = format.todayStr();
    billApi.list({ page: 1, size: 50, month: format.currentMonthStr() }, { silent: true })
      .then((page) => {
        const todayBills = ((page && page.records) || []).filter((b) => b.recordDate === today);
        const total = todayBills.reduce((sum, b) => sum + (Number(b.amount) || 0), 0);
        const head = this.greetingPrefix();
        const greeting = todayBills.length
          ? head + '今天记了 ' + todayBills.length + ' 笔，共 ¥' + format.formatMoney(total) + '。想记点什么？'
          : head + '今天还没有记账，说一句就开始。';
        this.setData({ greeting });
      })
      .catch(() => {});
  },

  greetingPrefix() {
    const hour = new Date().getHours();
    if (hour < 6) return '夜深了，';
    if (hour < 11) return '早上好，';
    if (hour < 14) return '中午好，';
    if (hour < 18) return '下午好，';
    return '晚上好，';
  },

  loadTemplates() {
    templateApi.list({ silent: true })
      .then((list) => {
        const chips = (list || []).slice(0, 4)
          .filter((item) => item.title)
          .map((item) => {
            const amount = Number(item.amount);
            return item.title + (amount > 0 ? ' ' + format.formatMoney(amount) : '');
          });
        this.setData({ quickChips: chips.length ? chips : QUICK_DEFAULT });
      })
      .catch(() => this.setData({ quickChips: QUICK_DEFAULT }));
  },

  loadRenewReminders() {
    subscriptionApi.list({ silent: true })
      .then((list) => {
        const next = (list || [])
          .filter((s) => Number(s.status) === 1 && (s.dueState === 'overdue' || s.dueState === 'today' || s.dueState === 'soon'))
          .sort((a, b) => Number(a.daysToNext) - Number(b.daysToNext))[0];
        if (!next) {
          this.setData({ renewText: '' });
          return;
        }
        const days = Number(next.daysToNext) || 0;
        const when = days < 0 ? '已逾期 ' + (-days) + ' 天'
          : (days === 0 ? '今天自动扣费' : days + ' 天后自动扣费');
        this.setData({ renewText: next.title + ' ' + when + ' · ¥' + format.formatMoney(next.avgAmount) });
      })
      .catch(() => {});
  },

  nowTime() {
    const d = new Date();
    return format.pad2(d.getHours()) + ':' + format.pad2(d.getMinutes());
  },

  /* ---------- 输入 ---------- */

  onInput(e) {
    const text = e.detail.value || '';
    this.setData({ text, canSend: !!text.trim() });
  },

  onChipTap(e) {
    const text = e.currentTarget.dataset.text;
    if (!text || this.data.parsing) {
      return;
    }
    this.setData({ text });
    this.send(text);
  },

  onSend() {
    const text = (this.data.text || '').trim();
    if (!text) {
      request.toast('说一句，或粘贴账单');
      return;
    }
    this.send(text);
  },

  send(text) {
    if (this.data.parsing) {
      return;
    }
    this.sourceText = text;
    this.appendMessage(text);
    this.setData({ text: '', canSend: false, parsing: true, draft: null, draftView: null, splitCount: 1 });
    this.scrollToEnd();

    billApi.parse(text, { silent: true })
      .then((draft) => {
        this.requestId = format.uuid();
        this.applyDraft(draft);
      })
      .catch((err) => {
        this.setData({ parsing: false });
        this.handleParseError(err);
      });
  },

  applyDraft(draft) {
    const view = this.buildDraftView(draft);
    this.setData({
      parsing: false,
      draft,
      draftView: view,
      splitCount: 1
    });
    this.scrollToEnd();
  },

  /** ParseResultDTO → 卡片展示字段 */
  buildDraftView(draft) {
    const d = draft || {};
    const amountText = format.formatMoney(d.amount);
    const parts = amountText.split('.');
    const missing = d.missingFields || [];
    const confidence = d.confidence === null || d.confidence === undefined
      ? null
      : Number(d.confidence);

    return {
      amountText,
      intPart: parts[0],
      decPart: parts[1] || '00',
      isIncome: Number(d.billType) === BILL_TYPE.INCOME,
      confidenceText: confidence === null ? '—' : Math.round(confidence * 100) + '%',
      confidenceLow: confidence !== null && confidence < 0.5,
      categoryLabel: this.categoryLabel(d.categoryCode, d.categoryName),
      missingCategory: missing.indexOf('categoryCode') >= 0 || !d.categoryCode,
      missingAmount: missing.indexOf('amount') >= 0 || d.amount === null || d.amount === undefined,
      missingChannel: missing.indexOf('payChannelCode') >= 0 || !d.payChannelCode,
      channelName: d.payChannelName || '未识别',
      dateText: this.dateText(d.recordDate),
      remarkText: d.remark || '',
      splitText: ''
    };
  },

  categoryLabel(code, fallbackName) {
    const hit = code && this.catMap ? this.catMap[code] : null;
    if (hit) {
      return hit.parentName ? hit.parentName + ' · ' + hit.name : hit.name;
    }
    return fallbackName || '未识别';
  },

  dateText(dateStr) {
    const today = format.todayStr();
    if (!dateStr) {
      return '今天';
    }
    if (dateStr === today) {
      return '今天';
    }
    const y = new Date();
    y.setDate(y.getDate() - 1);
    if (dateStr === format.toDateStr(y)) {
      return '昨天';
    }
    const p = dateStr.split('-');
    return Number(p[1]) + '月' + Number(p[2]) + '日';
  },

  handleParseError(err) {
    const code = err && err.code;
    const draft = (err && err.data) || {};

    if (code === CODE.AI_PARSE_FAILED ||
        code === CODE.AI_OUTPUT_INVALID ||
        code === CODE.AMOUNT_UNRECOGNIZED) {
      this.appendAi('这次没完全听懂，帮你打开手动确认，改一下就好。');
      this.openPreview(draft);
      return;
    }

    if (code === CODE.PARSE_TOO_FREQUENT) {
      this.appendAi('说得有点快，等我几秒再试一次～');
      return;
    }

    if (code === CODE.NETWORK_ERROR) {
      this.appendAi('网络好像断了，检查一下再发给我？');
      return;
    }

    this.appendAi((err && err.message) || '这次没解析出来，换个说法试试？');
  },

  /* ---------- 对话流 ---------- */

  appendMessage(text) {
    const messages = this.data.messages.concat({
      id: 'm' + Date.now() + Math.floor(Math.random() * 1000),
      text,
      time: this.nowTime()
    });
    this.setData({ messages });
  },

  appendAi(text) {
    this.setData({
      messages: this.data.messages.concat({
        id: 'a' + Date.now(),
        ai: true,
        text,
        time: this.nowTime()
      })
    });
    this.scrollToEnd();
  },

  scrollToEnd() {
    if (this._scrollTimer) {
      clearTimeout(this._scrollTimer);
    }
    this.setData({ scrollInto: '' });
    this._scrollTimer = setTimeout(() => {
      this._scrollTimer = null;
      this.setData({ scrollInto: 'chat-end' });
    }, 60);
  },

  /* ---------- 解析卡片 ---------- */

  onEditDraft() {
    if (!this.data.draft) {
      return;
    }
    this.openPreview(this.data.draft);
  },

  onSplitTap() {
    if (!this.data.draft) {
      return;
    }
    wx.showModal({
      title: '分账人数',
      editable: true,
      placeholderText: '几个人一起付？',
      content: '',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        const n = Math.round(Number(res.content));
        if (!isFinite(n) || n < 1 || n > 20) {
          request.toast('请输入 1~20 人');
          return;
        }
        this.applySplit(n);
      }
    });
  },

  applySplit(n) {
    const view = Object.assign({}, this.data.draftView);
    if (n <= 1) {
      view.splitText = '';
      this.setData({ splitCount: 1, draftView: view });
      return;
    }
    const per = (Number(this.data.draft && this.data.draft.amount) || 0) / n;
    view.splitText = n + ' 人 · 每人 ' + format.formatMoney(per);
    this.setData({ splitCount: n, draftView: view });
  },

  onRetryParse() {
    const text = this.sourceText || '';
    this.setData({ draft: null, draftView: null, splitCount: 1, text, canSend: !!text });
    request.toast('改一下说法，再发一次');
  },

  onSaveDraft() {
    if (this.data.saving || !this.data.draft) {
      return;
    }
    const error = this.validate(this.data.draft);
    if (error) {
      request.toast(error);
      this.openPreview(this.data.draft);
      return;
    }
    this.doSave(this.buildPayload(this.data.draft, {
      requestId: this.requestId || format.uuid(),
      source: SOURCE.TEXT,
      confidence: this.data.draft.confidence
    }));
  },

  /* ---------- 半屏编辑 / 手动录入 ---------- */

  openPreview(draft) {
    const d = draft || {};
    this.setData({
      showPreview: true,
      previewDraft: {
        amount: d.amount === undefined ? null : d.amount,
        billType: d.billType === undefined || d.billType === null ? BILL_TYPE.EXPENSE : d.billType,
        payChannelCode: d.payChannelCode || '',
        payChannelName: d.payChannelName || '',
        categoryCode: d.categoryCode || '',
        categoryName: d.categoryName || '',
        recordDate: d.recordDate || format.todayStr(),
        remark: d.remark || '',
        tags: d.tags || [],
        confidence: d.confidence === undefined ? null : d.confidence,
        missingFields: d.missingFields || []
      }
    });
  },

  onPreviewCancel() {
    this.setData({ showPreview: false, previewDraft: null });
  },

  onPreviewConfirm(e) {
    const form = (e.detail && e.detail.draft) || {};
    const source = this.data.draft ? SOURCE.TEXT : SOURCE.MANUAL;
    this.doSave(this.buildPayload(form, {
      requestId: this.requestId || format.uuid(),
      source,
      confidence: this.data.draft ? this.data.draft.confidence : null
    }));
  },

  /* ---------- 保存 ---------- */

  buildPayload(form, options) {
    const remark = this.buildRemark(form.remark);
    return {
      requestId: options.requestId,
      amount: Number(form.amount),
      billType: Number(form.billType),
      payChannelCode: form.payChannelCode,
      categoryCode: form.categoryCode,
      recordDate: form.recordDate,
      remark,
      tags: (form.tags || []).slice(),
      recordText: this.sourceText || '',
      source: this.pendingImageUrl ? SOURCE.OCR : options.source,
      confidence: options.confidence === undefined ? null : options.confidence,
      imageUrl: this.pendingImageUrl || '',
      force: false
    };
  },

  buildRemark(remark) {
    const base = remark || '';
    if (this.data.splitCount > 1) {
      return (base ? base + ' · ' : '') + '分账 ' + this.data.splitCount + ' 人';
    }
    return base;
  },

  doSave(payload) {
    this.setData({ saving: true });

    billApi.save(payload, { silent: true })
      .then((res) => {
        this.setData({ saving: false });
        this.onSaveSuccess(res, payload);
      })
      .catch((err) => {
        this.setData({ saving: false });
        this.handleSaveError(err, payload);
      });
  },

  handleSaveError(err, payload) {
    const code = err && err.code;

    if (code === CODE.DUPLICATE_BILL) {
      wx.showModal({
        title: '这笔好像记过了',
        content: this.duplicateSummary(err),
        confirmText: '仍要记账',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) {
            this.doSave(Object.assign({}, payload, { force: true, requestId: format.uuid() }));
          }
        }
      });
      return;
    }

    if (code !== CODE.NETWORK_ERROR) {
      request.toast((err && err.message) || '保存失败，请稍后再试');
    }
  },

  duplicateSummary(err) {
    const candidates = (err && err.data && err.data.candidates) || [];
    if (!candidates.length) {
      return '有一笔相同的记录，仍要记账吗？';
    }
    const lines = candidates.slice(0, 3).map((item) => {
      return (item.recordDate || '') + '  ¥' + format.formatMoney(item.amount) + '  ' + (item.categoryName || '未分类');
    });
    return '疑似已有：\n' + lines.join('\n') + '\n仍要记账吗？';
  },

  onSaveSuccess(res, payload) {
    const label = this.categoryLabel(payload.categoryCode, '');
    const sign = Number(payload.billType) === BILL_TYPE.INCOME ? '+' : '-';
    this.appendAi('已存入账本：' + label + ' ' + sign + '¥' + format.formatMoney(payload.amount));

    const insights = Array.isArray(res && res.insights) ? res.insights : [];
    insights.slice(0, 2).forEach((it) => {
      if (it && it.title) {
        this.appendAi(it.title + '：' + (it.body || ''));
      }
    });

    this.requestId = '';
    this.sourceText = '';
    this.pendingImageUrl = '';
    this.setData({
      draft: null,
      draftView: null,
      splitCount: 1,
      showPreview: false,
      previewDraft: null,
      pendingImagePath: ''
    });

    this.loadStat();
    this.loadGreeting();
    this.loadTemplates();
  },

  validate(form) {
    const amountText = String(form.amount === undefined || form.amount === null ? '' : form.amount).trim();
    if (!amountText || !/^\d+(\.\d{1,2})?$/.test(amountText) || Number(amountText) <= 0) {
      return '请补充金额';
    }
    if (!form.payChannelCode) {
      return '请选择支付渠道';
    }
    if (!form.categoryCode) {
      return '请选择分类';
    }
    return '';
  },

  /* ---------- 拍照 ---------- */

  onPhoto() {
    if (this.data.parsing || this.data.saving) {
      return;
    }
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera', 'album'],
      sizeType: ['compressed'],
      success: (res) => {
        const file = (res.tempFiles || [])[0];
        if (!file || !file.tempFilePath) {
          return;
        }
        if (file.size && file.size > 5 * 1024 * 1024) {
          request.toast('图片不能超过 5MB');
          return;
        }
        this.uploadPhoto(file.tempFilePath);
      }
    });
  },

  uploadPhoto(filePath) {
    wx.showLoading({ title: '上传中…', mask: true });
    uploadApi.uploadImage(filePath)
      .then((res) => {
        wx.hideLoading();
        this.pendingImageUrl = (res && res.url) || '';
        this.setData({ pendingImagePath: filePath });
        this.appendAi('小票收到，补充一句「买了什么、多少钱」，我就帮你记好。');
      })
      .catch((err) => {
        wx.hideLoading();
        request.toast((err && err.message) || '上传失败，请重试');
      });
  },

  onPhotoRemove() {
    this.pendingImageUrl = '';
    this.setData({ pendingImagePath: '' });
  },

  onVoice() {
    request.toast('语音记账即将上线');
  },

  /* ---------- 抽屉 ---------- */

  onAvatarTap() {
    this.openDrawer();
  },

  openDrawer() {
    this.setData({ drawerOpen: true, dragging: false, drawerX: 0, scrimOpacity: 0.5 });
  },

  closeDrawer() {
    this.setData({ drawerOpen: false, dragging: false, drawerX: -this.getDrawerW(), scrimOpacity: 0 });
  },

  getDrawerW() {
    return this.drawerW || DRAWER_W;
  },

  onDrawerNavigate() {
    this.closeDrawer();
  },

  onTouchStart(e) {
    const t = e.touches && e.touches[0];
    if (!t) {
      return;
    }
    this._start = { x: t.clientX, y: t.clientY };
    this._dragAllowed = this.data.drawerOpen || t.clientX <= EDGE;
    this._dragging = false;
  },

  onTouchMove(e) {
    if (!this._dragAllowed) {
      return;
    }
    const t = e.touches && e.touches[0];
    if (!t) {
      return;
    }
    const dx = t.clientX - this._start.x;
    const dy = t.clientY - this._start.y;

    if (!this._dragging) {
      // 横向意图才接管手势，避免抢掉页面纵向滚动
      if (Math.abs(dx) < 8 || Math.abs(dx) < Math.abs(dy)) {
        return;
      }
      this._dragging = true;
      this.setData({ dragging: true });
    }

    const W = this.getDrawerW();
    const base = this.data.drawerOpen ? 0 : -W;
    const x = Math.max(-W, Math.min(0, base + dx));
    this.setData({
      drawerX: x,
      scrimOpacity: (1 + x / W) * 0.5
    });
  },

  onTouchEnd() {
    if (!this._dragAllowed) {
      return;
    }
    if (!this._dragging) {
      this._dragAllowed = false;
      return;
    }
    const W = this.getDrawerW();
    const open = this.data.drawerX > -W * 0.68;
    this.setData({
      drawerOpen: open,
      dragging: false,
      drawerX: open ? 0 : -W,
      scrimOpacity: open ? 0.5 : 0
    });
    this._dragAllowed = false;
    this._dragging = false;
  },

  goRenew() {
    wx.navigateTo({ url: '/pages/subscription/subscription/subscription' });
  },

  noop() {}
});
