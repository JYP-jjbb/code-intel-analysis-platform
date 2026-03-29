package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.dto.OrderSubmitDto;
import com.mickey.onlineordering.onlineorderingserver.entity.CartItem;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import com.mickey.onlineordering.onlineorderingserver.entity.Order;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.CartItemMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.DishMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderItemMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderMapper;
import com.mickey.onlineordering.onlineorderingserver.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单服务测试类
 * 测试订单的创建、支付、状态流转等核心业务逻辑
 */
@SpringBootTest
@DisplayName("订单服务测试")
class OrderServiceTest {
    
    @Mock
    private OrderMapper orderMapper;
    
    @Mock
    private OrderItemMapper orderItemMapper;
    
    @Mock
    private CartItemMapper cartItemMapper;
    
    @Mock
    private DishMapper dishMapper;
    
    @InjectMocks
    private OrderServiceImpl orderService;
    
    private static final Long TEST_USER_ID = 1L;
    
    private OrderSubmitDto orderSubmitDto;
    private List<CartItem> mockCartItems;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 准备订单提交DTO
        orderSubmitDto = new OrderSubmitDto();
        orderSubmitDto.setReceiverName("张三");
        orderSubmitDto.setReceiverPhone("13800138000");
        orderSubmitDto.setAddress("北京市朝阳区xx路xx号");
        orderSubmitDto.setRemark("少放辣");
        
        // 准备Mock购物车项
        mockCartItems = new ArrayList<>();
        CartItem item1 = new CartItem();
        item1.setDishId(1L);
        item1.setQuantity(2);
        mockCartItems.add(item1);
        
        CartItem item2 = new CartItem();
        item2.setDishId(2L);
        item2.setQuantity(1);
        mockCartItems.add(item2);
    }
    
    @Test
    @DisplayName("测试提交订单 - 购物车为空")
    void testSubmitOrder_WithEmptyCart_ThrowException() {
        // Given
        when(cartItemMapper.selectByUserId(TEST_USER_ID)).thenReturn(new ArrayList<>());
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.submitOrder(TEST_USER_ID, orderSubmitDto);
        });
        
        assertEquals(ErrorCode.CART_EMPTY.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试提交订单 - 菜品库存不足")
    void testSubmitOrder_WithInsufficientStock_ThrowException() {
        // Given
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setStock(1);  // 库存不足
        dish.setStatus(1);
        
        when(cartItemMapper.selectByUserId(TEST_USER_ID)).thenReturn(mockCartItems);
        when(dishMapper.selectById(1L)).thenReturn(dish);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.submitOrder(TEST_USER_ID, orderSubmitDto);
        });
        
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试提交订单 - 菜品已下架")
    void testSubmitOrder_WithDisabledDish_ThrowException() {
        // Given
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setStatus(0);  // 已下架
        
        when(cartItemMapper.selectByUserId(TEST_USER_ID)).thenReturn(mockCartItems);
        when(dishMapper.selectById(1L)).thenReturn(dish);
        
        // When & Then
        assertThrows(BizException.class, () -> {
            orderService.submitOrder(TEST_USER_ID, orderSubmitDto);
        });
    }
}
