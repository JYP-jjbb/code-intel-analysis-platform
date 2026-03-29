package com.mickey.onlineordering.onlineorderingserver.exception;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参数校验异常类
 * 用于抛出参数校验相关的异常
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ValidationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private Integer code;
    
    /**
     * 错误信息
     */
    private String message;
    
    public ValidationException(String message) {
        super(message);
        this.code = ErrorCode.PARAM_ERROR.getCode();
        this.message = message;
    }
    
    public ValidationException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
}












