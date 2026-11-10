package com.smartledger.service;

import com.smartledger.dto.request.SavingsAmountRequest;
import com.smartledger.dto.request.SavingsGoalSaveRequest;
import com.smartledger.dto.response.SavingsGoalDTO;

import java.util.List;

/**
 * 心愿储蓄服务（V3）。
 */
public interface SavingsService {

    /** 心愿列表（进行中在前，已达成/已放弃在后） */
    List<SavingsGoalDTO> list(String openid);

    /** 新建心愿 */
    SavingsGoalDTO create(String openid, SavingsGoalSaveRequest request);

    /** 编辑心愿 */
    SavingsGoalDTO update(String openid, SavingsGoalSaveRequest request);

    /** 存入 / 取出 */
    SavingsGoalDTO changeAmount(String openid, SavingsAmountRequest request);

    /** 放弃心愿（逻辑删除） */
    void delete(String openid, Long id);
}