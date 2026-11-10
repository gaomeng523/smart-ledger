package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 字典项（分类 / 渠道通用）。
 */
@Data
@AllArgsConstructor
public class DictItemDTO {

    private String code;

    private String name;

    /** 分类专用：0 支出，1 收入 */
    private Integer billType;

    private Integer sortNo;

    /** 分类专用：父级编码，NULL=一级 */
    private String parentCode;

    /** 分类专用：1 一级，2 二级 */
    private Integer level;

    /** 分类专用：图标标识 */
    private String icon;
}
