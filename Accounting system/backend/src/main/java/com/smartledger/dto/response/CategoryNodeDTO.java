package com.smartledger.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 分类树节点（一级分类及其二级分类）。
 */
@Data
public class CategoryNodeDTO {

    private String code;

    private String name;

    /** 0 支出，1 收入 */
    private Integer billType;

    private String icon;

    private List<DictItemDTO> children;
}
