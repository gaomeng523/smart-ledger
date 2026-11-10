package com.smartledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入批次表（V2）。
 */
@Data
@TableName("import_record")
public class ImportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String fileName;

    /** WECHAT / ALIPAY */
    private String sourceType;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    /** 0 处理中 1 成功 2 部分失败 3 已撤销 */
    private Integer status;

    private String failDetail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}