package com.smartledger.service;

import java.io.OutputStream;
import java.time.LocalDate;

/**
 * 数据导出服务（V3 · F-91）。
 */
public interface ExportService {

    /**
     * 将指定日期区间（含首尾）的账单以 UTF-8 BOM CSV 写入输出流。
     * 导出口径与账单列表一致：仅本人、未删除记录，上限 10000 条。
     */
    void writeBillCsv(String openid, LocalDate start, LocalDate end, OutputStream out);
}
