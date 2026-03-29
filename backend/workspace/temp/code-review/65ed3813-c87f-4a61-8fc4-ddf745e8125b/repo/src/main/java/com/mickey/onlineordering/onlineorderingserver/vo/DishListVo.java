package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 菜品列表VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishListVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private BigDecimal price;
    private Long categoryId;
    private String categoryName;
    private String imageUrl;
    private Integer stock;
    private Integer sales;
    private Integer status;
}











