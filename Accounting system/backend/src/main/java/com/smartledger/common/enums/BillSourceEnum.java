package com.smartledger.common.enums;

import lombok.Getter;

/**
 * 账单来源枚举。
 */
@Getter
public enum BillSourceEnum {

    TEXT(1, "文本"),
    VOICE(2, "语音"),
    TEMPLATE(3, "模板"),
    IMPORT(4, "导入"),
    OCR(5, "OCR"),
    MANUAL(6, "手动");

    private final int code;
    private final String desc;

    BillSourceEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}