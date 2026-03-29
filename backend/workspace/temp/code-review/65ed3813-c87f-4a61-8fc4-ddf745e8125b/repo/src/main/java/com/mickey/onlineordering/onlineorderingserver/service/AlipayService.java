package com.mickey.onlineordering.onlineorderingserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mickey.onlineordering.onlineorderingserver.config.AlipayConfig;
import com.mickey.onlineordering.onlineorderingserver.entity.Order;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderMapper;
import com.mickey.onlineordering.onlineorderingserver.utils.AlipaySignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

/**
 * 支付宝沙箱扫码支付服务
 * - 交易预创建 alipay.trade.precreate -> 返回 qr_code
 * - 异步通知 /api/pay/alipay/notify 验签 + 更新订单状态
 */
@Slf4j
@Service
public class AlipayService {

    @Autowired(required = false)
    private AlipayConfig alipayConfig;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private PrivateKey merchantPrivateKey() {
        if (alipayConfig == null || alipayConfig.getMerchantPrivateKey() == null || alipayConfig.getMerchantPrivateKey().isBlank()) {
            throw new BizException("Alipay 未配置：merchant-private-key");
        }
        return AlipaySignatureUtil.loadPrivateKeyPkcs8(alipayConfig.getMerchantPrivateKey());
    }

    private PublicKey alipayPublicKey() {
        if (alipayConfig == null || alipayConfig.getAlipayPublicKey() == null || alipayConfig.getAlipayPublicKey().isBlank()) {
            throw new BizException("Alipay 未配置：alipay-public-key");
        }
        return AlipaySignatureUtil.loadPublicKey(alipayConfig.getAlipayPublicKey());
    }

    /**
     * 支付宝预下单（沙箱扫码支付）
     * @return qr_code 字符串（用它生成二维码即可）
     */
    public String precreate(Long orderId) throws Exception {
        log.info("========================================");
        log.info("开始 Alipay 预下单, orderId: {}", orderId);
        log.info("========================================");

        if (alipayConfig == null) {
            throw new BizException("Alipay 未启用");
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BizException("订单状态不正确，无法支付");
        }

        // biz_content
        ObjectNode biz = objectMapper.createObjectNode();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("total_amount", order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        biz.put("subject", "订单支付-" + order.getOrderNo());

        Map<String, String> params = new HashMap<>();
        params.put("app_id", alipayConfig.getAppId());
        params.put("method", "alipay.trade.precreate");
        params.put("format", alipayConfig.getFormat());
        params.put("charset", alipayConfig.getCharset());
        params.put("sign_type", alipayConfig.getSignType());
        params.put("timestamp", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        params.put("version", "1.0");
        params.put("notify_url", alipayConfig.getNotifyUrl());
        params.put("biz_content", biz.toString());

        String signContent = AlipaySignatureUtil.buildSignContent(params);
        String sign = AlipaySignatureUtil.signRsa2(signContent, merchantPrivateKey(), alipayConfig.getCharset());
        params.put("sign", sign);

        String formBody = AlipaySignatureUtil.buildFormBody(params, alipayConfig.getCharset());

        // ===== DEBUG PRINT (for troubleshooting invalid-signature) =====
        try {
            log.info("[ALIPAY DEBUG] signContent(raw): {}", signContent);
            log.info("[ALIPAY DEBUG] formBody(urlencoded): {}", formBody);

            // Extract and decode a few critical fields so we can compare with gateway string
            String decodedForm = URLDecoder.decode(formBody, Charset.forName(alipayConfig.getCharset()));
            log.info("[ALIPAY DEBUG] formBody(decoded): {}", decodedForm);
        } catch (Exception ignore) {
            // ignore logging failures
        }
        // ==============================================================

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(alipayConfig.getGatewayUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=" + alipayConfig.getCharset())
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info("Alipay gateway status: {}", response.statusCode());
        log.debug("Alipay gateway response: {}", response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("Alipay 网关请求失败: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode respNode = root.get("alipay_trade_precreate_response");
        if (respNode == null) {
            throw new BizException("Alipay 响应解析失败");
        }

        String code = respNode.path("code").asText();
        String msg = respNode.path("msg").asText();
        if (!"10000".equals(code)) {
            String subMsg = respNode.path("sub_msg").asText();
            throw new BizException("Alipay 预下单失败: " + msg + (subMsg == null || subMsg.isBlank() ? "" : (" - " + subMsg)));
        }

        String qrCode = respNode.path("qr_code").asText();
        if (qrCode == null || qrCode.isBlank()) {
            throw new BizException("Alipay 未返回 qr_code");
        }

        return qrCode;
    }

    /**
     * 主动查询支付宝订单状态（trade.query）
     * @return Map: {success: boolean, trade_status: String, ...}
     */
    public Map<String, Object> queryOrder(String orderNo) throws Exception {
        log.info("========================================");
        log.info("查询支付宝订单状态, orderNo: {}", orderNo);
        log.info("========================================");

        if (alipayConfig == null) {
            throw new BizException("Alipay 未启用");
        }

        // biz_content
        ObjectNode biz = objectMapper.createObjectNode();
        biz.put("out_trade_no", orderNo);

        Map<String, String> params = new HashMap<>();
        params.put("app_id", alipayConfig.getAppId());
        params.put("method", "alipay.trade.query");
        params.put("format", alipayConfig.getFormat());
        params.put("charset", alipayConfig.getCharset());
        params.put("sign_type", alipayConfig.getSignType());
        params.put("timestamp", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        params.put("version", "1.0");
        params.put("biz_content", biz.toString());

        String signContent = AlipaySignatureUtil.buildSignContent(params);
        String sign = AlipaySignatureUtil.signRsa2(signContent, merchantPrivateKey(), alipayConfig.getCharset());
        params.put("sign", sign);

        String formBody = AlipaySignatureUtil.buildFormBody(params, alipayConfig.getCharset());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(alipayConfig.getGatewayUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=" + alipayConfig.getCharset())
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("Alipay 网关请求失败: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode respNode = root.get("alipay_trade_query_response");
        if (respNode == null) {
            throw new BizException("Alipay 响应解析失败");
        }

        String code = respNode.path("code").asText();
        Map<String, Object> result = new HashMap<>();
        
        if ("10000".equals(code)) {
            String tradeStatus = respNode.path("trade_status").asText();
            result.put("success", true);
            result.put("trade_status", tradeStatus);
            result.put("trade_no", respNode.path("trade_no").asText());
            log.info("✅ 支付宝订单查询成功: orderNo={}, trade_status={}", orderNo, tradeStatus);
        } else {
            result.put("success", false);
            result.put("msg", respNode.path("msg").asText());
            log.warn("⚠️  支付宝订单查询失败: orderNo={}, msg={}", orderNo, respNode.path("msg").asText());
        }
        
        return result;
    }

    /**
     * 处理支付宝异步通知：验签 + 幂等更新订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(Map<String, String> params) {
        if (alipayConfig == null) {
            throw new BizException("Alipay 未启用");
        }

        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) {
            throw new BizException("缺少 sign");
        }

        // NOTE: Alipay notify params are usually urlencoded; verify must use decoded canonical string.
        String signContent = AlipaySignatureUtil.buildSignContentForNotify(params, alipayConfig.getCharset());
        boolean signOk = AlipaySignatureUtil.verifyRsa2(signContent, sign, alipayPublicKey(), alipayConfig.getCharset());
        if (!signOk) {
            String signType = params.get("sign_type");
            String keys = params.keySet().stream().sorted().collect(Collectors.joining(","));
            log.error("Alipay notify 验签失败: sign_type={}, keys={}", signType, keys);
            log.error("Alipay notify signContent(forVerify): {}", signContent.length() > 500 ? signContent.substring(0, 500) + "..." : signContent);
            throw new BizException("验签失败");
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String tradeNo = params.get("trade_no");

        log.info("Alipay notify: out_trade_no={}, trade_status={}, trade_no={}", outTradeNo, tradeStatus, tradeNo);

        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            updateOrderStatus(outTradeNo, tradeNo);
        }
    }

    private void updateOrderStatus(String orderNo, String tradeNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在: " + orderNo);
        }

        if (order.getStatus() != 0) {
            log.warn("订单状态不是待支付，跳过更新: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        String remark = order.getRemark() != null ? order.getRemark() : "";
        order.setRemark(remark + " [支付宝trade_no:" + tradeNo + "]");

        int result = orderMapper.update(order);
        if (result <= 0) {
            throw new BizException("订单状态更新失败");
        }

        log.info("�� Alipay 支付成功，订单已更新: orderNo={}", orderNo);
    }
}
