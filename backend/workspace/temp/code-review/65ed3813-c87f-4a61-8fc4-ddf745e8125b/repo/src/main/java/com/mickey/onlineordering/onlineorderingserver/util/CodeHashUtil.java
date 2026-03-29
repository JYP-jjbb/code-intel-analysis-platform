package com.mickey.onlineordering.onlineorderingserver.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 验证码Hash工具类
 * 使用SHA-256算法对验证码进行单向加密
 * 验证码在Redis中只存储Hash值，不存储明文，增强安全性
 */
@Slf4j
public class CodeHashUtil {
    
    private static final String ALGORITHM = "SHA-256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 生成随机盐值
     * 盐值用于增强Hash的安全性，防止彩虹表攻击
     *
     * @return Base64编码的盐值
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * 生成数字验证码
     * 默认生成6位数字验证码
     *
     * @return 6位数字验证码字符串
     */
    public static String generateCode() {
        return generateCode(6);
    }
    
    /**
     * 生成指定位数的数字验证码
     *
     * @param length 验证码位数
     * @return 数字验证码字符串
     */
    public static String generateCode(int length) {
        if (length <= 0 || length > 10) {
            throw new IllegalArgumentException("验证码长度必须在1-10之间");
        }
        
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        
        return code.toString();
    }
    
    /**
     * 计算验证码的Hash值
     * 使用SHA-256算法，结合盐值进行加密
     *
     * @param code 原始验证码
     * @param salt 盐值
     * @return Hash值（十六进制字符串）
     */
    public static String hashCode(String code, String salt) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("盐值不能为空");
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            
            // 将验证码和盐值组合后进行Hash
            String combined = code + salt;
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            return bytesToHex(hashBytes);
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash算法不存在: {}", ALGORITHM, e);
            throw new RuntimeException("验证码加密失败", e);
        }
    }
    
    /**
     * 验证验证码是否匹配
     *
     * @param inputCode 用户输入的验证码
     * @param salt      存储的盐值
     * @param storedHash 存储的Hash值
     * @return true-匹配成功，false-匹配失败
     */
    public static boolean verifyCode(String inputCode, String salt, String storedHash) {
        if (inputCode == null || salt == null || storedHash == null) {
            return false;
        }
        
        try {
            String inputHash = hashCode(inputCode, salt);
            return inputHash.equals(storedHash);
        } catch (Exception e) {
            log.error("验证码验证失败", e);
            return false;
        }
    }
    
    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}



