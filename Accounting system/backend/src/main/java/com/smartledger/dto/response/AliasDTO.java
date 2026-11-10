package com.smartledger.dto.response;

import lombok.Data;

/**
 * 别名 DTO（V2）。
 */
@Data
public class AliasDTO {

    private Long aliasId;

    private String keyword;

    /** 1 分类 2 渠道 */
    private Integer targetType;

    private String targetCode;

    private String targetName;

    private Integer hitCount;
}