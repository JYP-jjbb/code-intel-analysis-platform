package com.mickey.onlineordering.onlineorderingserver.exception;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常类
 * 用于抛出业务逻辑相关的异常
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private Integer code;
    
    /**
     * 错误信息
     */
    private String message;
    
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
    }
    
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 简化构造函数（使用默认错误码）
     * 用于快速抛出业务异常
     */
    public BizException(String message) {
        super(message);
        this.code = ErrorCode.BIZ_ERROR.getCode();
        this.message = message;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
}






