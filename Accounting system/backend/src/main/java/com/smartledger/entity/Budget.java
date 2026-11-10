package com.smartledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算表（V2）：同一用户同一月份同一分类唯一，categoryCode 为空表示总预算。
 */
@Data
@TableName("budget")
public class Budget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    /** yyyy-MM */
    private String budgetMonth;

    /** 空 = 总预算 */
    private String categoryCode;

    private BigDecimal amount;

    private BigDecimal warnRatio;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}