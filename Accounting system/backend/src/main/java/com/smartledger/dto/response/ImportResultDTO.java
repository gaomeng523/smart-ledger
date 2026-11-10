package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果（V2）。
 */
@Data
public class ImportResultDTO {

    private Long importId;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    private List<FailItem> failDetail = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class FailItem {

        /** CSV 行号（含表头，从 1 开始） */
        private int line;

        private String reason;
    }
}