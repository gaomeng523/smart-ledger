package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 保存账单结果。
 */
@Data
@AllArgsConstructor
public class SaveResultDTO {

    private Long billId;

    /** true 表示因 requestId 重复而返回既有记录（幂等命中） */
    private boolean duplicated;

    /** 保存后的即时消费洞察（V3，可能为空列表） */
    private List<InsightDTO> insights;
}