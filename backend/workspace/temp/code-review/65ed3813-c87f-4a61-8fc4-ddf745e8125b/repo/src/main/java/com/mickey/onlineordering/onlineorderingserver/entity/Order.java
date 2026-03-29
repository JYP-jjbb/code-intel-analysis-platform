package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 * 对应数据库表：tb_order
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    /**
     * 订单状态
     * 0-待支付
     * 1-待接单（已支付）
     * 2-制作中
     * 3-已派送
     * 4-已送达
     * 5-已取消
     */
    private Integer status;
    private String address;
    private String receiverName;
    private String receiverPhone;
    private String remark;
    private String paymentMethod;
    private LocalDateTime payTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}








