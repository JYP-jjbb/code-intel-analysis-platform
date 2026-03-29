package com.mickey.onlineordering.onlineorderingserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 菜品保存DTO（新增/修改）
 *
 * @author Mickey
 * @date 2025-11-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishSaveDto implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "菜品名称不能为空")
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    private String description;
    private String imageUrl;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    private Integer status;
}












