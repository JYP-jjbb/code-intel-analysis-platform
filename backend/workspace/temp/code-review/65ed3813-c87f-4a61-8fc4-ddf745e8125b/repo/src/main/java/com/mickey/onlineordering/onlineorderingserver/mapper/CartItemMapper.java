package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车Mapper接口
 */
@Mapper
public interface CartItemMapper {

    CartItem selectById(@Param("id") Long id);
    List<CartItem> selectByUserId(@Param("userId") Long userId);
    CartItem selectByUserIdAndDishId(@Param("userId") Long userId, @Param("dishId") Long dishId);
    int insert(CartItem cartItem);
    int update(CartItem cartItem);
    int deleteById(@Param("id") Long id);
    int deleteByUserId(@Param("userId") Long userId);
}












