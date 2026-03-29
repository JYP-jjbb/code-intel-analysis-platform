package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.dto.CartAddItemDto;
import com.mickey.onlineordering.onlineorderingserver.service.CartService;
import com.mickey.onlineordering.onlineorderingserver.vo.CartItemVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器
 * 处理购物车相关请求
 */
@Tag(name = "购物车接口", description = "购物车相关接口")
@RestController
@RequestMapping("/api/cart")
public class CartController {
    
    @Autowired
    private CartService cartService;
    
    /**
     * 获取购物车列表
     */
    @Operation(summary = "获取购物车列表")
    @GetMapping
    public Result<List<CartItemVo>> getCartItems(@RequestAttribute("userId") Long userId) {
        List<CartItemVo> cartItems = cartService.getCartItems(userId);
        return Result.success(cartItems);
    }
    
    /**
     * 添加商品到购物车
     */
    @Operation(summary = "添加商品到购物车")
    @PostMapping
    public Result<Void> addItem(@RequestAttribute("userId") Long userId,
                                @Valid @RequestBody CartAddItemDto dto) {
        cartService.addItem(userId, dto);
        return Result.success("添加成功", null);
    }
    
    /**
     * 更新购物车项数量
     */
    @Operation(summary = "更新购物车项数量")
    @PutMapping("/{cartItemId}")
    public Result<Void> updateItemQuantity(@RequestAttribute("userId") Long userId,
                                           @PathVariable Long cartItemId,
                                           @RequestParam Integer quantity) {
        cartService.updateItemQuantity(userId, cartItemId, quantity);
        return Result.success("更新成功", null);
    }
    
    /**
     * 删除购物车项
     */
    @Operation(summary = "删除购物车项")
    @DeleteMapping("/{cartItemId}")
    public Result<Void> deleteItem(@RequestAttribute("userId") Long userId,
                                    @PathVariable Long cartItemId) {
        cartService.deleteItem(userId, cartItemId);
        return Result.success("删除成功", null);
    }
    
    /**
     * 清空购物车
     */
    @Operation(summary = "清空购物车")
    @DeleteMapping
    public Result<Void> clearCart(@RequestAttribute("userId") Long userId) {
        cartService.clearCart(userId);
        return Result.success("清空成功", null);
    }
}












