package com.mickey.onlineordering.onlineorderingserver.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * 微信支付签名和验签工具类
 * 实现 APIv3 的签名生成和验签逻辑
 */
@Slf4j
public class WxPaySignatureUtil {
    
    /**
     * 生成签名
     * 用于请求微信支付接口时的签名
     *
     * @param method      HTTP 方法（GET、POST 等）
     * @param url         请求 URL（不含域名，例如 /v3/pay/transactions/native）
     * @param timestamp   时间戳（秒）
     * @param nonceStr    随机字符串
     * @param body        请求体（GET 请求为空字符串）
     * @param privateKey  商户私钥
     * @return 签名字符串（Base64 编码）
     * @throws Exception 签名失败抛出异常
     */
    public static String sign(String method, String url, long timestamp, 
                              String nonceStr, String body, PrivateKey privateKey) throws Exception {
        // 构建待签名字符串
        String signatureStr = buildSignatureString(method, url, timestamp, nonceStr, body);
        
        log.debug("待签名字符串:\n{}", signatureStr);
        
        // 使用商户私钥进行 SHA256withRSA 签名
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(signatureStr.getBytes(StandardCharsets.UTF_8));
        
        byte[] signatureBytes = sign.sign();
        String signature = Base64.getEncoder().encodeToString(signatureBytes);
        
        log.debug("生成签名: {}", signature);
        
        return signature;
    }
    
    /**
     * 构建待签名字符串
     * 格式: HTTP请求方法\nURL\n请求时间戳\n请求随机串\n请求报文主体\n
     *
     * @param method    HTTP 方法
     * @param url       请求 URL
     * @param timestamp 时间戳
     * @param nonceStr  随机字符串
     * @param body      请求体
     * @return 待签名字符串
     */
    private static String buildSignatureString(String method, String url, long timestamp, 
                                                String nonceStr, String body) {
        return method + "\n"
                + url + "\n"
                + timestamp + "\n"
                + nonceStr + "\n"
                + body + "\n";
    }
    
    /**
     * 验证签名
     * 用于验证微信支付回调的签名
     *
     * @param timestamp   时间戳（从请求头 Wechatpay-Timestamp 获取）
     * @param nonceStr    随机字符串（从请求头 Wechatpay-Nonce 获取）
     * @param body        响应体
     * @param signature   签名（从请求头 Wechatpay-Signature 获取）
     * @param certificate 平台证书
     * @return 验签是否通过
     */
    public static boolean verify(String timestamp, String nonceStr, String body, 
                                  String signature, X509Certificate certificate) {
        try {
            // 构建应答签名串
            String signatureStr = buildResponseSignatureString(timestamp, nonceStr, body);
            
            log.debug("应答签名串:\n{}", signatureStr);
            log.debug("待验证签名: {}", signature);
            
            // 使用平台公钥进行验签
            PublicKey publicKey = certificate.getPublicKey();
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(publicKey);
            sign.update(signatureStr.getBytes(StandardCharsets.UTF_8));
            
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            boolean result = sign.verify(signatureBytes);
            
            log.info("验签结果: {}", result ? "通过" : "失败");
            
            return result;
        } catch (Exception e) {
            log.error("验签失败", e);
            return false;
        }
    }
    
    /**
     * 构建应答签名串
     * 格式: 应答时间戳\n应答随机串\n应答报文主体\n
     *
     * @param timestamp 时间戳
     * @param nonceStr  随机字符串
     * @param body      响应体
     * @return 应答签名串
     */
    private static String buildResponseSignatureString(String timestamp, String nonceStr, String body) {
        return timestamp + "\n"
                + nonceStr + "\n"
                + body + "\n";
    }
    
    /**
     * 解密回调数据
     * 使用 APIv3 密钥解密 resource 字段
     *
     * @param associatedData 附加数据（通常是空字符串）
     * @param nonce          随机串（从 resource.nonce 获取）
     * @param ciphertext     密文（从 resource.ciphertext 获取，Base64 编码）
     * @param apiV3Key       APIv3 密钥
     * @return 解密后的明文
     * @throws Exception 解密失败抛出异常
     */
    public static String decryptToString(String associatedData, String nonce, 
                                         String ciphertext, String apiV3Key) throws Exception {
        try {
            log.debug("开始解密回调数据");
            log.debug("associatedData: {}", associatedData);
            log.debug("nonce: {}", nonce);
            log.debug("ciphertext 长度: {}", ciphertext.length());
            
            // Base64 解码密文
            byte[] encryptedData = Base64.getDecoder().decode(ciphertext);
            
            // 使用 AES-256-GCM 解密
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            // 设置附加数据
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            
            // 解密
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String result = new String(decryptedData, StandardCharsets.UTF_8);
            
            log.debug("解密成功，明文长度: {}", result.length());
            
            return result;
        } catch (Exception e) {
            log.error("解密失败", e);
            throw new Exception("解密回调数据失败", e);
        }
    }
    
    /**
     * 生成随机字符串
     */
    public static String generateNonceStr() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 获取当前时间戳（秒）
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000;
    }
}



