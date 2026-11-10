package com.smartledger.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 字典响应（渠道 + 分类）。
 */
@Data
public class DictResponse {

    private List<DictItemDTO> channels;

    private List<DictItemDTO> categories;

    /** 两级分类树：一级分类 + 其下二级分类 */
    private List<CategoryNodeDTO> categoryTree;
}