package com.smartledger.ai;

import com.smartledger.ai.rag.RagExample;
import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统 Prompt 构建器。分类与渠道由字典表动态拼接，保证模型只能从合法取值中选择。
 */
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final DictService dictService;

    /**
     * 文本解析 Prompt：一句话或一段账单文本。
     */
    public String buildSystemPrompt() {
        return buildSystemPrompt(List.of());
    }

    /**
     * 文本解析 Prompt（带检索示例）：把语义相近的历史记账作为 few-shot，
     * 统一分类口径，尤其改善口语化、商户名等没有明显关键词的描述。
     */
    public String buildSystemPrompt(List<RagExample> examples) {
        return buildPrompt("你是专业记账文本解析器。用户会给你一句话描述一笔收支，或粘贴一段账单文本。", examples);
    }

    /**
     * 图片识别 Prompt：购物小票、支付截图或账单照片。
     */
    public String buildReceiptPrompt() {
        return buildPrompt("你是专业小票识别助手。用户会给你一张购物小票、支付成功截图或账单照片。"
                + "先看清图片上的商户名、实付金额、支付方式和日期，再输出 JSON。"
                + "amount 取实付总额（不是优惠前金额，也不是找零），看不清填 0；商户名写进 remark。", List.of());
    }

    private String buildPrompt(String role, List<RagExample> examples) {
        String channels = dictService.listChannels().stream()
                .map(item -> item.getName() + "(" + item.getCode() + ")")
                .collect(Collectors.joining("、"));

        String expenseCategories = buildCategoryText(0);

        String incomeCategories = buildCategoryText(1);

        String today = DateUtils.format(LocalDate.now());

        return role + "只输出纯净 JSON，不要解释、不要 markdown 代码块、不要多余文字。\n"
                + "严格按以下字段解析：\n\n"
                + "amount：金额，数字类型（保留两位小数），无法识别填 0\n"
                + "billType：0=支出，1=收入\n"
                + "payChannel：支付渠道，只能从以下取值中选择：" + channels + "\n"
                + "category：消费/收入分类。支出类只能从以下取值中选择：" + expenseCategories
                + "；收入类只能从以下取值中选择：" + incomeCategories + "\n"
                + "分类优先选择括号内的二级名称（如「外卖」）；实在无法细分时才使用括号外的一级名称（如「买菜餐饮」）\n"
                + "recordDate：日期，格式 yyyy-MM-dd，未说明日期默认今天（今天为 " + today + "）\n"
                + "remark：简短中文备注，概括本次消费\n"
                + "confidence：0~1 的小数，表示你对本次解析的把握程度\n\n"
                + buildExampleBlock(examples)
                + "请严格输出 JSON 对象。";
    }

    /**
     * 检索示例块。示例只用于统一分类口径，明确禁止照抄金额与日期。
     */
    private String buildExampleBlock(List<RagExample> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(
                "以下是历史相似记账，仅作分类口径与写法参考；金额、日期、支付方式一律以本次输入为准：\n");
        for (RagExample example : examples) {
            String categoryText = example.getCategoryCode();
            DictItemDTO category = dictService.getCategory(example.getCategoryCode());
            if (category != null) {
                categoryText = category.getName() + "（" + category.getCode() + "）";
            }
            sb.append("- 「").append(example.getText()).append("」→ 金额 ")
                    .append(example.getAmount() == null ? "0.00" : example.getAmount().toPlainString())
                    .append("，分类 ").append(categoryText)
                    .append("，").append(Integer.valueOf(1).equals(example.getBillType()) ? "收入" : "支出")
                    .append('\n');
        }
        return sb.append('\n').toString();
    }

    /**
     * 把某一收支类型下的分类拼成「一级（二级/二级/…）」格式，
     * 既让模型知道层级关系，也避免把所有二级平铺成一长串。
     */
    private String buildCategoryText(int billType) {
        List<DictItemDTO> all = dictService.listCategories().stream()
                .filter(item -> Integer.valueOf(billType).equals(item.getBillType()))
                .collect(Collectors.toList());

        Map<String, List<DictItemDTO>> childrenByParent = all.stream()
                .filter(item -> item.getLevel() != null && item.getLevel() == 2 && item.getParentCode() != null)
                .collect(Collectors.groupingBy(DictItemDTO::getParentCode));

        List<String> groups = new ArrayList<>();
        for (DictItemDTO item : all) {
            if (item.getLevel() != null && item.getLevel() == 2) {
                continue;
            }
            List<DictItemDTO> children = childrenByParent.get(item.getCode());
            if (children == null || children.isEmpty()) {
                groups.add(item.getName());
            } else {
                groups.add(item.getName() + "（"
                        + children.stream().map(DictItemDTO::getName).collect(Collectors.joining("/"))
                        + "）");
            }
        }
        return String.join("、", groups);
    }
}