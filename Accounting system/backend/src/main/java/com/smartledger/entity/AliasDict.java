package com.smartledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 别名词典表（V2）。
 */
@Data
@TableName("alias_dict")
public class AliasDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String keyword;

    /** 1 分类 2 渠道 */
    private Integer targetType;

    private String targetCode;

    private Integer hitCount;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}