package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 疑似重复记账检测结果（V2）：作为错误码 1005 的 data 返回。
 */
@Data
public class DuplicateResultDTO {

    private List<Candidate> candidates = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class Candidate {

        private Long billId;

        private String recordDate;

        private BigDecimal amount;

        private String categoryName;
    }
}