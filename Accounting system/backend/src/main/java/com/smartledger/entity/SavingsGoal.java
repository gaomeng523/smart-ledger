package com.smartledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 心愿储蓄表（V3）：买手机、旅行基金等目标储蓄。
 */
@Data
@TableName("savings_goal")
public class SavingsGoal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String title;

    /** 图标 emoji */
    private String icon;

    /** 目标金额 */
    private BigDecimal targetAmount;

    /** 已存金额 */
    private BigDecimal savedAmount;

    /** 计划达成日，可空 */
    private LocalDate dueDate;

    /** 关联分类编码，可空 */
    private String categoryCode;

    /** 0 进行中 1 已达成 2 已放弃 */
    private Integer status;

    /** 0 正常 1 删除（逻辑删除，由 MyBatis-Plus 全局配置接管） */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}