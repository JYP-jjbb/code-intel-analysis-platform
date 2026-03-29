package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品实体类
 * 对应数据库表：tb_dish
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dish implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private BigDecimal price;
    private Long categoryId;
    private String description;
    private String imageUrl;
    private Integer stock;
    private Integer sales;
    private Integer status; //状态（0-下架，1-上架）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}












