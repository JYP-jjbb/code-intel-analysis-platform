package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private String address;
    private String receiverName;
    private String receiverPhone;
    private String remark;
    private String paymentMethod;
    private LocalDateTime payTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime createTime;
    private List<OrderItemVo> items;
}











