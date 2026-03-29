package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体类
 * 对应数据库表：tb_order_item
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long orderId;
    private Long dishId;
    private String dishName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private String imageUrl;
    private LocalDateTime createTime;
}












