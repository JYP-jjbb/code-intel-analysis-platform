package com.mickey.onlineordering.onlineorderingserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mickey.onlineordering.onlineorderingserver.config.WxPayConfig;
import com.mickey.onlineordering.onlineorderingserver.utils.WxPaySignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付平台证书管理器
 * 负责获取、缓存和管理微信支付平台证书
 * 用于验证回调签名
 */
@Slf4j
@Component
public class WxPayCertificateManager {
    
    @Autowired
    private WxPayConfig wxPayConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 平台证书缓存（内存缓存）
     * Key: 证书序列号
     * Value: 平台证书对象
     */
    private final Map<String, X509Certificate> certificateCache = new ConcurrentHashMap<>();
    
    /**
     * 本地证书缓存目录
     */
    private static final String CACHE_DIR = "wxpay_certs";
    
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    /**
     * 初始化：尝试加载本地缓存的证书
     */
    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("初始化微信支付平台证书管理器");
        log.info("========================================");
        
        // 创建缓存目录
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
            log.info("创建证书缓存目录: {}", CACHE_DIR);
        }
        
        // 尝试从本地加载缓存的证书
        loadCachedCertificates();
        
        // 如果没有缓存或缓存过期，从微信服务器获取
        if (certificateCache.isEmpty()) {
            log.info("本地无缓存证书，开始从微信服务器获取...");
            try {
                downloadCertificates();
            } catch (Exception e) {
                log.error("获取平台证书失败", e);
            }
        }
        
        log.info("========================================");
        log.info("平台证书管理器初始化完成，已缓存证书数量: {}", certificateCache.size());
        log.info("========================================");
    }
    
    /**
     * 从本地文件加载缓存的证书
     */
    private void loadCachedCertificates() {
        File cacheDir = new File(CACHE_DIR);
        File[] certFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".pem"));
        
        if (certFiles == null || certFiles.length == 0) {
            log.info("本地无缓存证书");
            return;
        }
        
        for (File certFile : certFiles) {
            try {
                // 读取证书文件
                try (FileInputStream fis = new FileInputStream(certFile)) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate certificate = (X509Certificate) cf.generateCertificate(fis);
                    
                    // 检查证书是否有效
                    certificate.checkValidity();
                    
                    // 获取证书序列号
                    String serialNo = certificate.getSerialNumber().toString(16).toUpperCase();
                    
                    // 添加到缓存
                    certificateCache.put(serialNo, certificate);
                    log.info("✅ 加载本地证书: {} (序列号: {})", certFile.getName(), serialNo);
                }
            } catch (Exception e) {
                log.warn("加载证书失败: {}", certFile.getName(), e);
                // 删除无效证书文件
                certFile.delete();
            }
        }
    }
    
    /**
     * 从微信服务器下载平台证书
     * 调用 GET /v3/certificates 接口
     */
    public void downloadCertificates() throws Exception {
        log.info("开始从微信服务器下载平台证书...");
        
        String url = "/v3/certificates";
        String method = "GET";
        long timestamp = WxPaySignatureUtil.getCurrentTimestamp();
        String nonceStr = WxPaySignatureUtil.generateNonceStr();
        String body = "";
        
        // 生成签名
        String signature = WxPaySignatureUtil.sign(
                method, url, timestamp, nonceStr, body, 
                wxPayConfig.getMerchantPrivateKey()
        );
        
        // 构建 Authorization 头
        String authorization = buildAuthorization(signature, nonceStr, timestamp);
        
        // 发送请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(wxPayConfig.getApiUrl(url)))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .header("User-Agent", "Online-Ordering-System")
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        log.info("获取证书接口响应状态: {}", response.statusCode());
        
        if (response.statusCode() != 200) {
            log.error("获取证书失败: {}", response.body());
            throw new Exception("获取平台证书失败: HTTP " + response.statusCode());
        }
        
        // 解析响应
        parseCertificatesResponse(response.body());
    }
    
    /**
     * 解析证书接口的响应
     * 解密证书数据并缓存
     *
     * @param responseBody 响应体
     */
    private void parseCertificatesResponse(String responseBody) throws Exception {
        log.info("解析证书接口响应...");
        
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode dataArray = root.get("data");
        
        if (dataArray == null || !dataArray.isArray()) {
            throw new Exception("证书接口响应格式错误");
        }
        
        for (JsonNode certNode : dataArray) {
            try {
                // 获取证书序列号
                String serialNo = certNode.get("serial_no").asText();
                
                // 获取加密的证书信息
                JsonNode encryptCertificate = certNode.get("encrypt_certificate");
                String associatedData = encryptCertificate.get("associated_data").asText();
                String nonce = encryptCertificate.get("nonce").asText();
                String ciphertext = encryptCertificate.get("ciphertext").asText();
                
                // 解密证书
                String certificatePEM = WxPaySignatureUtil.decryptToString(
                        associatedData, nonce, ciphertext, wxPayConfig.getApiV3Key()
                );
                
                // 解析证书
                X509Certificate certificate = parseCertificate(certificatePEM);
                
                // 添加到缓存
                certificateCache.put(serialNo, certificate);
                log.info("✅ 获取平台证书: 序列号={}", serialNo);
                
                // 保存到本地文件
                saveCertificateToFile(serialNo, certificatePEM);
                
            } catch (Exception e) {
                log.error("处理证书失败", e);
            }
        }
    }
    
    /**
     * 解析 PEM 格式的证书
     *
     * @param certificatePEM PEM 格式的证书字符串
     * @return X509Certificate 证书对象
     */
    private X509Certificate parseCertificate(String certificatePEM) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                certificatePEM.getBytes(StandardCharsets.UTF_8)
        );
        return (X509Certificate) cf.generateCertificate(inputStream);
    }
    
    /**
     * 保存证书到本地文件
     *
     * @param serialNo      证书序列号
     * @param certificatePEM 证书内容
     */
    private void saveCertificateToFile(String serialNo, String certificatePEM) {
        try {
            File certFile = new File(CACHE_DIR, serialNo + ".pem");
            try (FileOutputStream fos = new FileOutputStream(certFile)) {
                fos.write(certificatePEM.getBytes(StandardCharsets.UTF_8));
            }
            log.info("证书已保存到本地: {}", certFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("保存证书到本地失败", e);
        }
    }
    
    /**
     * 根据序列号获取平台证书
     *
     * @param serialNo 证书序列号
     * @return 平台证书，如果不存在返回 null
     */
    public X509Certificate getCertificate(String serialNo) {
        X509Certificate certificate = certificateCache.get(serialNo);
        
        if (certificate == null) {
            log.warn("未找到序列号为 {} 的证书，尝试重新下载...", serialNo);
            try {
                downloadCertificates();
                certificate = certificateCache.get(serialNo);
            } catch (Exception e) {
                log.error("重新下载证书失败", e);
            }
        }
        
        return certificate;
    }
    
    /**
     * 构建 Authorization 头
     *
     * @param signature 签名
     * @param nonceStr  随机字符串
     * @param timestamp 时间戳
     * @return Authorization 头内容
     */
    private String buildAuthorization(String signature, String nonceStr, long timestamp) {
        return String.format(
                "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%d\",serial_no=\"%s\"",
                wxPayConfig.getMchId(),
                nonceStr,
                signature,
                timestamp,
                wxPayConfig.getMchSerialNo()
        );
    }
}

