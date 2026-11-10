package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.response.ImportResultDTO;
import com.smartledger.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 账单导入接口（V2）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /** 批量导入账单（dryRun=true 仅试算不入库） */
    @PostMapping("/bill/import")
    public R<ImportResultDTO> importBills(@RequestParam("file") MultipartFile file,
                                          @RequestParam("sourceType") String sourceType,
                                          @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return R.ok(importService.importBills(UserContext.requireOpenid(), file, sourceType, dryRun));
    }

    /** 撤销导入批次 */
    @PostMapping("/import/rollback")
    public R<Void> rollback(@RequestParam Long importId) {
        importService.rollback(UserContext.requireOpenid(), importId);
        return R.ok();
    }
}