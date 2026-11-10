package com.smartledger.dto.response;

import lombok.Data;

/**
 * 别名解析结果（V2，仅内部使用，不直接返回前端）。
 */
@Data
public class AliasResolveDTO {

    /** 命中的分类编码（未命中为 null） */
    private String categoryCode;

    /** 命中的渠道编码（未命中为 null） */
    private String channelCode;

    /** 命中的别名数量 */
    private int hitCount;
}