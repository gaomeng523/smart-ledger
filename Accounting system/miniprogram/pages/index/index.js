/**
 * 首页 · 对话式记账
 * 打开即对话：一句话 / 一张小票 → 解析卡片 → 存入账本。
 * 左缘右滑唤出「我的」抽屉。
 */
const billApi = require('../../api/bill');
const statApi = require('../../api/stat');
const templateApi = require('../../api/template');
const subscriptionApi = require('../../api/subscription');
const store = require('../../store/index');
const request = require('../../utils/request');
const format = require('../../utils/format');
const palette = require('../../utils/palette');
const auth = require('../../utils/auth');
const avatar = require('../../utils/avatar');
const { CODE, BILL_TYPE, SOURCE } = require('../../config/const');

/** 无模板时的默认快捷短语 */
const QUICK_DEFAULT = ['早餐 12', '打车 32', '咖啡 18', '房租 3200'];

/** 快捷短语 → 带分类色圆点的展示结构 */
function toChips(list) {
  return (list || []).map((text) => ({
    text,
    color: palette.colorFor(String(text).split(' ')[0])
  }));
}

/** 一句话里多笔消费的切分符：中英文逗号、顿号、分号、换行 */
const SPLIT_RE = /[，,、;；\n]+/;
/** 片段内是否含金额 */
const AMOUNT_RE = /\d+(?:\.\d+)?/;
/** 整段只有一个金额：「3100」「¥3100」「3100元」 */
const ONLY_AMOUNT_RE = /^[¥￥]?\s*\d+(?:\.\d+)?\s*(?:元|块钱|块)?$/;
/** 一次最多识别的笔数（后端每分钟解析上限 20 次，留出余量） */
const MAX_MULTI = 8;

/**
 * 「买衣服花 3100，车票花 398」→ ['买衣服花 3100', '车票花 398']
 * 只有带金额的片段才算一笔；「买衣服，3100」这种金额单独成段的并回前一段。
 */
function splitBillText(text) {
  const raw = String(text || '')
    .split(SPLIT_RE)
    .map((s) => s.trim())
    .filter((s) => !!s);

  const merged = [];
  raw.forEach((segment) => {
    const prev = merged[merged.length - 1];
    if (prev && ONLY_AMOUNT_RE.test(segment)) {
      merged[merged.length - 1] = prev + ' ' + segment;
    } else {
      merged.push(segment);
    }
  });

  return merged.filter((s) => AMOUNT_RE.test(s));
}

/** 兜底取片段里最后一个金额（口语里金额通常在句尾） */
function pickAmount(text) {
  const list = String(text || '').match(/\d+(?:\.\d+)?/g);
  return list && list.length ? Number(list[list.length - 1]) : null;
}

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
    quickChips: toChips(QUICK_DEFAULT),

    // 解析结果卡
    draft: null,
    draftView: null,
    splitCount: 1,

    // 多笔汇总卡
    draftViews: [],
    multiTotalText: '',

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

    // 游客态（未登录）：可以浏览，写入数据时再登录
    guest: false,

    // 续费提醒
    renewText: '',

    // 字典
    dict: null,

    // 编辑 / 手动录入半屏
    showPreview: false,
    previewDraft: null,

    // 拍照
    pendingImagePath: '',

    // 朋友圈「单页模式」打开（未登录，仅展示介绍）
    singlePage: false
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

    // 朋友圈分享打开时是「单页模式」（scene=1154）：无登录能力，展示介绍页
    let scene = 0;
    try {
      scene = (wx.getLaunchOptionsSync() || {}).scene || 0;
    } catch (e) {
      scene = 0;
    }
    this.setData({ singlePage: scene === 1154 });

    // 同时开启「转发给朋友」和「分享到朋友圈」
    if (wx.showShareMenu) {
      wx.showShareMenu({
        withShareTicket: false,
        menus: ['shareAppMessage', 'shareTimeline']
      });
    }
  },

  /** 转发给朋友 */
  onShareAppMessage() {
    return {
      title: '萌萌记账｜说一句话就记好账',
      path: '/pages/index/index'
    };
  },

  /** 分享到朋友圈（打开后为单页模式，不依赖登录） */
  onShareTimeline() {
    return {
      title: '萌萌记账｜说一句话、拍张小票，就能记好账'
    };
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(0);
    }

    // 设置页重置数据后回到首页：清空本会话的对话与待存草稿
    const app = getApp();
    if (app && app.globalData && app.globalData.resetAt) {
      app.globalData.resetAt = 0;
      this.clearDraftState();
      this.setData({ messages: [], text: '', canSend: false });
    }

    // 朋友圈单页模式：不请求任何需要登录的数据
    if (this.data.singlePage) {
      this.setData({ guest: true, avatarPath: '' });
      return;
    }

    // 游客态：正常展示对话界面，保存/拍照等动作时再引导登录
    if (!auth.guard()) {
      this.setData({ guest: true, avatarPath: '' });
      const menu = this.selectComponent('#menu');
      if (menu) {
        menu.refreshProfile();
      }
      return;
    }
    this.setData({ guest: false });
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
        this.setData({ quickChips: chips.length ? toChips(chips) : toChips(QUICK_DEFAULT) });
      })
      .catch(() => this.setData({ quickChips: toChips(QUICK_DEFAULT) }));
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
    if (auth.requireLogin()) {
      return;
    }
    this.sourceText = text;
    this.appendMessage(text);
    this.multiDrafts = null;
    this.editIndex = -1;
    this.setData({
      text: '',
      canSend: false,
      parsing: true,
      draft: null,
      draftView: null,
      draftViews: [],
      multiTotalText: '',
      splitCount: 1
    });
    this.scrollToEnd();

    // 一句话里有多笔消费（逗号/顿号/分号/换行分隔，且每段都带金额）→ 汇总卡
    const segments = splitBillText(text);
    if (segments.length >= 2) {
      if (segments.length > MAX_MULTI) {
        request.toast('一次最多记 ' + MAX_MULTI + ' 笔，先处理前 ' + MAX_MULTI + ' 笔');
      }
      this.parseMulti(segments.slice(0, MAX_MULTI));
      return;
    }

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

  /** 多笔：逐段并行解析，聚合成汇总卡 */
  parseMulti(segments) {
    const tasks = segments.map((segment) => billApi.parse(segment, { silent: true })
      .catch((err) => {
        const data = (err && err.data) || {};
        if (data.amount !== null && data.amount !== undefined) {
          return data;
        }
        // 单段解析失败也不丢：用本地金额兜底，让用户补分类/账户
        const amount = pickAmount(segment);
        return amount === null ? null : {
          amount,
          billType: BILL_TYPE.EXPENSE,
          recordDate: format.todayStr(),
          missingFields: ['categoryCode', 'payChannelCode']
        };
      }));

    Promise.all(tasks).then((list) => {
      const drafts = [];
      list.forEach((draft, i) => {
        if (draft) {
          drafts.push(Object.assign({}, draft, { segmentText: segments[i] }));
        }
      });

      if (!drafts.length) {
        this.setData({ parsing: false });
        this.handleParseError({ code: CODE.AMOUNT_UNRECOGNIZED });
        return;
      }
      if (drafts.length === 1) {
        this.applyDraft(drafts[0]);
        return;
      }
      this.applyMulti(drafts);
    });
  },

  applyMulti(drafts) {
    this.multiDrafts = drafts;
    this.editIndex = -1;
    this.setData({
      parsing: false,
      draft: null,
      draftView: null,
      splitCount: 1,
      draftViews: this.buildMultiViews(drafts),
      multiTotalText: this.multiTotalTextOf(drafts)
    });
    this.scrollToEnd();
  },

  /** 多笔列表 → 卡片行数据 */
  buildMultiViews(drafts) {
    return (drafts || []).map((draft, index) => {
      const view = this.buildDraftView(draft);
      view.key = 'mv' + index;
      view.sign = view.isIncome ? '+' : '-';
      view.subText = (view.missingChannel ? '账户待确认' : view.channelName) + ' · ' + view.dateText;
      view.warn = view.missingCategory || view.missingAmount || view.missingChannel;
      return view;
    });
  },

  /** 多笔合计（支出记负、收入记正） */
  multiTotalTextOf(drafts) {
    let net = 0;
    (drafts || []).forEach((draft) => {
      const amount = Number(draft.amount) || 0;
      net += Number(draft.billType) === BILL_TYPE.INCOME ? amount : -amount;
    });
    return (net >= 0 ? '+' : '-') + '¥' + format.formatMoney(Math.abs(net));
  },

  applyDraft(draft) {
    const view = this.buildDraftView(draft);
    this.multiDrafts = null;
    this.setData({
      parsing: false,
      draft,
      draftView: view,
      draftViews: [],
      multiTotalText: '',
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
    const categoryLabel = this.categoryLabel(d.categoryCode, d.categoryName);

    return {
      amountText,
      intPart: parts[0],
      decPart: parts[1] || '00',
      isIncome: Number(d.billType) === BILL_TYPE.INCOME,
      confidenceText: confidence === null ? '—' : Math.round(confidence * 100) + '%',
      confidenceLow: confidence !== null && confidence < 0.9,
      needConfirm: confidence !== null && confidence < 0.9,
      categoryLabel,
      catColor: palette.colorFor((d.categoryName || categoryLabel || '').split(' · ').pop()),
      catBg: palette.rgba(palette.colorFor((d.categoryName || categoryLabel || '').split(' · ').pop()), 0.15),
      missingCategory: missing.indexOf('categoryCode') >= 0 || !d.categoryCode,
      missingAmount: missing.indexOf('amount') >= 0 || d.amount === null || d.amount === undefined,
      missingChannel: missing.indexOf('payChannelCode') >= 0 || !d.payChannelCode,
      channelName: d.payChannelName || '未指定',
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

  /** 点汇总卡里的某一笔：打开编辑弹层，确认后回写到列表 */
  onEditMulti(e) {
    if (this.data.saving) {
      return;
    }
    const index = Number(e.currentTarget.dataset.index);
    const drafts = this.multiDrafts || [];
    if (!drafts[index]) {
      return;
    }
    this.openPreview(drafts[index], index);
  },

  /** 移除误识别的某一笔 */
  onRemoveMulti(e) {
    if (this.data.saving) {
      return;
    }
    const index = Number(e.currentTarget.dataset.index);
    const drafts = (this.multiDrafts || []).slice();
    if (!drafts[index]) {
      return;
    }
    drafts.splice(index, 1);
    if (!drafts.length) {
      this.clearDraftState();
      this.appendAi('这次识别已清空，重新说一句试试？');
      return;
    }
    this.applyMulti(drafts);
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
    if (this.data.saving) {
      return;
    }
    const text = this.sourceText || '';
    this.clearDraftState();
    this.setData({ text, canSend: !!text });
    request.toast('改一下说法，再发一次');
  },

  /** 多笔：校验后逐笔顺序存入 */
  onSaveAll() {
    if (this.data.saving) {
      return;
    }
    const drafts = this.multiDrafts || [];
    if (!drafts.length) {
      return;
    }

    for (let i = 0; i < drafts.length; i++) {
      const error = this.validate(drafts[i]);
      if (error) {
        request.toast('第 ' + (i + 1) + ' 笔：' + error);
        this.openPreview(drafts[i], i);
        return;
      }
    }

    this.setData({ saving: true });
    const done = [];
    const skipped = [];
    const failed = [];

    const saveAt = (i) => {
      if (i >= drafts.length) {
        return Promise.resolve();
      }
      const draft = drafts[i];
      const payload = this.buildPayload(draft, {
        requestId: format.uuid(),
        source: SOURCE.TEXT,
        confidence: draft.confidence,
        recordText: draft.segmentText || this.sourceText
      });
      return billApi.save(payload, { silent: true })
        .then((res) => {
          done.push({ draft, payload, res });
        })
        .catch((err) => {
          if (err && err.code === CODE.DUPLICATE_BILL) {
            skipped.push({ draft, payload });
          } else {
            failed.push({ draft, payload, err });
          }
        })
        .then(() => saveAt(i + 1));
    };

    saveAt(0).then(() => this.finishSaveAll(done, skipped, failed));
  },

  finishSaveAll(done, skipped, failed) {
    this.setData({ saving: false });

    if (done.length) {
      const parts = done.map((it) => '¥' + format.formatMoney(it.payload.amount));
      this.appendAi('已存入 ' + done.length + ' 笔：' + parts.join('、'));
      const insights = [];
      done.forEach((it) => {
        ((it.res && it.res.insights) || []).forEach((item) => insights.push(item));
      });
      insights.slice(0, 2).forEach((item) => {
        if (item && item.title) {
          this.appendAi(item.title + '：' + (item.body || ''));
        }
      });
    }
    if (skipped.length) {
      this.appendAi('另有 ' + skipped.length + ' 笔和已有记录重复，已跳过。');
    }
    if (failed.length) {
      const first = failed[0].err;
      this.appendAi((first && first.message) || ('有 ' + failed.length + ' 笔没存上，点开确认后再试。'));
    }

    if (!failed.length) {
      this.clearDraftState();
      this.loadStat();
      this.loadGreeting();
      this.loadTemplates();
      return;
    }

    // 失败的留在卡里，改完可以再点「全部存入」
    this.multiDrafts = failed.map((it) => it.draft);
    this.setData({
      draftViews: this.buildMultiViews(this.multiDrafts),
      multiTotalText: this.multiTotalTextOf(this.multiDrafts)
    });
    if (done.length || skipped.length) {
      this.loadStat();
      this.loadGreeting();
    }
  },

  /** 清空解析卡 / 汇总卡与相关临时状态 */
  clearDraftState() {
    this.multiDrafts = null;
    this.editIndex = -1;
    this.requestId = '';
    this.sourceText = '';
    this.pendingImageUrl = '';
    this.setData({
      draft: null,
      draftView: null,
      draftViews: [],
      multiTotalText: '',
      splitCount: 1,
      showPreview: false,
      previewDraft: null,
      pendingImagePath: ''
    });
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

  openPreview(draft, index) {
    const d = draft || {};
    this.editIndex = typeof index === 'number' ? index : -1;
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
    this.editIndex = -1;
    this.setData({ showPreview: false, previewDraft: null });
  },

  onPreviewConfirm(e) {
    const form = (e.detail && e.detail.draft) || {};

    // 多笔汇总卡里的单笔编辑：只回写列表，不立即入库
    if (this.editIndex >= 0 && this.multiDrafts && this.multiDrafts[this.editIndex]) {
      // 表单里字段已补齐，清掉旧的 missingFields，避免「待确认」标记残留
      const patch = Object.assign({}, form, { missingFields: [] });
      this.multiDrafts[this.editIndex] = Object.assign({}, this.multiDrafts[this.editIndex], patch);
      this.editIndex = -1;
      this.setData({
        showPreview: false,
        previewDraft: null,
        draftViews: this.buildMultiViews(this.multiDrafts),
        multiTotalText: this.multiTotalTextOf(this.multiDrafts)
      });
      return;
    }

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
      recordText: options.recordText === undefined ? (this.sourceText || '') : options.recordText,
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

    this.clearDraftState();

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
    if (auth.requireLogin()) {
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
        this.recognizePhoto(file.tempFilePath);
      }
    });
  },

  /** 小票识别：上传 + 视觉模型解析，成功后直接弹出确认卡片 */
  recognizePhoto(filePath) {
    this.sourceText = '';
    this.appendMessage('[小票]');
    this.multiDrafts = null;
    this.editIndex = -1;
    this.setData({ parsing: true, draft: null, draftView: null, draftViews: [], multiTotalText: '', splitCount: 1 });
    this.scrollToEnd();
    wx.showLoading({ title: '上传中…', mask: true });

    billApi.parseImage(filePath)
      .then((draft) => {
        wx.hideLoading();
        this.requestId = format.uuid();
        this.pendingImageUrl = (draft && draft.imageUrl) || '';
        this.setData({ pendingImagePath: filePath });
        this.applyDraft(draft);
      })
      .catch((err) => {
        wx.hideLoading();
        const draft = (err && err.data) || {};
        // 图片已存下但没认全：保留小票，打开手动确认让用户补字段
        if (draft.imageUrl) {
          this.pendingImageUrl = draft.imageUrl;
          this.setData({ pendingImagePath: filePath });
        }
        this.setData({ parsing: false });
        this.handleParseError(err);
      });
  },

  onPhotoRemove() {
    this.pendingImageUrl = '';
    this.setData({ pendingImagePath: '' });
  },

  onVoice() {
    if (auth.requireLogin()) {
      return;
    }
    request.toast('语音记账即将上线');
  },

  /* ---------- 抽屉 ---------- */

  onAvatarTap() {
    this.openDrawer();
  },

  /** 游客态顶部「登录」入口：由用户主动触发授权 */
  onLoginTap() {
    auth.requireLogin();
  },

  /** 游客在抽屉里点头像 / 登录按钮：关掉抽屉并引导登录 */
  onDrawerProfile() {
    if (!auth.guard()) {
      this.closeDrawer();
      auth.requireLogin();
    }
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
