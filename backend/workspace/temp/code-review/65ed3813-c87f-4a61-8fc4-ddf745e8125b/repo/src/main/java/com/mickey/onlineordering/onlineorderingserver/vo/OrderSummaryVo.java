package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单摘要VO（列表展示）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private List<OrderItemVo> items;
    private Integer itemCount;
}











