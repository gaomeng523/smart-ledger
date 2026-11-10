
package com.smartledger.service;

import com.smartledger.dto.response.InsightDTO;

import java.util.List;

/**
 * 消费洞察服务（V3）：记账后即时提示 + 月度洞察卡片。
 */
public interface InsightService {

    /** 保存账单后的即时洞察（通常 0~2 条，按严重程度取前两条） */
    List<InsightDTO> afterSave(String openid, Long billId);

    /** 指定月份的月度洞察（统计页卡片） */
    List<InsightDTO> monthInsights(String openid, String month);
}
