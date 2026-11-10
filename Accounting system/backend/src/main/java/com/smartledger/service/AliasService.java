package com.smartledger.service;

import com.smartledger.dto.request.AliasSaveRequest;
import com.smartledger.dto.request.AliasUpdateRequest;
import com.smartledger.dto.response.AliasDTO;
import com.smartledger.dto.response.AliasResolveDTO;

import java.util.List;

/**
 * 别名词典应用服务（V2）。
 */
public interface AliasService {

    AliasDTO create(String openid, AliasSaveRequest request);

    List<AliasDTO> list(String openid);

    void update(String openid, AliasUpdateRequest request);

    void delete(String openid, Long aliasId);

    /**
     * 解析前别名匹配：返回命中的分类 / 渠道，并对命中别名累加 hitCount。
     */
    AliasResolveDTO resolve(String openid, String text);
}