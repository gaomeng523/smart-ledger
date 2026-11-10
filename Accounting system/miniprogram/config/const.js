/**
 * 全局常量：错误码枚举、业务枚举、存储 Key（§6.2、§7.1）
 */

/** 统一响应 code（与后端 ErrorCode 一一对应） */
const CODE = {
  SUCCESS: 0,
  PARAM_ERROR: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  TOO_MANY_REQUESTS: 429,
  SYSTEM_ERROR: 500,

  WX_LOGIN_FAILED: 1001,
  AI_PARSE_FAILED: 1002,
  AI_OUTPUT_INVALID: 1003,
  AMOUNT_UNRECOGNIZED: 1004,
  DUPLICATE_BILL: 1005,
  IMPORT_FORMAT_ERROR: 1006,
  PARSE_TOO_FREQUENT: 1007,

  /** 本地网络异常（非后端返回） */
  NETWORK_ERROR: -1
};

/** 收支类型 */
const BILL_TYPE = {
  EXPENSE: 0,
  INCOME: 1
};

/** 账单来源 */
const SOURCE = {
  TEXT: 1,
  VOICE: 2,
  TEMPLATE: 3,
  IMPORT: 4,
  OCR: 5,
  MANUAL: 6
};

/** 本地存储 Key */
const STORAGE_KEY = {
  TOKEN: 'token',
  DICT: 'dict',
  NICKNAME: 'nickname'
};

/** 字典缓存有效期：10 分钟（§6.5） */
const DICT_TTL = 10 * 60 * 1000;

module.exports = {
  CODE,
  BILL_TYPE,
  SOURCE,
  STORAGE_KEY,
  DICT_TTL
};