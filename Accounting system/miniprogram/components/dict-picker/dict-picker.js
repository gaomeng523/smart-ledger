/**
 * 分类 / 渠道选择器（§5）
 * 底部半屏选择面板；分类为两级结构：左侧一级、右侧二级（首项为该一级本身「不细分」）。
 * 字典缺少 categoryTree 时自动退回单列平铺。
 * 事件：change → { code, name }
 */
const { iconFor, iconOf } = require('../../utils/category-icon');

/** 图标颜色：常规 / 选中（与 --color-text-sub / --color-brand 对应） */
const ICON_COLOR = '#C9CAC6';
const ICON_COLOR_ACTIVE = '#3D8BFF';

Component({
  properties: {
    /** category | channel */
    type: {
      type: String,
      value: 'category'
    },
    /** 当前收支类型（仅 category 过滤用） */
    billType: {
      type: Number,
      value: 0
    },
    /** 当前选中 code */
    value: {
      type: String,
      value: ''
    },
    /** 全量字典 { channels, categories, categoryTree } */
    dict: {
      type: Object,
      value: null
    },
    placeholder: {
      type: String,
      value: '请选择'
    },
    disabled: {
      type: Boolean,
      value: false
    },
    /** 警示描边（用于解析结果缺失/低置信字段） */
    invalid: {
      type: Boolean,
      value: false
    }
  },

  data: {
    show: false,
    /** single=单列平铺；tree=两级双列 */
    mode: 'single',
    list: [],
    parents: [],
    children: [],
    activeParent: '',
    selectedName: '',
    selectedIcon: '',
    iconColor: ICON_COLOR,
    iconColorActive: ICON_COLOR_ACTIVE
  },

  observers: {
    'type, billType, value, dict': function () {
      this.computeList();
    }
  },

  methods: {
    computeList() {
      const dict = this.data.dict || {};
      const value = this.data.value || '';

      if (this.data.type === 'channel') {
        this.applySingle(dict.channels || [], value, false);
        return;
      }

      const billType = Number(this.data.billType);
      const tree = (dict.categoryTree || []).filter((item) => Number(item.billType) === billType);
      if (tree.length === 0) {
        const flat = (dict.categories || []).filter((item) => Number(item.billType) === billType);
        this.applySingle(flat, value, true);
        return;
      }

      // 定位当前值所属一级：值本身是一级，或命中某个一级的子项
      let activeParent = '';
      for (let i = 0; i < tree.length; i += 1) {
        const parent = tree[i];
        const children = parent.children || [];
        if (parent.code === value || children.some((item) => item.code === value)) {
          activeParent = parent.code;
          break;
        }
      }
      if (!activeParent) {
        const remembered = this.data.activeParent;
        const stillExists = tree.some((item) => item.code === remembered);
        activeParent = stillExists ? remembered : tree[0].code;
      }

      this.applyTree(tree, activeParent, value);
    },

    applySingle(list, value, useIcon) {
      const selected = list.filter((item) => item.code === value)[0];
      this.setData({
        mode: 'single',
        list,
        parents: [],
        children: [],
        activeParent: '',
        selectedName: selected ? selected.name : '',
        selectedIcon: selected && useIcon ? iconOf(selected) : ''
      });
    },

    applyTree(tree, activeParent, value) {
      const parents = tree.map((item) => ({
        code: item.code,
        name: item.name,
        icon: iconOf(item)
      }));
      const current = tree.filter((item) => item.code === activeParent)[0] || tree[0];
      const children = [{
        code: current.code,
        name: current.name,
        label: current.name + '（不细分）',
        icon: iconOf(current)
      }].concat((current.children || []).map((item) => ({
        code: item.code,
        name: item.name,
        label: item.name,
        icon: iconOf(item)
      })));

      // 选中项的展示名：二级用自身名称，一级直接用一级名称
      let selectedName = '';
      for (let i = 0; i < tree.length && !selectedName; i += 1) {
        const parent = tree[i];
        if (parent.code === value) {
          selectedName = parent.name;
          break;
        }
        const hit = (parent.children || []).filter((item) => item.code === value)[0];
        if (hit) {
          selectedName = hit.name;
        }
      }

      this.setData({
        mode: 'tree',
        list: [],
        parents,
        children,
        activeParent: current.code,
        selectedName,
        selectedIcon: selectedName ? (this.iconByCode(tree, value) || iconFor(value)) : ''
      });
    },

    iconByCode(tree, code) {
      for (let i = 0; i < tree.length; i += 1) {
        const parent = tree[i];
        if (parent.code === code) {
          return parent.icon || '';
        }
        const hit = (parent.children || []).filter((item) => item.code === code)[0];
        if (hit) {
          return hit.icon || '';
        }
      }
      return '';
    },

    open() {
      if (this.data.disabled) {
        return;
      }
      this.setData({ show: true });
    },

    close() {
      this.setData({ show: false });
    },

    chooseParent(e) {
      const code = e.currentTarget.dataset.code;
      const tree = ((this.data.dict || {}).categoryTree || [])
        .filter((item) => Number(item.billType) === Number(this.data.billType));
      if (!tree.length || code === this.data.activeParent) {
        return;
      }
      this.applyTree(tree, code, this.data.value || '');
    },

    choose(e) {
      const code = e.currentTarget.dataset.code;
      const name = e.currentTarget.dataset.name;
      this.setData({ show: false });
      this.triggerEvent('change', { code, name });
    },

    noop() {}
  }
});
