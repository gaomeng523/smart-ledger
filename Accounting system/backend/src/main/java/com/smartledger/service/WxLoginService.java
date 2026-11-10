package com.smartledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.JwtTokenProvider;
import com.smartledger.config.WxProperties;
import com.smartledger.dto.request.WxLoginRequest;
import com.smartledger.dto.response.LoginResponse;
import com.smartledger.entity.User;
import com.smartledger.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 微信登录服务：code2Session → 查询/创建用户 → 签发 JWT。
 */
@Slf4j
@Service
public class WxLoginService {

    private final RestClient wxRestClient;
    private final WxProperties wxProperties;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public WxLoginService(@Qualifier("wxRestClient") RestClient wxRestClient,
                          WxProperties wxProperties,
                          UserMapper userMapper,
                          JwtTokenProvider jwtTokenProvider,
                          ObjectMapper objectMapper) {
        this.wxRestClient = wxRestClient;
        this.wxProperties = wxProperties;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    public LoginResponse  login(WxLoginRequest request) {
        String openid = resolveOpenid(request.getCode());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));

        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(request.getNickname());
            user.setStatus(1);
            userMapper.insert(user);
            isNewUser = true;
        } else if (StringUtils.hasText(request.getNickname())
                && !request.getNickname().equals(user.getNickname())) {
            user.setNickname(request.getNickname());
            userMapper.updateById(user);
        }

        LoginResponse response = new LoginResponse();
        response.setToken(jwtTokenProvider.createToken(openid));
        response.setIsNewUser(isNewUser);
        response.setExpiresIn(jwtTokenProvider.getExpireSeconds());
        return response;
    }

    private String resolveOpenid(String code) {
        if (wxProperties.isMockEnabled()) {
            // 本地开发：无真实小程序凭据时，用 code 派生稳定的 mock openid
            String openid = "mock_" + md5(code).substring(0, 20);
            log.warn("微信登录处于 mock 模式，生成的 openid={}", openid);
            return openid;
        }
        if (!StringUtils.hasText(wxProperties.getAppId()) || !StringUtils.hasText(wxProperties.getAppSecret())) {
            throw new BizException(ErrorCode.WX_LOGIN_FAILED, "未配置微信 AppId / AppSecret");
        }
        try {
            String responseJson = wxRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                            .queryParam("appid", wxProperties.getAppId())
                            .queryParam("secret", wxProperties.getAppSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            int errcode = root.path("errcode").asInt(0);
            String openid = root.path("openid").asText(null);
            if (errcode != 0 || !StringUtils.hasText(openid)) {
                String errmsg = root.path("errmsg").asText("未知错误");
                log.warn("code2Session 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new BizException(ErrorCode.WX_LOGIN_FAILED, "微信登录失败: " + errmsg);
            }
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 code2Session 异常", e);
            throw new BizException(ErrorCode.WX_LOGIN_FAILED);
        }
    }

    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return Integer.toHexString(text == null ? 0 : text.hashCode());
        }
    }
}