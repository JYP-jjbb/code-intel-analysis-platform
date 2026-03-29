package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单Mapper接口
 */
@Mapper
public interface OrderMapper {

    Order selectById(@Param("id") Long id);
    Order selectByOrderNo(@Param("orderNo") String orderNo);
    List<Order> selectByUserId(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("limit") Integer limit);
    Long countByUserId(@Param("userId") Long userId);
    List<Order> selectAll(@Param("offset") Integer offset, @Param("limit") Integer limit);
    Long countAll();
    int insert(Order order);
    int update(Order order);
    int updateById(Order order);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}




