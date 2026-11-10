package com.smartledger.service;

import com.smartledger.dto.request.TemplateSaveRequest;
import com.smartledger.dto.request.TemplateUpdateRequest;
import com.smartledger.dto.response.TemplateDTO;
import com.smartledger.dto.response.TemplateUseResultDTO;

import java.util.List;

/**
 * 快捷模板应用服务（V2）。
 */
public interface TemplateService {

    /** 新增模板 */
    TemplateDTO create(String openid, TemplateSaveRequest request);

    /** 模板列表（useCount DESC, sortNo ASC） */
    List<TemplateDTO> list(String openid);

    /** 修改模板 */
    void update(String openid, TemplateUpdateRequest request);

    /** 逻辑删除模板 */
    void delete(String openid, Long templateId);

    /** 一键记账：直接落库，source=3，useCount 自增 */
    TemplateUseResultDTO use(String openid, Long templateId);
}