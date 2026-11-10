/**
 * 环境与地址配置（§7.3）
 * 约定：域名只在此处声明，禁止散落到各页面。
 *
 * 本地联调提醒：微信开发者工具需关闭「校验合法域名」才能请求本机地址
 * （project.config.json 已设 urlCheck:false）。
 * 注意：dev 用 127.0.0.1 而非 localhost —— 开发者工具下 localhost 可能被解析为 IPv6/走代理导致请求挂起。
 */

// 当前环境：dev | prod
const ENV = 'prod';

const BASE_URL_MAP = {
  // 当前临时指向本机后端做 UI 联调；测完改回服务器地址 http://101.42.2.204:8080
  dev: 'http://127.0.0.1:8080',
  // 正式环境：已备案域名 + HTTPS（Nginx 443 反代到本机 8080）
  prod: 'https://api.gmenggmeng.cn'
};

module.exports = {
  ENV,
  BASE_URL: BASE_URL_MAP[ENV]
};