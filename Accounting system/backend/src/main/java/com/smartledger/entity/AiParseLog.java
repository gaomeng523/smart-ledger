package com.smartledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 解析日志表。
 */
@Data
@TableName("ai_parse_log")
public class AiParseLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String inputText;

    private String outputJson;

    private String model;

    /** 1 成功，0 失败 */
    private Integer success;

    private String errorCode;

    private String errorMsg;

    private Integer costMs;

    private Integer promptTokens;

    private Integer completionTokens;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}