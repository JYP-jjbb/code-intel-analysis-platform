package com.mickey.onlineordering.onlineorderingserver.service.impl;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.dto.CartAddItemDto;
import com.mickey.onlineordering.onlineorderingserver.entity.CartItem;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.CartItemMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.DishMapper;
import com.mickey.onlineordering.onlineorderingserver.service.CartService;
import com.mickey.onlineordering.onlineorderingserver.vo.CartItemVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车服务实现类
 */
@Slf4j
@Service
public class CartServiceImpl implements CartService {
    
    @Autowired
    private CartItemMapper cartItemMapper;
    
    @Autowired
    private DishMapper dishMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, CartAddItemDto dto) {
        // 查询菜品
        Dish dish = dishMapper.selectById(dto.getDishId());
        if (dish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }
        
        // 检查库存
        if (dish.getStock() < dto.getQuantity()) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }
        
        // 检查该菜品是否已在购物车中
        CartItem existingItem = cartItemMapper.selectByUserIdAndDishId(userId, dto.getDishId());
        
        if (existingItem != null) {
            // 更新数量
            existingItem.setQuantity(existingItem.getQuantity() + dto.getQuantity());
            existingItem.setUpdateTime(LocalDateTime.now());
            cartItemMapper.update(existingItem);
        } else {
            // 新增购物车项
            CartItem cartItem = new CartItem();
            cartItem.setUserId(userId);
            cartItem.setDishId(dto.getDishId());
            cartItem.setQuantity(dto.getQuantity());
            cartItem.setPrice(dish.getPrice());
            cartItem.setCreateTime(LocalDateTime.now());
            cartItem.setUpdateTime(LocalDateTime.now());
            cartItemMapper.insert(cartItem);
        }
        
        log.info("添加购物车成功：userId={}, dishId={}", userId, dto.getDishId());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItemQuantity(Long userId, Long cartItemId, Integer quantity) {
        CartItem cartItem = cartItemMapper.selectById(cartItemId);
        if (cartItem == null || !cartItem.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "购物车项不存在");
        }
        
        // 检查库存
        Dish dish = dishMapper.selectById(cartItem.getDishId());
        if (dish != null && dish.getStock() < quantity) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }
        
        cartItem.setQuantity(quantity);
        cartItem.setUpdateTime(LocalDateTime.now());
        
        int result = cartItemMapper.update(cartItem);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        
        log.info("更新购物车项成功：cartItemId={}", cartItemId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long userId, Long cartItemId) {
        CartItem cartItem = cartItemMapper.selectById(cartItemId);
        if (cartItem == null || !cartItem.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "购物车项不存在");
        }
        
        int result = cartItemMapper.deleteById(cartItemId);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        
        log.info("删除购物车项成功：cartItemId={}", cartItemId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        cartItemMapper.deleteByUserId(userId);
        log.info("清空购物车成功：userId={}", userId);
    }
    
    @Override
    public List<CartItemVo> getCartItems(Long userId) {
        List<CartItem> cartItems = cartItemMapper.selectByUserId(userId);
        
        return cartItems.stream().map(item -> {
            CartItemVo vo = new CartItemVo();
            vo.setId(item.getId());
            vo.setDishId(item.getDishId());
            vo.setPrice(item.getPrice());
            vo.setQuantity(item.getQuantity());
            vo.setSubtotal(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
            
            // 查询菜品信息
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish != null) {
                vo.setDishName(dish.getName());
                vo.setImageUrl(dish.getImageUrl());
                vo.setStock(dish.getStock());
            }
            
            return vo;
        }).toList();
    }
}












