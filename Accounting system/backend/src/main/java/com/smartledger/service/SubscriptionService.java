package com.smartledger.service;

import com.smartledger.dto.request.SubscriptionSaveRequest;
import com.smartledger.dto.request.SubscriptionUpdateRequest;
import com.smartledger.dto.response.SubscriptionCandidateDTO;
import com.smartledger.dto.response.SubscriptionDTO;
import com.smartledger.entity.Bill;
import com.smartledger.entity.Subscription;

import java.util.List;

/**
 * 周期订阅服务（V3）：历史账单聚类识别、续费日滚动。
 */
public interface SubscriptionService {

    /** 我的订阅（正常 + 暂停），按下次扣费日升序 */
    List<SubscriptionDTO> list(String openid);

    /** 扫描近 6 个月账单，输出待确认的订阅候选（不入库） */
    List<SubscriptionCandidateDTO> suggest(String openid);

    /** 确认候选 / 手动新建订阅 */
    SubscriptionDTO create(String openid, SubscriptionSaveRequest request);

    /** 编辑订阅（名称 / 金额 / 周期 / 扣费日 / 暂停状态） */
    SubscriptionDTO update(String openid, SubscriptionUpdateRequest request);

    /** 删除订阅 */
    void delete(String openid, Long id);

    /**
     * 新账单落库后尝试匹配订阅并顺延扣费日；未命中返回 null。
     * 仅供记账后置流程调用，自身吞掉校验异常，不影响主流程。
     */
    Subscription consumeBill(String openid, Bill bill);
}
