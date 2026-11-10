package com.smartledger.common.enums;

import lombok.Getter;

/**
 * 收支类型枚举。
 */
@Getter
public enum BillTypeEnum {

    EXPENSE(0, "支出"),
    INCOME(1, "收入");

    private final int code;
    private final String desc;

    BillTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BillTypeEnum of(Integer code) {
        if (code == null) {
            return EXPENSE;
        }
        return code == 1 ? INCOME : EXPENSE;
    }
}