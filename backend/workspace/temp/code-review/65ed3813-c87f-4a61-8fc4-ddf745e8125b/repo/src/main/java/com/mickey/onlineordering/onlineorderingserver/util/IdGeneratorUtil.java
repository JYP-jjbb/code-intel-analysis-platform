package com.mickey.onlineordering.onlineorderingserver.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * ID生成工具类
 * 用于生成订单号等唯一标识
 */
public class IdGeneratorUtil {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();
    
    /**
     * 生成订单号
     * 格式：yyyyMMddHHmmss + 6位随机数
     *
     * @return 订单号
     */
    public static String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        int randomNum = RANDOM.nextInt(900000) + 100000;
        return "ORD" + timestamp + randomNum;
    }
    
    /**
     * 生成验证码ID
     * 格式：时间戳 + 6位随机数
     *
     * @return 验证码ID
     */
    public static String generateCaptchaId() {
        long timestamp = System.currentTimeMillis();
        int randomNum = RANDOM.nextInt(900000) + 100000;
        return "CAPTCHA" + timestamp + randomNum;
    }
    
    private IdGeneratorUtil() {
        // 私有构造函数，防止实例化
    }
}











