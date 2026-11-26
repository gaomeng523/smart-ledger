package com.smartledger.ai.ask;

import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能问答 Prompt：第一步让模型产出 SQL，第二步让模型把查询结果转成中文回答。
 *
 * <p>日期区间在服务端算好后直接写进 Prompt，避免模型自己推算「这个月」「上个月」出错。</p>
 */
@Component
@RequiredArgsConstructor
public class AskPromptBuilder {

    private final DictService dictService;

    /**
     * 生成 SQL 的 Prompt。
     */
    public String buildSqlPrompt() {
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.of(today.getYear(), today.getMonth());

        StringBuilder sb = new StringBuilder();
        sb.append("你是账单数据查询助手。用户会用中文问自己账本的问题，你要输出一条 MySQL 查询语句。\n\n");

        sb.append("【表结构】只有一张只读表 bill：\n")
                .append("- openid 用户标识（VARCHAR）\n")
                .append("- amount 金额，恒为正数（DECIMAL）\n")
                .append("- bill_type 0=支出，1=收入\n")
                .append("- category_code 分类编码（VARCHAR）\n")
                .append("- pay_channel_code 支付渠道编码（VARCHAR）\n")
                .append("- record_date 记账日期（DATE，统计一律以此字段为准）\n")
                .append("- remark 备注（VARCHAR）\n")
                .append("- deleted 0=正常，1=已删除\n\n");

        sb.append("【今天】").append(DateUtils.format(today)).append('\n');
        sb.append("【时间区间】查询统一写成 record_date >= '起点' AND record_date < '终点'（含首不含尾）：\n");
        appendRange(sb, "本月", current);
        appendRange(sb, "上月", current.minusMonths(1));
        appendRange(sb, "上上月", current.minusMonths(2));
        appendRange(sb, "近三个月", current.minusMonths(2), current.plusMonths(1));
        appendRange(sb, "去年同月", current.minusYears(1));
        sb.append('\n');

        sb.append("【分类】一级分类(编码)：其下二级分类\n").append(categoryText()).append('\n');
        sb.append("【支付渠道】名称(编码)：").append(channelText()).append("\n\n");

        sb.append("【硬性要求】\n")
                .append("1. 只能写一条 SELECT，只能查 bill 表；禁止 INSERT/UPDATE/DELETE/DDL/UNION/多表 JOIN；\n")
                .append("2. 必须带 openid = :openid 作为用户过滤条件，:openid 由服务端绑定，禁止写具体用户；\n")
                .append("3. 不要写 deleted = 0，服务端会自动补；\n")
                .append("4. 不要写分号、注释、EXPLAIN；\n")
                .append("5. 问一级分类（如「吃饭」「医疗」「购物」）时，必须把该一级及其全部二级编码写进 IN 列表，"
                        + "例如吃饭写 category_code IN ('FOOD','FOOD_GROCERY','FOOD_TAKEOUT','FOOD_DINING','FOOD_COFFEE','FOOD_SNACK')；"
                        + "不要用 LIKE 前缀猜测，因为一级编码与其子编码前缀并不总是一致（如医疗是 MEDICAL，子类是 MED_CLINIC）；\n")
                .append("6. 问二级分类时用 category_code = 'FOOD_TAKEOUT'；\n")
                .append("7. 支出用 bill_type = 0，收入用 bill_type = 1；\n")
                .append("8. 金额聚合用 ROUND(SUM(amount), 2)，笔数用 COUNT(*)，最大单笔用 MAX(amount)，平均用 ROUND(AVG(amount), 2)；\n")
                .append("9. 需要同时算多个口径（如本月与上月）时，用 SUM(CASE WHEN 条件 THEN amount ELSE 0 END) 在一条 SQL 里分别求和再相减，结果为一行；\n")
                .append("10. 只返回统计值，不要返回 id、openid、原始文本等隐私字段；列别名用英文小写。\n\n");

        sb.append("只输出 JSON：{\"sql\": \"SELECT ...\"}");
        return sb.toString();
    }

    /**
     * 把查询结果转成中文回答的 Prompt。
     */
    public String buildAnswerPrompt(String rowsJson) {
        return "你是记账助手。下面是用户问题对应的账单查询结果（JSON 数组），请用一句自然、口语化的中文回答。\n"
                + "要求：\n"
                + "1. 只使用结果里的数字，不要编造，也不要解释查询过程；\n"
                + "2. 金额保留两位小数并带「元」，笔数带「笔」；\n"
                + "3. 结果为空、或所有数值都为 0 时，直接说明这段时间没有相关账单；\n"
                + "4. 不超过 60 字。\n"
                + "查询结果：" + rowsJson + "\n"
                + "只输出 JSON：{\"answer\": \"...\"}";
    }

    private void appendRange(StringBuilder sb, String label, YearMonth month) {
        appendRange(sb, label, month, month.plusMonths(1));
    }

    private void appendRange(StringBuilder sb, String label, YearMonth start, YearMonth end) {
        sb.append("- ").append(label).append("：").append(start.atDay(1))
                .append(" ~ ").append(end.atDay(1)).append('\n');
    }

    private String categoryText() {
        List<DictItemDTO> all = dictService.listCategories();
        Map<String, List<DictItemDTO>> children = all.stream()
                .filter(item -> item.getLevel() != null && item.getLevel() == 2 && item.getParentCode() != null)
                .collect(Collectors.groupingBy(DictItemDTO::getParentCode));

        List<String> lines = new ArrayList<>();
        for (DictItemDTO item : all) {
            if (item.getLevel() != null && item.getLevel() == 2) {
                continue;
            }
            List<DictItemDTO> subs = children.getOrDefault(item.getCode(), List.of());
            String subText = subs.stream()
                    .map(sub -> sub.getName() + "(" + sub.getCode() + ")")
                    .collect(Collectors.joining("、"));
            lines.add("- " + item.getName() + "(" + item.getCode() + ")"
                    + (subText.isEmpty() ? "" : "：" + subText));
        }
        return String.join("\n", lines);
    }

    private String channelText() {
        return dictService.listChannels().stream()
                .map(item -> item.getName() + "(" + item.getCode() + ")")
                .collect(Collectors.joining("、"));
    }
}
