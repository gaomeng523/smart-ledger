/**
 * 模板条目（V2，§5）
 * 左滑露出「编辑 / 删除」；右侧「记一笔」一键记账。
 * 属性传入、事件抛出，组件内不发请求。
 *
 * 事件：tap → { template } / use → { template } / edit → { template } / delete → { template }
 */
const format = require('../../utils/format');
const { BILL_TYPE } = require('../../config/const');

const BADGE_COLORS = ['#FFC46B', '#FF9EAB', '#8ED9C0', '#6BB6FF', '#A88BEB', '#7ED9A6'];
/** 操作区宽度（px）：两个按钮 */
const ACTION_WIDTH = 150;

Component({
  properties: {
    template: {
      type: Object,
      value: null
    },
    tintIndex: {
      type: Number,
      value: 0
    }
  },

  data: {
    offset: 0,
    opened: false,
    amountText: '',
    isIncome: false,
    badgeText: '记',
    badgeColor: BADGE_COLORS[0],
    metaText: ''
  },

  observers: {
    'template, tintIndex': function (template, tintIndex) {
      if (!template) {
        return;
      }
      const index = Math.abs(Number(tintIndex) || 0) % BADGE_COLORS.length;
      const meta = [template.payChannelName, template.categoryName]
        .filter((text) => !!text)
        .join(' · ');
      this.setData({
        amountText: format.signedAmount(template.amount, template.billType),
        isIncome: Number(template.billType) === BILL_TYPE.INCOME,
        badgeText: format.firstChar(template.title || template.categoryName),
        badgeColor: BADGE_COLORS[index],
        metaText: meta
      });
    }
  },

  methods: {
    onTouchStart(e) {
      this._startX = e.touches[0].clientX;
      this._startOffset = this.data.opened ? -ACTION_WIDTH : 0;
    },

    onTouchMove(e) {
      if (this._startX === undefined) {
        return;
      }
      const dx = e.touches[0].clientX - this._startX;
      let offset = this._startOffset + dx;
      offset = Math.max(-ACTION_WIDTH, Math.min(0, offset));
      this.setData({ offset });
    },

    onTouchEnd() {
      this._startX = undefined;
      const opened = this.data.offset < -ACTION_WIDTH / 2;
      this.setData({
        opened,
        offset: opened ? -ACTION_WIDTH : 0
      });
    },

    /** 点击卡片：左滑展开时先收起，否则抛 tap */
    onTap() {
      if (this.data.opened) {
        this.setData({ opened: false, offset: 0 });
        return;
      }
      this.triggerEvent('tap', { template: this.data.template });
    },

    onUse() {
      this.triggerEvent('use', { template: this.data.template });
    },

    onEdit() {
      this.setData({ opened: false, offset: 0 });
      this.triggerEvent('edit', { template: this.data.template });
    },

    onDelete() {
      this.setData({ opened: false, offset: 0 });
      this.triggerEvent('delete', { template: this.data.template });
    },

    noop() {}
  }
});