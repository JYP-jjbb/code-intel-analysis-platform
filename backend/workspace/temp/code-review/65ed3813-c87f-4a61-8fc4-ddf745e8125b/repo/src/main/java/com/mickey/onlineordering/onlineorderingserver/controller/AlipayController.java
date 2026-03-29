package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.service.AlipayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付控制器（沙箱）
 */
@Slf4j
@Tag(name = "支付宝支付接口", description = "Alipay 沙箱扫码支付相关接口")
@RestController
@RequestMapping("/api/pay/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Operation(summary = "预下单（生成扫码支付二维码链接）", description = "调用 alipay.trade.precreate，返回 qr_code")
    @PostMapping("/precreate/{orderId}")
    public Result<Map<String, String>> precreate(@PathVariable Long orderId) {
        try {
            String qrCode = alipayService.precreate(orderId);
            Map<String, String> data = new HashMap<>();
            data.put("qr_code", qrCode);
            data.put("order_id", orderId.toString());
            return Result.success("预下单成功", data);
        } catch (Exception e) {
            log.error("Alipay precreate 失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 接收支付宝异步通知
     * - 路径: /api/pay/alipay/notify
     * - 必须返回: success / failure
     */
    @Operation(summary = "接收支付宝支付结果通知", description = "支付宝服务器回调的通知接口")
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        try {
            Map<String, String> params = extractParams(request);
            alipayService.handleNotify(params);
            return "success";
        } catch (Exception e) {
            log.error("Alipay notify 处理失败", e);
            return "failure";
        }
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getParameter(name);
            params.put(name, value);
        }
        return params;
    }
}
