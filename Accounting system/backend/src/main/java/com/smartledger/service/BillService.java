package com.smartledger.service;

import com.smartledger.common.result.PageResult;
import com.smartledger.dto.request.BillListQuery;
import com.smartledger.dto.request.BillSaveRequest;
import com.smartledger.dto.request.BillUpdateRequest;
import com.smartledger.dto.response.BillDTO;
import com.smartledger.dto.response.SaveResultDTO;

import java.util.List;

/**
 * 账单应用服务。
 */
public interface BillService {

    /** 保存账单（含幂等与疑似重复校验） */
    SaveResultDTO save(String openid, BillSaveRequest request);

    /** 分页查询 */
    PageResult<BillDTO> page(String openid, BillListQuery query);

    /** 详情 */
    BillDTO detail(String openid, Long billId);

    /** 修改 */
    void update(String openid, BillUpdateRequest request);

    /** 逻辑删除 */
    void delete(String openid, Long billId);

    /** 历史标签联想（V2，按使用频次倒序，最多 20 个） */
    List<String> tagSuggest(String openid);
}