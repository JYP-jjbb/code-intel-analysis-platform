package com.mickey.onlineordering.onlineorderingserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 菜品查询DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishQueryDto implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private String keyword;
    private Long categoryId;
    private Integer status;
    private Integer pageNum;
    private Integer pageSize;
    private String orderBy;
}












