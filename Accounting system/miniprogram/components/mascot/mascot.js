/**
 * 吉祥物「萌萌」（§2.7）
 *
 * 定位：软萌、治愈、会帮用户「吃掉」账单烦恼的小记账精灵。
 * 形象：云朵身体 + 硬币光环 + 钱包尾巴（深色色板见 mascot.wxss 顶部注释）。
 *
 * 默认用纯 WXSS 代码绘制；若 assets/mascot/mengmeng-*.png 存在（加载成功）则自动改用位图。
 * 尺寸 96 / 160 / 240 rpx：根节点 font-size = size，内部尺寸全部用 em 等比缩放。
 */

/** 8 个状态：表情 + 动作 + 使用场景 */
const STATES = [
  'idle',      // 微笑   + 站立挥手   默认展示 / 我的页
  'expect',    // 期待   + 抱小账本   首页等待输入
  'thinking',  // 疑惑   + 抱臂思考   AI 解析中
  'success',   // 开心   + 打滚开心   保存成功
  'wow',       // 惊讶   + 蒙眼害羞   大额支出
  'empty',     // 委屈               空状态
  'offline',   // 委屈   + 撑伞躲雨   网络异常
  'shine'      // 闪亮   + 抱着硬币   统计完成 / 结余
];

/** 历史命名兼容 */
const STATE_ALIAS = {
  sorry: 'expect'
};

/**
 * V1 默认关闭位图：素材不存在时文件服务器会返回 500，每次渲染都会往控制台刷错误。
 * 把 assets/mascot/mengmeng-*.png 放好后改成 true，组件会自动优先使用位图。
 */
const USE_BITMAP = false;

Component({
  properties: {
    /** 见 STATES 注释 */
    state: {
      type: String,
      value: 'idle'
    },
    /** 96 | 160 | 240 */
    size: {
      type: Number,
      value: 96
    }
  },

  data: {
    /** 归一化后的状态，仅用于 WXML 类名 */
    s: 'idle',
    /** 是否尝试加载位图素材 */
    useBitmap: USE_BITMAP,
    src: '',
    /** 位图加载成功后才置 true（否则始终显示代码绘制的萌萌） */
    useImage: false
  },

  observers: {
    state(state) {
      this.apply(state);
    }
  },

  lifetimes: {
    attached() {
      this.apply(this.data.state);
    }
  },

  methods: {
    apply(raw) {
      const resolved = STATE_ALIAS[raw] || raw;
      const s = STATES.indexOf(resolved) >= 0 ? resolved : 'idle';
      this.setData({
        s,
        src: '/assets/mascot/mengmeng-' + s + '.png',
        useImage: false
      });
    },

    onImageLoad() {
      this.setData({ useImage: true });
    },

    onImageError() {
      // 素材缺失：静默降级为代码绘制，不白屏
      this.setData({ useImage: false });
    }
  }
});