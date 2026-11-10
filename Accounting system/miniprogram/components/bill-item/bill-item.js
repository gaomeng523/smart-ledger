/**
 * 账单条目（§5）
 * 属性传入、事件抛出；组件内不发请求。
 * 事件：tap → { bill }
 */
const format = require('../../utils/format');
const { BILL_TYPE } = require('../../config/const');
const { iconFor } = require('../../utils/category-icon');

/** 分类圆标底色调色板（点缀色，深色文字，§2.2 约束 2） */
const BADGE_COLORS = ['#FFC46B', '#FF9EAB', '#8ED9C0', '#6BB6FF', '#A88BEB', '#7ED9A6'];

Component({
  properties: {
    /** 单条账单 */
    bill: {
      type: Object,
      value: null
    },
    /** 是否显示日期（首页/分组内一般不需要） */
    showDate: {
      type: Boolean,
      value: false
    },
    /** 圆标配色序位（由列表按顺序传入，保证同列表内不重色） */
    tintIndex: {
      type: Number,
      value: 0
    }
  },

  data: {
    amountText: '',
    isIncome: false,
    iconName: '',
    badgeText: '记',
    badgeColor: BADGE_COLORS[0],
    remarkText: '',
    dateText: ''
  },

  observers: {
    'bill, showDate, tintIndex': function (bill, showDate, tintIndex) {
      if (!bill) {
        return;
      }
      const isIncome = Number(bill.billType) === BILL_TYPE.INCOME;
      const index = Math.abs(Number(tintIndex) || 0) % BADGE_COLORS.length;
      const remark = bill.remark || '';

      this.setData({
        amountText: format.signedAmount(bill.amount, bill.billType),
        isIncome,
        iconName: iconFor(bill.categoryCode),
        badgeText: format.firstChar(bill.categoryName),
        badgeColor: BADGE_COLORS[index],
        remarkText: format.truncate(remark, 16),
        dateText: showDate ? format.shortDate(bill.recordDate) : ''
      });
    }
  },

  methods: {
    onTap() {
      this.triggerEvent('tap', { bill: this.data.bill });
    }
  }
});