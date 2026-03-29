package com.mickey.onlineordering.onlineorderingserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mickey.onlineordering.onlineorderingserver.config.WxPayConfig;
import com.mickey.onlineordering.onlineorderingserver.entity.Order;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderMapper;
import com.mickey.onlineordering.onlineorderingserver.utils.WxPaySignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付服务类
 * 实现微信支付 APIv3 的核心业务逻辑
 */
@Slf4j
@Service
public class WxPayService {
    
    @Autowired
    private WxPayConfig wxPayConfig;
    
    @Autowired
    private WxPayCertificateManager certificateManager;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    /**
     * NATIVE 统一下单
     * 生成扫码支付二维码
     *
     * @param orderId 订单ID
     * @return 二维码链接（code_url）
     * @throws Exception 下单失败抛出异常
     */
    public String nativePay(Long orderId) throws Exception {
        log.info("========================================");
        log.info("开始 NATIVE 统一下单，订单ID: {}", orderId);
        log.info("========================================");
        
        // 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        
        if (order.getStatus() != 0) {
            throw new BizException("订单状态不正确，无法支付");
        }
        
        // 构建请求体
        String requestBody = buildNativePayRequestBody(order);
        
        log.info("请求体:\n{}", requestBody);
        
        // 调用微信支付接口
        String url = "/v3/pay/transactions/native";
        String responseBody = sendRequest("POST", url, requestBody);
        
        log.info("统一下单响应:\n{}", responseBody);
        
        // 解析响应
        JsonNode response = objectMapper.readTree(responseBody);
        
        if (response.has("code")) {
            // 返回错误
            String code = response.get("code").asText();
            String message = response.get("message").asText();
            log.error("统一下单失败: code={}, message={}", code, message);
            throw new BizException("微信支付下单失败: " + message);
        }
        
        String codeUrl = response.get("code_url").asText();
        log.info("✅ 统一下单成功，二维码链接: {}", codeUrl);
        
        return codeUrl;
    }
    
    /**
     * 构建 NATIVE 支付请求体
     *
     * @param order 订单对象
     * @return JSON 请求体
     */
    private String buildNativePayRequestBody(Order order) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        
        // APPID
        root.put("appid", wxPayConfig.getAppid());
        
        // 商户号
        root.put("mchid", wxPayConfig.getMchId());
        
        // 商品描述
        root.put("description", "订单支付-" + order.getOrderNo());
        
        // 商户订单号
        root.put("out_trade_no", order.getOrderNo());
        
        // 通知地址
        root.put("notify_url", wxPayConfig.getNotifyUrl());
        
        // 订单金额
        ObjectNode amountNode = objectMapper.createObjectNode();
        // 金额单位：分
        int total = order.getTotalAmount().multiply(new BigDecimal(100)).intValue();
        amountNode.put("total", total);
        amountNode.put("currency", "CNY");
        root.set("amount", amountNode);
        
        return objectMapper.writeValueAsString(root);
    }
    
    /**
     * 查询订单
     * 主动查询订单支付状态（兜底一致性）
     *
     * @param orderNo 商户订单号
     * @return 订单信息
     * @throws Exception 查询失败抛出异常
     */
    public Map<String, Object> queryOrder(String orderNo) throws Exception {
        log.info("========================================");
        log.info("查询订单支付状态，订单号: {}", orderNo);
        log.info("========================================");
        
        String url = String.format("/v3/pay/transactions/out-trade-no/%s?mchid=%s", 
                orderNo, wxPayConfig.getMchId());
        
        String responseBody = sendRequest("GET", url, "");
        
        log.info("查询订单响应:\n{}", responseBody);
        
        // 解析响应
        JsonNode response = objectMapper.readTree(responseBody);
        
        Map<String, Object> result = new HashMap<>();
        
        if (response.has("code")) {
            // 查询失败
            String code = response.get("code").asText();
            String message = response.get("message").asText();
            log.warn("查询订单失败: code={}, message={}", code, message);
            result.put("success", false);
            result.put("message", message);
        } else {
            // 查询成功
            String tradeState = response.get("trade_state").asText();
            String tradeStateDesc = response.get("trade_state_desc").asText();
            
            result.put("success", true);
            result.put("trade_state", tradeState);
            result.put("trade_state_desc", tradeStateDesc);
            result.put("out_trade_no", response.get("out_trade_no").asText());
            
            if (response.has("transaction_id")) {
                result.put("transaction_id", response.get("transaction_id").asText());
            }
            
            log.info("✅ 订单状态: {}, {}", tradeState, tradeStateDesc);
        }
        
        return result;
    }
    
    /**
     * 处理支付回调
     * 验签 + 解密 + 更新订单状态
     *
     * @param headers  请求头
     * @param body     请求体
     * @return 处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handlePaymentNotify(Map<String, String> headers, String body) {
        log.info("========================================");
        log.info("收到微信支付回调通知");
        log.info("========================================");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 验签
            boolean verifyResult = verifySignature(headers, body);
            if (!verifyResult) {
                log.error("❌ 验签失败");
                result.put("code", "FAIL");
                result.put("message", "验签失败");
                return result;
            }
            
            log.info("✅ 验签通过");
            
            // 2. 解密 resource
            JsonNode notification = objectMapper.readTree(body);
            JsonNode resource = notification.get("resource");
            
            String associatedData = resource.get("associated_data").asText();
            String nonce = resource.get("nonce").asText();
            String ciphertext = resource.get("ciphertext").asText();
            
            String plainText = WxPaySignatureUtil.decryptToString(
                    associatedData, nonce, ciphertext, wxPayConfig.getApiV3Key()
            );
            
            log.info("✅ 解密成功");
            log.info("明文:\n{}", plainText);
            
            // 3. 解析支付结果
            JsonNode paymentResult = objectMapper.readTree(plainText);
            
            String outTradeNo = paymentResult.get("out_trade_no").asText();
            String tradeState = paymentResult.get("trade_state").asText();
            String transactionId = paymentResult.get("transaction_id").asText();
            
            log.info("订单号: {}", outTradeNo);
            log.info("支付状态: {}", tradeState);
            log.info("微信支付订单号: {}", transactionId);
            
            // 4. 幂等性检查 + 更新订单状态
            if ("SUCCESS".equals(tradeState)) {
                updateOrderStatus(outTradeNo, transactionId);
            }
            
            // 5. 返回成功响应
            result.put("code", "SUCCESS");
            result.put("message", "成功");
            
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            result.put("code", "FAIL");
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 验证回调签名
     *
     * @param headers 请求头
     * @param body    请求体
     * @return 验签是否通过
     */
    private boolean verifySignature(Map<String, String> headers, String body) {
        String timestamp = headers.get("wechatpay-timestamp");
        String nonce = headers.get("wechatpay-nonce");
        String signature = headers.get("wechatpay-signature");
        String serialNo = headers.get("wechatpay-serial");
        
        log.info("时间戳: {}", timestamp);
        log.info("随机串: {}", nonce);
        log.info("签名: {}", signature);
        log.info("平台证书序列号: {}", serialNo);
        
        // 获取平台证书
        X509Certificate certificate = certificateManager.getCertificate(serialNo);
        if (certificate == null) {
            log.error("未找到平台证书: {}", serialNo);
            return false;
        }
        
        // 验签
        return WxPaySignatureUtil.verify(timestamp, nonce, body, signature, certificate);
    }
    
    /**
     * 更新订单状态（幂等处理）
     *
     * @param orderNo       商户订单号
     * @param transactionId 微信支付订单号
     */
    private void updateOrderStatus(String orderNo, String transactionId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.error("订单不存在: {}", orderNo);
            throw new BizException("订单不存在");
        }
        
        // 幂等性检查
        if (order.getStatus() != 0) {
            log.warn("订单状态不是待支付，跳过更新: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }
        
        // 更新订单状态
        order.setStatus(1); // 1-待接单（已支付）
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        // 可以在 remark 中记录微信支付订单号
        String remark = order.getRemark() != null ? order.getRemark() : "";
        order.setRemark(remark + " [微信支付订单号:" + transactionId + "]");
        
        int result = orderMapper.update(order);
        if (result > 0) {
            log.info("✅ 订单状态更新成功: orderNo={}, status={}", orderNo, order.getStatus());
        } else {
            log.error("❌ 订单状态更新失败: {}", orderNo);
            throw new BizException("订单状态更新失败");
        }
    }
    
    /**
     * 发送 HTTP 请求到微信支付接口
     *
     * @param method HTTP 方法
     * @param url    API 路径
     * @param body   请求体
     * @return 响应体
     * @throws Exception 请求失败抛出异常
     */
    private String sendRequest(String method, String url, String body) throws Exception {
        long timestamp = WxPaySignatureUtil.getCurrentTimestamp();
        String nonceStr = WxPaySignatureUtil.generateNonceStr();
        
        // 生成签名
        String signature = WxPaySignatureUtil.sign(
                method, url, timestamp, nonceStr, body, 
                wxPayConfig.getMerchantPrivateKey()
        );
        
        // 构建 Authorization 头
        String authorization = buildAuthorization(signature, nonceStr, timestamp);
        
        log.debug("Authorization: {}", authorization);
        
        // 构建请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(wxPayConfig.getApiUrl(url)))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Online-Ordering-System");
        
        if ("POST".equals(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            requestBuilder.GET();
        }
        
        HttpRequest request = requestBuilder.build();
        
        // 发送请求
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        log.info("HTTP 响应状态: {}", response.statusCode());
        
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("请求失败: {}", response.body());
        }
        
        return response.body();
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

