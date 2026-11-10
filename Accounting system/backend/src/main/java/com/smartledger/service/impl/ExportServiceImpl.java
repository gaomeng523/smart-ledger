package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.entity.Bill;
import com.smartledger.mapper.BillMapper;
import com.smartledger.service.DictService;
import com.smartledger.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 账单 CSV 导出实现（F-91）。
 * UTF-8 BOM 保证 Excel 直接打开中文不乱码；字段按 RFC 4180 规则转义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    /** 单次导出上限，防止一次拉取过多数据 */
    private static final int EXPORT_LIMIT = 10000;

    private static final String[] HEADERS = {
            "记账日期", "类型", "分类", "支付渠道", "金额", "标签", "备注", "原始输入", "来源"
    };

    private static final Map<Integer, String> SOURCE_TEXT = Map.of(
            1, "文本",
            2, "语音",
            3, "模板",
            4, "导入",
            5, "OCR",
            6, "手动"
    );

    private final BillMapper billMapper;
    private final DictService dictService;

    @Override
    public void writeBillCsv(String openid, LocalDate start, LocalDate end, OutputStream out) {
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .ge(Bill::getRecordDate, start)
                .le(Bill::getRecordDate, end)
                .orderByDesc(Bill::getRecordDate)
                .orderByDesc(Bill::getId)
                .last("LIMIT " + EXPORT_LIMIT));

        Map<String, String> categoryNames = dictService.categoryNameMap();
        Map<String, String> channelNames = dictService.channelNameMap();

        try {
            // UTF-8 BOM（EF BB BF）：Excel 凭此识别编码，避免中文乱码
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);

            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write(String.join(",", HEADERS));
            writer.write("\r\n");

            for (Bill bill : bills) {
                String type = Integer.valueOf(1).equals(bill.getBillType()) ? "收入" : "支出";
                String category = categoryNames.getOrDefault(bill.getCategoryCode(),
                        bill.getCategoryCode() == null ? "" : bill.getCategoryCode());
                String channel = bill.getPayChannelCode() == null ? ""
                        : channelNames.getOrDefault(bill.getPayChannelCode(), bill.getPayChannelCode());
                String[] columns = {
                        bill.getRecordDate() == null ? "" : bill.getRecordDate().toString(),
                        type,
                        category,
                        channel,
                        bill.getAmount() == null ? "" : bill.getAmount().toPlainString(),
                        bill.getTags() == null ? "" : bill.getTags(),
                        bill.getRemark() == null ? "" : bill.getRemark(),
                        bill.getRecordText() == null ? "" : bill.getRecordText(),
                        SOURCE_TEXT.getOrDefault(bill.getSource() == null ? 0 : bill.getSource(), "其他")
                };
                writer.write(String.join(",", escape(columns)));
                writer.write("\r\n");
            }
            writer.flush();
        } catch (IOException e) {
            log.error("导出账单 CSV 失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导出失败，请稍后重试");
        }
    }

    /**
     * RFC 4180：含逗号、双引号、换行的字段用双引号包裹，内部双引号双写。
     */
    private String[] escape(String[] columns) {
        for (int i = 0; i < columns.length; i++) {
            String value = columns[i];
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                columns[i] = "\"" + value.replace("\"", "\"\"") + "\"";
            }
        }
        return columns;
    }
}
