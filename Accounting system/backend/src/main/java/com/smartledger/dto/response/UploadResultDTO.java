package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图片上传结果（V3 阶段二，拍照记账）。
 */
@Data
@AllArgsConstructor
public class UploadResultDTO {

    /** 可访问的图片相对地址，如 /uploads/2026/10/xxx.jpg */
    private String url;
}