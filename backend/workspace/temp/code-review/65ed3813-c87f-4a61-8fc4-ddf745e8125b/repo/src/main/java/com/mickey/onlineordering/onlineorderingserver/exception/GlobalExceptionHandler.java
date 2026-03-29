package com.mickey.onlineordering.onlineorderingserver.exception;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理系统中抛出的各类异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        return Result.error(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(ValidationException.class)
    public Result<?> handleValidationException(ValidationException e) {
        log.error("参数校验异常：{}", e.getMessage(), e);
        return Result.error(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理方法参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("方法参数校验异常：{}", message, e);
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("参数绑定异常：{}", message, e);
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }
    
    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.error("约束违反异常：{}", message, e);
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常：{}", e.getMessage(), e);
        return Result.error(ErrorCode.PARAM_ERROR, e.getMessage());
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常：{}", e.getMessage(), e);
        // 开发环境返回详细错误信息，生产环境返回通用提示
        String message = e.getMessage() != null ? e.getMessage() : "系统异常，请稍后重试";
        return Result.error(ErrorCode.SYSTEM_ERROR, message);
    }
    
    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("未知异常：{}", e.getMessage(), e);
        // 开发环境返回详细错误信息，生产环境返回通用提示
        String message = e.getMessage() != null ? e.getMessage() : "系统异常，请稍后重试";
        return Result.error(ErrorCode.SYSTEM_ERROR, message);
    }
}












