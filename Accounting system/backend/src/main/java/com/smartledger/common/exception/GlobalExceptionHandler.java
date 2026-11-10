package com.smartledger.common.exception;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.result.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。统一收敛为 {@link R} 结构，避免向外暴露内部细节。
 *
 * <p>HTTP 状态码约定（与《04-接口设计说明书》§1.5 一致）：
 * 400/401/403/404/429/500 这类 4xx/5xx 错误码同时作为 HTTP 状态码返回；
 * 1xxx 属业务失败（如 AI 解析超时需回落到手动记账），业务上可正常处理，HTTP 仍返回 200。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Object>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return build(ErrorCode.PARAM_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(ErrorCode.PARAM_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return build(ErrorCode.PARAM_ERROR.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(ErrorCode.PARAM_ERROR.getCode(), "缺少必要参数: " + e.getParameterName(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResourceFound(NoResourceFoundException e) {
        // 未匹配到任何接口或静态资源（如浏览器自动请求的 favicon.ico），属正常情况，不打 error 日志
        log.debug("资源不存在: {}", e.getResourcePath());
        return build(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return build(ErrorCode.PARAM_ERROR.getCode(), "导入文件不能超过 5MB", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return build(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), null);
    }

    /**
     * 构造响应：错误码落在 4xx/5xx 区间时同步作为 HTTP 状态码，其余（含 1xxx 业务码）返回 200。
     */
    private static <T> ResponseEntity<R<T>> build(int code, String message, T data) {
        return ResponseEntity.status(httpStatusOf(code)).body(R.error(code, message, data));
    }

    private static HttpStatus httpStatusOf(int code) {
        if (code >= 400 && code < 600) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.OK;
    }
}