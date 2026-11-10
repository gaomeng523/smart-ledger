package com.smartledger.ai;

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

    public String buildSystemPrompt() {
        String channels = dictService.listChannels().stream()
                .map(item -> item.getName() + "(" + item.getCode() + ")")
                .collect(Collectors.joining("、"));

        String expenseCategories = buildCategoryText(0);

        String incomeCategories = buildCategoryText(1);

        String today = DateUtils.format(LocalDate.now());

        return "你是专业记账文本解析器。只输出纯净 JSON，不要解释、不要 markdown 代码块、不要多余文字。\n"
                + "严格按以下字段解析用户输入：\n\n"
                + "amount：金额，数字类型（保留两位小数），无法识别填 0\n"
                + "billType：0=支出，1=收入\n"
                + "payChannel：支付渠道，只能从以下取值中选择：" + channels + "\n"
                + "category：消费/收入分类。支出类只能从以下取值中选择：" + expenseCategories
                + "；收入类只能从以下取值中选择：" + incomeCategories + "\n"
                + "分类优先选择括号内的二级名称（如「外卖」）；实在无法细分时才使用括号外的一级名称（如「买菜餐饮」）\n"
                + "recordDate：日期，格式 yyyy-MM-dd，未说明日期默认今天（今天为 " + today + "）\n"
                + "remark：简短中文备注，概括本次消费\n"
                + "confidence：0~1 的小数，表示你对本次解析的把握程度\n\n"
                + "请严格输出 JSON 对象。";
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