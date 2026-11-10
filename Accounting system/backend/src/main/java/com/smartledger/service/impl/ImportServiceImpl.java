package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.constant.CommonConstants;
import com.smartledger.common.enums.BillSourceEnum;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.dto.response.ImportResultDTO;
import com.smartledger.entity.Bill;
import com.smartledger.entity.ImportRecord;
import com.smartledger.mapper.BillMapper;
import com.smartledger.mapper.ImportRecordMapper;
import com.smartledger.service.DictService;
import com.smartledger.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账单导入应用服务实现（V2）。
 *
 * <p>支持微信 / 支付宝导出的 CSV，按行解析，失败行记录行号与原因。
 * 同一批次的账单通过 {@code requestId} 前缀 {@code imp-{importId}-} 关联，供整批撤销。</p>
 */
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String REQUEST_ID_PREFIX = "imp-";
    private static final int MAX_REMARK_LEN = 255;
    private static final String UNCATEGORIZED_EXPENSE = "EXPENSE_OTHER";
    private static final String UNCATEGORIZED_INCOME = "INCOME_OTHER";

    /** 备注关键词 → 分类编码（简易归类，未命中落「其他」） */
    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("餐", "FOOD");
        CATEGORY_KEYWORDS.put("饭", "FOOD");
        CATEGORY_KEYWORDS.put("外卖", "FOOD");
        CATEGORY_KEYWORDS.put("美团", "FOOD");
        CATEGORY_KEYWORDS.put("饿了么", "FOOD");
        CATEGORY_KEYWORDS.put("买菜", "FOOD");
        CATEGORY_KEYWORDS.put("超市", "FOOD");
        CATEGORY_KEYWORDS.put("奶茶", "FOOD");
        CATEGORY_KEYWORDS.put("咖啡", "FOOD");
        CATEGORY_KEYWORDS.put("打车", "TRAFFIC");
        CATEGORY_KEYWORDS.put("滴滴", "TRAFFIC");
        CATEGORY_KEYWORDS.put("地铁", "TRAFFIC");
        CATEGORY_KEYWORDS.put("公交", "TRAFFIC");
        CATEGORY_KEYWORDS.put("高铁", "TRAFFIC");
        CATEGORY_KEYWORDS.put("火车", "TRAFFIC");
        CATEGORY_KEYWORDS.put("机票", "TRAFFIC");
        CATEGORY_KEYWORDS.put("加油", "TRAFFIC");
        CATEGORY_KEYWORDS.put("停车", "TRAFFIC");
        CATEGORY_KEYWORDS.put("淘宝", "SHOPPING");
        CATEGORY_KEYWORDS.put("京东", "SHOPPING");
        CATEGORY_KEYWORDS.put("拼多多", "SHOPPING");
        CATEGORY_KEYWORDS.put("购物", "SHOPPING");
        CATEGORY_KEYWORDS.put("房租", "HOUSING");
        CATEGORY_KEYWORDS.put("水电", "HOUSING");
        CATEGORY_KEYWORDS.put("物业", "HOUSING");
        CATEGORY_KEYWORDS.put("燃气", "HOUSING");
        CATEGORY_KEYWORDS.put("电费", "HOUSING");
        CATEGORY_KEYWORDS.put("水费", "HOUSING");
        CATEGORY_KEYWORDS.put("电影", "ENTERTAINMENT");
        CATEGORY_KEYWORDS.put("游戏", "ENTERTAINMENT");
        CATEGORY_KEYWORDS.put("娱乐", "ENTERTAINMENT");
        CATEGORY_KEYWORDS.put("健身", "ENTERTAINMENT");
        CATEGORY_KEYWORDS.put("医院", "MEDICAL");
        CATEGORY_KEYWORDS.put("药", "MEDICAL");
        CATEGORY_KEYWORDS.put("医疗", "MEDICAL");
        CATEGORY_KEYWORDS.put("书", "STUDY");
        CATEGORY_KEYWORDS.put("课程", "STUDY");
        CATEGORY_KEYWORDS.put("学习", "STUDY");
        CATEGORY_KEYWORDS.put("培训", "STUDY");
        CATEGORY_KEYWORDS.put("红包", "SOCIAL");
        CATEGORY_KEYWORDS.put("转账", "SOCIAL");
        CATEGORY_KEYWORDS.put("礼", "SOCIAL");
        CATEGORY_KEYWORDS.put("工资", "SALARY");
        CATEGORY_KEYWORDS.put("报销", "REIMBURSE");
    }

    private final ImportRecordMapper importRecordMapper;
    private final BillMapper billMapper;
    private final DictService dictService;

    @Override
    public ImportResultDTO importBills(String openid, MultipartFile file, String sourceType, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "导入文件不能超过 5MB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            throw new BizException(ErrorCode.IMPORT_FORMAT_ERROR);
        }
        String channelCode = resolveChannel(sourceType);

        List<String[]> rows = parseCsv(readContent(file));
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_FORMAT_ERROR);
        }
        ColumnIndex columns = ColumnIndex.resolve(rows.get(0));
        if (!columns.valid()) {
            throw new BizException(ErrorCode.IMPORT_FORMAT_ERROR);
        }

        Long importId = null;
        if (!dryRun) {
            ImportRecord record = new ImportRecord();
            record.setOpenid(openid);
            record.setFileName(fileName);
            record.setSourceType(sourceType.trim().toUpperCase());
            record.setStatus(0);
            record.setTotalCount(0);
            record.setSuccessCount(0);
            record.setFailCount(0);
            importRecordMapper.insert(record);
            importId = record.getId();
        }

        int total = 0;
        int success = 0;
        List<ImportResultDTO.FailItem> failDetail = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            int lineNo = i + 1;
            total++;
            try {
                ParsedRow parsed = parseRow(row, columns);
                if (!dryRun) {
                    billMapper.insert(buildBill(openid, importId, lineNo, channelCode, parsed));
                }
                success++;
            } catch (BizException e) {
                failDetail.add(new ImportResultDTO.FailItem(lineNo, e.getMessage()));
            } catch (Exception e) {
                failDetail.add(new ImportResultDTO.FailItem(lineNo, "解析失败"));
            }
        }

        if (!dryRun && importId != null) {
            ImportRecord update = new ImportRecord();
            update.setId(importId);
            update.setTotalCount(total);
            update.setSuccessCount(success);
            update.setFailCount(failDetail.size());
            update.setStatus(failDetail.isEmpty() ? 1 : 2);
            update.setFailDetail(serializeFailDetail(failDetail));
            importRecordMapper.updateById(update);
        }

        ImportResultDTO result = new ImportResultDTO();
        result.setImportId(importId);
        result.setTotalCount(total);
        result.setSuccessCount(success);
        result.setFailCount(failDetail.size());
        result.setFailDetail(failDetail);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(String openid, Long importId) {
        ImportRecord record = importRecordMapper.selectOne(new LambdaQueryWrapper<ImportRecord>()
                .eq(ImportRecord::getId, importId)
                .eq(ImportRecord::getOpenid, openid));
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入批次不存在");
        }
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .likeRight(Bill::getRequestId, REQUEST_ID_PREFIX + importId + "-"));
        for (Bill bill : bills) {
            billMapper.deleteById(bill.getId());
        }
        ImportRecord update = new ImportRecord();
        update.setId(importId);
        update.setStatus(3);
        importRecordMapper.updateById(update);
    }

    // ==================== 解析辅助 ====================

    private Bill buildBill(String openid, Long importId, int lineNo, String channelCode, ParsedRow parsed) {
        Bill bill = new Bill();
        bill.setOpenid(openid);
        bill.setBookId(CommonConstants.DEFAULT_BOOK_ID);
        bill.setAmount(parsed.amount);
        bill.setBillType(parsed.billType);
        bill.setPayChannelCode(channelCode);
        bill.setCategoryCode(parsed.categoryCode);
        bill.setRemark(parsed.remark);
        bill.setRecordDate(parsed.recordDate);
        bill.setSource(BillSourceEnum.IMPORT.getCode());
        bill.setRequestId(REQUEST_ID_PREFIX + importId + "-" + lineNo);
        bill.setDeleted(0);
        return bill;
    }

    private ParsedRow parseRow(String[] row, ColumnIndex columns) {
        String rawDate = column(row, columns.dateIdx);
        LocalDate recordDate = parseDate(rawDate);
        if (recordDate == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式非法");
        }

        String amountText = column(row, columns.amountIdx);
        BigDecimal amount = parseAmount(amountText);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "金额格式非法");
        }

        String typeText = column(row, columns.typeIdx);
        int billType = typeText != null && typeText.contains("收") && !typeText.contains("不") ? 1 : 0;

        String remark = column(row, columns.remarkIdx);
        if (remark != null && remark.length() > MAX_REMARK_LEN) {
            remark = remark.substring(0, MAX_REMARK_LEN);
        }

        ParsedRow parsed = new ParsedRow();
        parsed.amount = amount;
        parsed.billType = billType;
        parsed.recordDate = recordDate;
        parsed.remark = remark;
        parsed.categoryCode = resolveCategory(remark, billType);
        return parsed;
    }

    private String resolveCategory(String remark, int billType) {
        if (StringUtils.hasText(remark)) {
            for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
                if (remark.contains(entry.getKey())) {
                    String code = entry.getValue();
                    var category = dictService.getCategory(code);
                    if (category != null && category.getBillType() == billType) {
                        return code;
                    }
                }
            }
        }
        return billType == 1 ? UNCATEGORIZED_INCOME : UNCATEGORIZED_EXPENSE;
    }

    private String resolveChannel(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "来源类型不能为空");
        }
        String type = sourceType.trim().toUpperCase();
        if (!"WECHAT".equals(type) && !"ALIPAY".equals(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "来源类型仅支持 WECHAT / ALIPAY");
        }
        return type;
    }

    private String readContent(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            // 去除 UTF-8 BOM
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            return content;
        } catch (IOException e) {
            throw new BizException(ErrorCode.IMPORT_FORMAT_ERROR);
        }
    }

    /** 极简 CSV 解析：支持双引号包裹与转义引号。 */
    private List<String[]> parseCsv(String content) {
        List<String[]> rows = new ArrayList<>();
        for (String rawLine : content.split("\r\n|\r|\n")) {
            if (rawLine.isBlank()) {
                continue;
            }
            List<String> fields = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < rawLine.length(); i++) {
                char c = rawLine.charAt(i);
                if (c == '"') {
                    if (inQuotes && i + 1 < rawLine.length() && rawLine.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (c == ',' && !inQuotes) {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            fields.add(current.toString());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }

    private boolean isBlankRow(String[] row) {
        for (String field : row) {
            if (StringUtils.hasText(field)) {
                return false;
            }
        }
        return true;
    }

    private String column(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return null;
        }
        String value = row[index];
        return value == null ? null : value.trim();
    }

    private BigDecimal parseAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replace("¥", "").replace("￥", "")
                .replace(",", "").replace(" ", "").trim();
        // 部分导出带正负号或「元」
        normalized = normalized.replace("元", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        try {
            return new BigDecimal(normalized).abs();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().replace("/", "-").replace("年", "-")
                .replace("月", "-").replace("日", "");
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeFailDetail(List<ImportResultDTO.FailItem> failDetail) {
        StringBuilder sb = new StringBuilder();
        for (ImportResultDTO.FailItem item : failDetail) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append("第 ").append(item.getLine()).append(" 行：").append(item.getReason());
        }
        String text = sb.toString();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    /** 行解析结果 */
    private static class ParsedRow {
        private BigDecimal amount;
        private int billType;
        private String categoryCode;
        private LocalDate recordDate;
        private String remark;
    }

    /** CSV 表头列映射 */
    private static class ColumnIndex {
        private int dateIdx = -1;
        private int amountIdx = -1;
        private int typeIdx = -1;
        private int remarkIdx = -1;

        static ColumnIndex resolve(String[] header) {
            ColumnIndex index = new ColumnIndex();
            for (int i = 0; i < header.length; i++) {
                String name = header[i] == null ? "" : header[i].trim();
                if (index.dateIdx < 0 && (name.contains("交易时间") || name.contains("交易日期")
                        || name.contains("日期") || name.equalsIgnoreCase("date") || name.equalsIgnoreCase("time"))) {
                    index.dateIdx = i;
                } else if (index.amountIdx < 0 && (name.contains("金额") || name.equalsIgnoreCase("amount"))) {
                    index.amountIdx = i;
                } else if (index.typeIdx < 0 && (name.contains("收/支") || name.contains("收支")
                        || name.contains("类型") || name.equalsIgnoreCase("type"))) {
                    index.typeIdx = i;
                } else if (index.remarkIdx < 0 && (name.contains("商品说明") || name.contains("商品")
                        || name.contains("备注") || name.contains("交易对方") || name.contains("摘要")
                        || name.equalsIgnoreCase("remark"))) {
                    index.remarkIdx = i;
                }
            }
            return index;
        }

        boolean valid() {
            return dateIdx >= 0 && amountIdx >= 0;
        }
    }
}