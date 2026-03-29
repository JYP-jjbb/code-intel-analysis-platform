package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付信息VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfoVo {

    private Long orderId;
    private String orderNo;
    private Double amount;
    private String paymentType;
    private String qrCode;
    private String expireTime;
}

