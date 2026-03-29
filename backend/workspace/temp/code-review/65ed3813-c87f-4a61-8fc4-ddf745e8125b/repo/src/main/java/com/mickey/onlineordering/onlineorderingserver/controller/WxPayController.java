package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.service.WxPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 微信支付控制器
 * 处理微信支付相关请求
 */
@Slf4j
@Tag(name = "微信支付接口", description = "微信支付 APIv3 相关接口")
@RestController
@RequestMapping("/api/pay/wechat")
public class WxPayController {
    
    @Autowired
    private WxPayService wxPayService;
    
    /**
     * NATIVE 统一下单
     * 生成扫码支付二维码
     */
    @Operation(summary = "NATIVE 统一下单", description = "生成微信扫码支付二维码")
    @PostMapping("/native/{orderId}")
    public Result<Map<String, String>> nativePay(@PathVariable Long orderId) {
        log.info("========================================");
        log.info("收到 NATIVE 统一下单请求，订单ID: {}", orderId);
        log.info("========================================");
        
        try {
            String codeUrl = wxPayService.nativePay(orderId);
            
            Map<String, String> result = new HashMap<>();
            result.put("code_url", codeUrl);
            result.put("order_id", orderId.toString());
            
            return Result.success("下单成功", result);
        } catch (Exception e) {
            log.error("NATIVE 统一下单失败", e);
            return Result.error(500, e.getMessage());
        }
    }
    
    /**
     * 查询订单支付状态
     * 主动查询，兜底一致性
     */
    @Operation(summary = "查询订单支付状态", description = "主动查询订单支付状态（兜底一致性）")
    @GetMapping("/query/{orderNo}")
    public Result<Map<String, Object>> queryOrder(@PathVariable String orderNo) {
        log.info("========================================");
        log.info("收到查询订单请求，订单号: {}", orderNo);
        log.info("========================================");
        
        try {
            Map<String, Object> result = wxPayService.queryOrder(orderNo);
            
            if ((Boolean) result.get("success")) {
                return Result.success("查询成功", result);
            } else {
                return Result.error(500, result.get("message").toString());
            }
        } catch (Exception e) {
            log.error("查询订单失败", e);
            return Result.error(500, e.getMessage());
        }
    }
    
    /**
     * 接收微信支付结果通知
     * 这是微信服务器主动回调的接口
     * 路径: /api/pay/wechat/notify
     * 完整地址: wxpay.notify-domain + /api/pay/wechat/notify
     */
    @Operation(summary = "接收微信支付结果通知", description = "微信服务器主动回调的接口")
    @PostMapping("/notify")
    public Map<String, Object> paymentNotify(HttpServletRequest request) {
        log.info("========================================");
        log.info("收到微信支付结果通知");
        log.info("========================================");
        
        try {
            // 读取请求头
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                headers.put(headerName.toLowerCase(), headerValue);
                log.info("Header: {} = {}", headerName, headerValue);
            }
            
            // 读取请求体
            String body;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
                body = reader.lines().collect(Collectors.joining("\n"));
            }
            
            log.info("请求体:\n{}", body);
            
            // 处理支付回调
            Map<String, Object> result = wxPayService.handlePaymentNotify(headers, body);
            
            log.info("处理结果: {}", result);
            
            return result;
            
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("code", "FAIL");
            errorResult.put("message", e.getMessage());
            
            return errorResult;
        }
    }
    
    /**
     * 测试接口：检查微信支付配置
     * 仅用于开发调试
     */
    @Operation(summary = "检查微信支付配置", description = "仅用于开发调试")
    @GetMapping("/config/check")
    public Result<Map<String, String>> checkConfig() {
        log.info("========================================");
        log.info("检查微信支付配置");
        log.info("========================================");
        
        try {
            Map<String, String> config = new HashMap<>();
            config.put("status", "OK");
            config.put("message", "微信支付配置正常");
            
            return Result.success("配置正常", config);
        } catch (Exception e) {
            log.error("配置检查失败", e);
            return Result.error(500, "配置异常: " + e.getMessage());
        }
    }
}

