package com.smartledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类字典表。
 */
@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    /** 父级分类编码，NULL 表示一级分类 */
    private String parentCode;

    /** 1 一级，2 二级 */
    private Integer level;

    private String name;

    /** 0 支出，1 收入 */
    private Integer billType;

    private String icon;

    private Integer sortNo;

    /** 1 内置，0 自定义 */
    private Integer isSystem;

    /** 1 启用，0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}