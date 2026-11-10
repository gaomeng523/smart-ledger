package com.smartledger.interceptor;

import com.smartledger.common.constant.CommonConstants;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.JwtTokenProvider;
import com.smartledger.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器：校验 Authorization 头，并将 openid 写入 {@link UserContext}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(CommonConstants.AUTH_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(CommonConstants.BEARER_PREFIX)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = header.substring(CommonConstants.BEARER_PREFIX.length()).trim();
        try {
            String openid = jwtTokenProvider.parseOpenid(token);
            if (!StringUtils.hasText(openid)) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            UserContext.setOpenid(openid);
            return true;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}