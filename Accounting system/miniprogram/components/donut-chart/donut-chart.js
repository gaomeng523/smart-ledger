/**
 * 支出构成环形图（§5、§4.4）
 * 自绘 canvas：环宽 32rpx、扇区间留 2rpx 白色间隙、中心显示总额。
 *
 * 属性：items（[{ name, amount, color }]）、centerText
 * 事件：tapItem → { item }
 */
let uid = 0;

function rpxToPx() {
  try {
    const info = wx.getSystemInfoSync();
    return (info.windowWidth || 375) / 750;
  } catch (e) {
    return 0.5;
  }
}

Component({
  properties: {
    items: {
      type: Array,
      value: []
    },
    centerText: {
      type: String,
      value: '0.00'
    }
  },

  data: {
    canvasId: ''
  },

  lifetimes: {
    attached() {
      uid += 1;
      this.setData({ canvasId: 'donut-' + uid });
    },
    ready() {
      this.initCanvas();
    }
  },

  pageLifetimes: {
    show() {
      // tabBar 页面切回时 canvas 可能被清空，需要重绘
      if (this.ctx) {
        this.initCanvas();
      }
    }
  },

  observers: {
    items() {
      if (this.ctx) {
        this.draw();
      }
    }
  },

  methods: {
    initCanvas() {
      this.createSelectorQuery()
        .select('#' + this.data.canvasId)
        .fields({ node: true, size: true, rect: true })
        .exec((res) => {
          const info = res && res[0];
          if (!info || !info.node) {
            return;
          }
          const canvas = info.node;
          const ctx = canvas.getContext('2d');
          let dpr = 2;
          try {
            dpr = wx.getSystemInfoSync().pixelRatio || 2;
          } catch (e) {
            dpr = 2;
          }
          canvas.width = info.width * dpr;
          canvas.height = info.height * dpr;
          ctx.scale(dpr, dpr);

          this.canvas = canvas;
          this.ctx = ctx;
          this.cssWidth = info.width;
          this.cssHeight = info.height;
          this.canvasRect = { left: info.left, top: info.top };
          this.draw();
        });
    },

    draw() {
      const ctx = this.ctx;
      if (!ctx) {
        return;
      }
      const w = this.cssWidth;
      const h = this.cssHeight;
      ctx.clearRect(0, 0, w, h);

      const cx = w / 2;
      const cy = h / 2;
      const unit = rpxToPx();
      const ringWidth = 32 * unit;
      const radius = Math.min(w, h) / 2 - ringWidth / 2 - 2;

      const items = (this.data.items || []).filter((it) => Number(it.amount) > 0);
      const total = items.reduce((sum, it) => sum + Number(it.amount), 0);

      if (!total) {
        ctx.beginPath();
        ctx.strokeStyle = 'rgba(255,255,255,.06)';
        ctx.lineWidth = ringWidth;
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.stroke();
        return;
      }

      const gapAngle = (2 * unit) / radius;
      let start = -Math.PI / 2;
      items.forEach((it) => {
        const angle = (Number(it.amount) / total) * Math.PI * 2;
        const from = start + gapAngle / 2;
        const to = start + angle - gapAngle / 2;
        if (to > from) {
          ctx.beginPath();
          ctx.strokeStyle = it.color || '#D9D2CB';
          ctx.lineWidth = ringWidth;
          ctx.arc(cx, cy, radius, from, to);
          ctx.stroke();
        }
        start += angle;
      });
    },

    onTap(e) {
      if (!this.ctx || !this.canvasRect) {
        return;
      }
      const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]);
      const pageX = touch ? touch.clientX : (e.detail && e.detail.x);
      const pageY = touch ? touch.clientY : (e.detail && e.detail.y);
      if (pageX === undefined || pageY === undefined) {
        return;
      }

      const unit = rpxToPx();
      const ringWidth = 32 * unit;
      const cx = this.cssWidth / 2;
      const cy = this.cssHeight / 2;
      const radius = Math.min(this.cssWidth, this.cssHeight) / 2 - ringWidth / 2 - 2;

      const dx = pageX - this.canvasRect.left - cx;
      const dy = pageY - this.canvasRect.top - cy;
      const distance = Math.sqrt(dx * dx + dy * dy);
      if (Math.abs(distance - radius) > ringWidth / 2 + 4) {
        return;
      }

      const items = (this.data.items || []).filter((it) => Number(it.amount) > 0);
      const total = items.reduce((sum, it) => sum + Number(it.amount), 0);
      if (!total) {
        return;
      }

      let angle = Math.atan2(dy, dx) + Math.PI / 2;
      if (angle < 0) {
        angle += Math.PI * 2;
      }

      let acc = 0;
      for (let i = 0; i < items.length; i++) {
        const span = (Number(items[i].amount) / total) * Math.PI * 2;
        if (angle >= acc && angle < acc + span) {
          this.triggerEvent('tapItem', { item: items[i] });
          return;
        }
        acc += span;
      }
    }
  }
});