package com.smartledger.service;

import com.smartledger.dto.request.BudgetSaveRequest;
import com.smartledger.dto.response.BudgetDTO;

/**
 * 预算应用服务（V2）。
 */
public interface BudgetService {

    /** 设置预算（同月同分类重复提交即覆盖更新） */
    BudgetDTO save(String openid, BudgetSaveRequest request);

    /** 查询某月预算与使用情况 */
    BudgetDTO get(String openid, String month);

    /** 删除预算 */
    void delete(String openid, Long budgetId);
}