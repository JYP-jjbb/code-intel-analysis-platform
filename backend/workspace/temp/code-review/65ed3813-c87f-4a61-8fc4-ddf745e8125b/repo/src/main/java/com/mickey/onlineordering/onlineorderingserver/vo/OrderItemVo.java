package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long dishId;
    private String dishName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private String imageUrl;
}











