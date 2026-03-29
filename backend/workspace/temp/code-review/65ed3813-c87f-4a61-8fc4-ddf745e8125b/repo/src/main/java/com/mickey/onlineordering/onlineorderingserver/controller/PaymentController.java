package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.dto.PaymentCreateDto;
import com.mickey.onlineordering.onlineorderingserver.service.PaymentService;
import com.mickey.onlineordering.onlineorderingserver.vo.PaymentInfoVo;
import com.mickey.onlineordering.onlineorderingserver.vo.PaymentStatusVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器
 * 处理支付相关请求
 */
@Tag(name = "支付接口", description = "支付相关接口")
@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    
    @Autowired
    private PaymentService paymentService;
    
    /**
     * 创建支付订单（生成二维码）
     */
    @Operation(summary = "创建支付订单")
    @PostMapping("/create")
    public Result<PaymentInfoVo> createPayment(@Valid @RequestBody PaymentCreateDto dto) {
        PaymentInfoVo paymentInfo = paymentService.createPayment(dto);
        return Result.success(paymentInfo);
    }
    
    /**
     * 查询支付状态
     */
    @Operation(summary = "查询支付状态")
    @GetMapping("/status/{orderId}")
    public Result<PaymentStatusVo> checkPaymentStatus(@PathVariable Long orderId) {
        PaymentStatusVo status = paymentService.checkPaymentStatus(orderId);
        return Result.success(status);
    }
    
    /**
     * 模拟支付成功（仅用于测试）
     */
    @Operation(summary = "模拟支付成功")
    @PostMapping("/simulate/{orderId}")
    public Result<Void> simulatePayment(@PathVariable Long orderId) {
        paymentService.simulatePayment(orderId);
        return Result.success("支付成功", null);
    }
}

