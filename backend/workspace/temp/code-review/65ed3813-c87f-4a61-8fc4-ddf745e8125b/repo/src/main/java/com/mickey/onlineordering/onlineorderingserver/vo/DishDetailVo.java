package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品详情VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishDetailVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private BigDecimal price;
    private Long categoryId;
    private String categoryName;
    private String description;
    private String imageUrl;
    private Integer stock;
    private Integer sales;
    private Integer status;
    private LocalDateTime createTime;
}











