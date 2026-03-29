package com.mickey.onlineordering.onlineorderingserver.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建支付订单DTO
 */
@Data
public class PaymentCreateDto {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "支付类型不能为空")
    private String paymentType;
}

