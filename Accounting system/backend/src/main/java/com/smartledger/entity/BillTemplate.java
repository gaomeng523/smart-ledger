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
 * 快捷记账模板表（V2）。
 */
@Data
@TableName("bill_template")
public class BillTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String title;

    private BigDecimal amount;

    /** 0 支出，1 收入 */
    private Integer billType;

    private String payChannelCode;

    private String categoryCode;

    private String remark;

    private Integer useCount;

    private Integer sortNo;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}