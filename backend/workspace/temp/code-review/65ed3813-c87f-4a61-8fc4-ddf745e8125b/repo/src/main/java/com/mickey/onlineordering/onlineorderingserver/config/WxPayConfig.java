package com.mickey.onlineordering.onlineorderingserver.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * 微信支付配置类
 * 读取 wxpay.properties 中的配置信息
 */
@Slf4j
@Data
@Configuration
@PropertySource("classpath:wxpay.properties")
@ConfigurationProperties(prefix = "wxpay")
public class WxPayConfig {

    private String mchId;
    private String mchSerialNo;
    private String privateKeyPath;
    private String apiV3Key;
    private String appid;
    private String domain;
    private String notifyDomain;
    private PrivateKey merchantPrivateKey;
    private String notifyUrl;

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("初始化微信支付配置");
        log.info("========================================");
        log.info("商户号: {}", mchId);
        log.info("商户证书序列号: {}", mchSerialNo);
        log.info("APPID: {}", appid);
        log.info("微信服务器地址: {}", domain);
        log.info("通知域名: {}", notifyDomain);
        
        // 拼接完整的回调地址
        // notify_url = wxpay.notify-domain + "/api/pay/wechat/notify"
        this.notifyUrl = notifyDomain + "/api/pay/wechat/notify";
        log.info("完整回调地址: {}", notifyUrl);
        
        // 加载商户私钥
        try {
            this.merchantPrivateKey = loadPrivateKey(privateKeyPath);
            log.info("✅ 商户私钥加载成功: {}", privateKeyPath);
        } catch (Exception e) {
            log.error("❌ 商户私钥加载失败: {}", privateKeyPath, e);
            throw new RuntimeException("微信支付配置初始化失败：无法加载商户私钥", e);
        }
        
        log.info("========================================");
        log.info("微信支付配置初始化完成");
        log.info("========================================");
    }
    
    /**
     * 加载商户私钥
     * 支持两种方式：
     * 1. classpath 路径（文件放在 src/main/resources/ 下）
     * 2. 绝对路径
     *
     * @param keyPath 私钥文件路径
     * @return PrivateKey 私钥对象
     * @throws Exception 加载失败抛出异常
     */
    private PrivateKey loadPrivateKey(String keyPath) throws Exception {
        log.info("尝试加载商户私钥: {}", keyPath);
        
        String privateKeyPEM;
        
        // 方式1: 尝试从 classpath 加载
        try {
            Resource resource = new ClassPathResource(keyPath);
            if (resource.exists()) {
                log.info("从 classpath 加载私钥: {}", keyPath);
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    privateKeyPEM = br.lines().collect(Collectors.joining("\n"));
                }
            } else {
                // 方式2: 尝试从绝对路径加载
                log.info("classpath 中未找到，尝试从绝对路径加载: {}", keyPath);
                File keyFile = new File(keyPath);
                if (!keyFile.exists()) {
                    throw new IOException("私钥文件不存在: " + keyPath);
                }
                try (BufferedReader br = new BufferedReader(
                        new FileReader(keyFile, StandardCharsets.UTF_8))) {
                    privateKeyPEM = br.lines().collect(Collectors.joining("\n"));
                }
            }
        } catch (IOException e) {
            log.error("读取私钥文件失败: {}", keyPath, e);
            throw e;
        }
        
        // 解析 PEM 格式的私钥
        return parsePrivateKey(privateKeyPEM);
    }
    
    /**
     * 解析 PEM 格式的私钥
     */
    private PrivateKey parsePrivateKey(String privateKeyPEM) throws Exception {
        // 移除 PEM 头尾和换行符
        String privateKeyContent = privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        
        // Base64 解码
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        
        // 生成私钥对象
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        
        return keyFactory.generatePrivate(keySpec);
    }
    
    /**
     * 获取完整的微信支付 API 地址
     */
    public String getApiUrl(String path) {
        return domain + path;
    }
}



