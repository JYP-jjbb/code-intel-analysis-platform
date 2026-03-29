package com.mickey.onlineordering.onlineorderingserver.common;

/**
 * 错误码枚举类
 * 统一定义系统中使用的错误码和错误信息
 */
public enum ErrorCode {
    

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统错误"),
    BIZ_ERROR(1000, "业务错误"),
    LOGIN_ERROR(1001, "用户名或密码错误"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    CAPTCHA_ERROR(1003, "验证码错误"),
    CAPTCHA_EXPIRED(1004, "验证码已过期"),
    TOKEN_INVALID(1005, "Token无效"),
    TOKEN_EXPIRED(1006, "Token已过期"),
    STOCK_INSUFFICIENT(1007, "库存不足"),
    ORDER_NOT_FOUND(1008, "订单不存在"),
    ORDER_STATUS_ERROR(1009, "订单状态异常"),
    DISH_NOT_FOUND(1010, "菜品不存在"),
    USER_NOT_FOUND(1011, "用户不存在"),
    CART_EMPTY(1012, "购物车为空"),
    EMAIL_FORMAT_ERROR(1013, "邮箱格式错误"),
    EMAIL_EXISTS(1014, "邮箱已被注册"),
    EMAIL_CODE_EXPIRED(1015, "邮箱验证码已过期或不存在"),
    EMAIL_CODE_INVALID(1016, "邮箱验证码错误"),
    EMAIL_CODE_TOO_FREQUENT(1017, "邮箱验证码发送过于频繁，请稍后再试"),
    EMAIL_CODE_TRY_LIMIT(1018, "验证码错误次数过多，该邮箱已被锁定"),
    EMAIL_CODE_DAILY_LIMIT(1019, "该邮箱今日验证码发送次数已达上限"),
    IP_SEND_TOO_FREQUENT(1020, "您的请求过于频繁，请稍后再试"),
    EMAIL_LOCKED(1021, "该邮箱因多次验证失败已被锁定，请稍后再试"),
    EMAIL_SEND_FAILED(1022, "邮件发送失败，请稍后重试");

    private final Integer code;
    private final String message;
    
    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}









