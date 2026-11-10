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
 * 周期订阅表（V3）：会员、话费、房租等周期性支出。
 */
@Data
@TableName("subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String title;

    private String categoryCode;

    private String payChannelCode;

    /** 参考金额 */
    private BigDecimal avgAmount;

    /** 30 月 / 90 季 / 365 年 */
    private Integer cycleDays;

    /** 下次预计扣费日 */
    private LocalDate nextDate;

    private Long lastBillId;

    /** 已匹配账单次数 */
    private Integer sampleCount;

    /** 1 正常 0 暂停 */
    private Integer status;

    /** 0 正常 1 删除（逻辑删除，由 MyBatis-Plus 全局配置接管） */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
