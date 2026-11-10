package com.smartledger.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页响应结构。
 */
@Data
public class PageResult<T> {

    private long total;
    private long page;
    private long size;
    private long pages;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPage(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        result.setRecords(records);
        return result;
    }
}