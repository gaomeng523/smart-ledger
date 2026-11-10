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
 * 账单表（核心业务表）。
 */
@Data
@TableName("bill")
public class Bill {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private String openid;

    /** 所属账本，0 表示个人默认账本 */
    private Long bookId;

    /** 金额（恒为正数） */
    private BigDecimal amount;

    /** 0 支出，1 收入 */
    private Integer billType;

    private String payChannelCode;

    private String categoryCode;

    /** 用户原始输入文本 */
    private String recordText;

    private String remark;

    /** 记账日期（统计口径以此为准） */
    private LocalDate recordDate;

    /** 1 文本 2 语音 3 模板 4 导入 5 OCR 6 手动 */
    private Integer source;

    /** AI 解析置信度 */
    private BigDecimal confidence;

    private String tags;

    /** 账单图片 URL（拍照记账），V3 阶段二 */
    private String imageUrl;

    /** 幂等标识 */
    private String requestId;

    /** 0 正常，1 已删除（逻辑删除） */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}