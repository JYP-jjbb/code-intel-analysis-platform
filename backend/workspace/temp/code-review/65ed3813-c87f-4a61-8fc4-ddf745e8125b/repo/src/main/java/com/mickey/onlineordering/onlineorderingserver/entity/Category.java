package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜品分类实体类
 * 对应数据库表：tb_category
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private Integer sort;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}












