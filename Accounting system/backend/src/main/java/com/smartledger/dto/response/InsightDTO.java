package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消费洞察条目（V3）。数字均来自服务端聚合，AI 仅允许改写措辞。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightDTO {

    /** 规则类型：outlier / weekSurge / budgetOver / budgetWarn / repeat /
     *  monthCompare / highFreq / bigBill / subscription / subscriptionMatched */
    private String type;

    /** info / warning / danger */
    private String level;

    private String title;

    private String body;
}
