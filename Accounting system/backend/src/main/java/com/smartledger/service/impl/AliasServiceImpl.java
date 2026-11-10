package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.dto.request.AliasSaveRequest;
import com.smartledger.dto.request.AliasUpdateRequest;
import com.smartledger.dto.response.AliasDTO;
import com.smartledger.dto.response.AliasResolveDTO;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.entity.AliasDict;
import com.smartledger.mapper.AliasDictMapper;
import com.smartledger.service.AliasService;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 别名词典应用服务实现。
 */
@Service
@RequiredArgsConstructor
public class AliasServiceImpl implements AliasService {

    private static final int TARGET_TYPE_CATEGORY = 1;
    private static final int TARGET_TYPE_CHANNEL = 2;

    private final AliasDictMapper aliasDictMapper;
    private final DictService dictService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AliasDTO create(String openid, AliasSaveRequest request) {
        String keyword = request.getKeyword().trim();
        validateTarget(request.getTargetType(), request.getTargetCode());
        if (existsKeyword(openid, keyword, null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该别名已存在");
        }

        AliasDict alias = new AliasDict();
        alias.setOpenid(openid);
        alias.setKeyword(keyword);
        alias.setTargetType(request.getTargetType());
        alias.setTargetCode(request.getTargetCode());
        alias.setHitCount(0);
        alias.setStatus(1);
        aliasDictMapper.insert(alias);
        return toDTO(alias);
    }

    @Override
    public List<AliasDTO> list(String openid) {
        List<AliasDict> aliases = aliasDictMapper.selectList(new LambdaQueryWrapper<AliasDict>()
                .eq(AliasDict::getOpenid, openid)
                .eq(AliasDict::getStatus, 1)
                .orderByDesc(AliasDict::getHitCount)
                .orderByDesc(AliasDict::getId));
        return aliases.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String openid, AliasUpdateRequest request) {
        AliasDict alias = requireOwned(openid, request.getAliasId());
        String keyword = request.getKeyword().trim();
        validateTarget(request.getTargetType(), request.getTargetCode());
        if (existsKeyword(openid, keyword, alias.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该别名已存在");
        }

        alias.setKeyword(keyword);
        alias.setTargetType(request.getTargetType());
        alias.setTargetCode(request.getTargetCode());
        aliasDictMapper.updateById(alias);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String openid, Long aliasId) {
        requireOwned(openid, aliasId);
        aliasDictMapper.deleteById(aliasId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AliasResolveDTO resolve(String openid, String text) {
        AliasResolveDTO result = new AliasResolveDTO();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        List<AliasDict> aliases = aliasDictMapper.selectList(new LambdaQueryWrapper<AliasDict>()
                .eq(AliasDict::getOpenid, openid)
                .eq(AliasDict::getStatus, 1));
        List<AliasDict> hits = new ArrayList<>();
        for (AliasDict alias : aliases) {
            if (StringUtils.hasText(alias.getKeyword()) && text.contains(alias.getKeyword())) {
                hits.add(alias);
                if (alias.getTargetType() != null && alias.getTargetType() == TARGET_TYPE_CATEGORY) {
                    result.setCategoryCode(alias.getTargetCode());
                } else if (alias.getTargetType() != null && alias.getTargetType() == TARGET_TYPE_CHANNEL) {
                    result.setChannelCode(alias.getTargetCode());
                }
            }
        }
        if (!hits.isEmpty()) {
            result.setHitCount(hits.size());
            for (AliasDict alias : hits) {
                alias.setHitCount(alias.getHitCount() == null ? 1 : alias.getHitCount() + 1);
                aliasDictMapper.updateById(alias);
            }
        }
        return result;
    }

    private void validateTarget(Integer targetType, String targetCode) {
        if (targetType == null || (targetType != TARGET_TYPE_CATEGORY && targetType != TARGET_TYPE_CHANNEL)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "映射目标类型非法");
        }
        if (targetType == TARGET_TYPE_CATEGORY) {
            DictItemDTO category = dictService.getCategory(targetCode);
            if (category == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在: " + targetCode);
            }
        } else if (dictService.getChannel(targetCode) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付渠道不存在: " + targetCode);
        }
    }

    private boolean existsKeyword(String openid, String keyword, Long excludeId) {
        LambdaQueryWrapper<AliasDict> wrapper = new LambdaQueryWrapper<AliasDict>()
                .eq(AliasDict::getOpenid, openid)
                .eq(AliasDict::getKeyword, keyword);
        if (excludeId != null) {
            wrapper.ne(AliasDict::getId, excludeId);
        }
        return aliasDictMapper.selectCount(wrapper) > 0;
    }

    private AliasDict requireOwned(String openid, Long aliasId) {
        AliasDict alias = aliasDictMapper.selectById(aliasId);
        if (alias == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "别名不存在");
        }
        if (!openid.equals(alias.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该别名");
        }
        return alias;
    }

    private AliasDTO toDTO(AliasDict alias) {
        AliasDTO dto = new AliasDTO();
        dto.setAliasId(alias.getId());
        dto.setKeyword(alias.getKeyword());
        dto.setTargetType(alias.getTargetType());
        dto.setTargetCode(alias.getTargetCode());
        dto.setHitCount(alias.getHitCount());
        if (alias.getTargetType() != null && alias.getTargetType() == TARGET_TYPE_CATEGORY) {
            dto.setTargetName(dictService.categoryName(alias.getTargetCode()));
        } else {
            dto.setTargetName(dictService.channelName(alias.getTargetCode()));
        }
        return dto;
    }
}