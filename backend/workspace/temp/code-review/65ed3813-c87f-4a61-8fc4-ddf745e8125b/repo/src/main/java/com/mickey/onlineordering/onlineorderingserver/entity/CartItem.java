package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车项实体类
 * 对应数据库表：tb_cart_item
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long userId;
    private Long dishId;
    private Integer quantity;
    private BigDecimal price;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}












