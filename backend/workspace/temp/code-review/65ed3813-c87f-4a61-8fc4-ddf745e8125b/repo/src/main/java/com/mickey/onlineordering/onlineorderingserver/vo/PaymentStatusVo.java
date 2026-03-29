package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付状态VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusVo {

    private Long orderId;
    private Boolean isPaid;
    private String payTime;
}

