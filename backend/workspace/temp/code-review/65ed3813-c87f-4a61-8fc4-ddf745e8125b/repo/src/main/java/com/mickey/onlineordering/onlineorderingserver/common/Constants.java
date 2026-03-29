package com.mickey.onlineordering.onlineorderingserver.common;

/**
 * 全局常量类
 * 定义系统中使用的各种常量
 */
public class Constants {

    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USERNAME = "username";
    public static final String SESSION_USER_ROLE = "userRole";
    public static final String SESSION_CAPTCHA = "captcha";
    public static final String HEADER_TOKEN = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final Integer DEFAULT_PAGE_SIZE = 10;
    public static final Integer DEFAULT_PAGE_NUM = 1;
    public static final Integer MAX_PAGE_SIZE = 100;
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";
    public static final Integer ORDER_STATUS_PENDING = 0;
    public static final Integer ORDER_STATUS_PAID = 1;
    public static final Integer ORDER_STATUS_PREPARING = 2;
    public static final Integer ORDER_STATUS_DELIVERING = 3;
    public static final Integer ORDER_STATUS_COMPLETED = 4;
    public static final Integer ORDER_STATUS_CANCELLED = 5;
    public static final Long CAPTCHA_EXPIRE_TIME = 300L;
    public static final Long TOKEN_EXPIRE_TIME = 86400L;
    public static final String DEFAULT_AVATAR = "/images/default-avatar.png";
}








