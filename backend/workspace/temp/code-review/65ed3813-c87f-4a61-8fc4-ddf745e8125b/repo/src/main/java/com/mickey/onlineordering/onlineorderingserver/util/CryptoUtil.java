package com.mickey.onlineordering.onlineorderingserver.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 加密解密工具类
 * 用于前端传输参数加密
 */
@Slf4j
public class CryptoUtil {
    
    private static final String ALGORITHM = "AES";
    private static final String DEFAULT_KEY = "OnlineOrderKey16";
    
    /**
     * AES加密
     *
     * @param content 待加密内容
     * @param key 密钥
     * @return 加密后的Base64字符串
     */
    public static String encrypt(String content, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(encryptedBytes);
        } catch (Exception e) {
            log.error("加密失败：{}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * AES加密（使用默认密钥）
     *
     * @param content 待加密内容
     * @return 加密后的Base64字符串
     */
    public static String encrypt(String content) {
        return encrypt(content, DEFAULT_KEY);
    }
    
    /**
     * AES解密
     *
     * @param encryptedContent 加密后的Base64字符串
     * @param key 密钥
     * @return 解密后的内容
     */
    public static String decrypt(String encryptedContent, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.decodeBase64(encryptedContent));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败：{}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * AES解密（使用默认密钥）
     *
     * @param encryptedContent 加密后的Base64字符串
     * @return 解密后的内容
     */
    public static String decrypt(String encryptedContent) {
        return decrypt(encryptedContent, DEFAULT_KEY);
    }
    
    private CryptoUtil() {
        // 私有构造函数，防止实例化
    }
}











