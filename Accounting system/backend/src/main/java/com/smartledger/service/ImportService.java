package com.smartledger.service;

import com.smartledger.dto.response.ImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 账单导入应用服务（V2）。
 */
public interface ImportService {

    /**
     * 导入 CSV 账单。
     *
     * @param dryRun true 时仅试算不入库
     */
    ImportResultDTO importBills(String openid, MultipartFile file, String sourceType, boolean dryRun);

    /** 撤销某批次导入的全部账单（逻辑删除） */
    void rollback(String openid, Long importId);
}