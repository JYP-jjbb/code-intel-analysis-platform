package com.mickey.onlineordering.onlineorderingserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 购物车添加项DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartAddItemDto implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @NotNull(message = "菜品ID不能为空")
    private Long dishId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}












