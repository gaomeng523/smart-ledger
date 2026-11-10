/**
 * 标签胶囊（V2，§5）
 *
 * 账单表单（mode=input）与列表筛选面板（mode=filter）共用：
 * - input：展示已选标签（可删除）+ 输入框 + 历史联想，可新增；最多 max 个，单个 ≤ 12 字
 * - filter：展示全部候选标签，点选多选
 *
 * 事件：change → tags(string[])
 */
const MAX_TAG_LENGTH = 12;

Component({
  properties: {
    /** 当前标签（input 为已选，filter 为已勾选） */
    value: {
      type: Array,
      value: []
    },
    /** 历史标签候选（来自 /api/bill/tag/suggest） */
    suggestions: {
      type: Array,
      value: []
    },
    /** 上限，默认 5 */
    max: {
      type: Number,
      value: 5
    },
    /** input | filter */
    mode: {
      type: String,
      value: 'input'
    },
    placeholder: {
      type: String,
      value: '添加标签'
    }
  },

  data: {
    input: '',
    /** input 模式：未选中的历史候选 */
    candidates: [],
    /** filter 模式：全部候选 */
    allTags: []
  },

  observers: {
    'value, suggestions, mode': function () {
      this.compute();
    }
  },

  methods: {
    compute() {
      const value = this.data.value || [];
      const suggestions = (this.data.suggestions || []).filter(
        (tag) => value.indexOf(tag) < 0
      );
      this.setData({
        candidates: suggestions,
        allTags: this.data.suggestions || []
      });
    },

    onInput(e) {
      this.setData({ input: e.detail.value || '' });
    },

    /** input 模式：确认输入框内容 */
    onConfirm() {
      const text = (this.data.input || '').trim();
      if (!text) {
        return;
      }
      this.addTag(text);
    },

    addTag(tag) {
      const value = (this.data.value || []).slice();
      if (value.indexOf(tag) >= 0) {
        this.setData({ input: '' });
        return;
      }
      if (value.length >= this.data.max) {
        wx.showToast({ title: '最多 ' + this.data.max + ' 个标签', icon: 'none' });
        return;
      }
      if (tag.length > MAX_TAG_LENGTH) {
        wx.showToast({ title: '单个标签不超过 ' + MAX_TAG_LENGTH + ' 字', icon: 'none' });
        return;
      }
      value.push(tag);
      this.setData({ input: '' });
      this.triggerEvent('change', value);
    },

    /** input 模式：点候选快速添加 */
    onCandidateTap(e) {
      this.addTag(e.currentTarget.dataset.tag);
    },

    /** 通用：移除（input） */
    onRemove(e) {
      const tag = e.currentTarget.dataset.tag;
      const value = (this.data.value || []).filter((item) => item !== tag);
      this.triggerEvent('change', value);
    },

    /** filter 模式：切换勾选 */
    onToggle(e) {
      const tag = e.currentTarget.dataset.tag;
      const value = (this.data.value || []).slice();
      const index = value.indexOf(tag);
      if (index >= 0) {
        value.splice(index, 1);
      } else {
        value.push(tag);
      }
      this.triggerEvent('change', value);
    }
  }
});