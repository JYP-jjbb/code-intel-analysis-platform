package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.dto.CartAddItemDto;
import com.mickey.onlineordering.onlineorderingserver.vo.CartItemVo;

import java.util.List;

/**
 * 购物车服务接口
 */
public interface CartService {

    void addItem(Long userId, CartAddItemDto dto);
    void updateItemQuantity(Long userId, Long cartItemId, Integer quantity);
    void deleteItem(Long userId, Long cartItemId);
    void clearCart(Long userId);
    List<CartItemVo> getCartItems(Long userId);
}











