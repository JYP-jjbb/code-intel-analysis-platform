package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.dto.OrderSubmitDto;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderSummaryVo;

/**
 * 订单服务接口
 */
public interface OrderService {

    OrderDetailVo submitOrder(Long userId, OrderSubmitDto dto);
    void cancelOrder(Long userId, Long orderId);
    void payOrder(Long userId, Long orderId);
    void completeOrder(Long orderId);
    OrderDetailVo getOrderById(Long userId, Long orderId);
    PageResult<OrderSummaryVo> getUserOrders(Long userId, Integer pageNum, Integer pageSize);
    PageResult<OrderSummaryVo> getAllOrders(Integer pageNum, Integer pageSize);
    void updateStatus(Long orderId, Integer status);
}











