package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细Mapper接口
 */
@Mapper
public interface OrderItemMapper {
    

    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);
    int batchInsert(@Param("items") List<OrderItem> items);
    int insert(OrderItem orderItem);
}












