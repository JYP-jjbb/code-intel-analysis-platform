package com.mickey.onlineordering.onlineorderingserver.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装类
 * 用于封装分页查询的返回数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private List<T> records;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPages;
    private Boolean hasNext;
    private Boolean hasPrevious;

    public PageResult(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
        this.hasNext = pageNum < this.totalPages;
        this.hasPrevious = pageNum > 1;
    }

    public static <T> PageResult<T> empty(Integer pageNum, Integer pageSize) {
        return new PageResult<>(List.of(), 0L, pageNum, pageSize, 0, false, false);
    }
}












