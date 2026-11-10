package com.smartledger.common.util;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 标签工具：账单标签以逗号分隔存储于 {@code bill.tags}，不单独建表（见《04》§4.6）。
 *
 * <p>约束：单条账单最多 5 个标签；单个标签 ≤ 12 字符；同一账单内去重。</p>
 */
public final class TagUtils {

    public static final int MAX_TAGS = 5;
    public static final int MAX_TAG_LENGTH = 12;

    private TagUtils() {
    }

    /** 拆分存储串为标签列表（无标签返回空列表）。 */
    public static List<String> split(String tags) {
        List<String> result = new ArrayList<>();
        if (tags == null || tags.isBlank()) {
            return result;
        }
        for (String part : tags.split(",")) {
            String tag = part.trim();
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        return result;
    }

    /**
     * 校验并归一化标签列表，返回可入库的存储串；无标签返回 null。
     */
    public static String normalizeToStorage(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String tag = raw.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new BizException(ErrorCode.PARAM_ERROR, "单个标签不能超过 " + MAX_TAG_LENGTH + " 个字符");
            }
            distinct.add(tag);
        }
        if (distinct.size() > MAX_TAGS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标签数量不能超过 " + MAX_TAGS + " 个");
        }
        return distinct.isEmpty() ? null : String.join(",", distinct);
    }
}