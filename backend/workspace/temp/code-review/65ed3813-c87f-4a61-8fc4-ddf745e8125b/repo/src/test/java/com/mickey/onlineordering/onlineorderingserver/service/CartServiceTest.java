package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.dto.CartAddItemDto;
import com.mickey.onlineordering.onlineorderingserver.entity.CartItem;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.CartItemMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.DishMapper;
import com.mickey.onlineordering.onlineorderingserver.service.impl.CartServiceImpl;
import com.mickey.onlineordering.onlineorderingserver.vo.CartItemVo;
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
 * 购物车服务测试类
 * 测试购物车的添加、删除、查询等功能
 */
@SpringBootTest
@DisplayName("购物车服务测试")
class CartServiceTest {
    
    @Mock
    private CartItemMapper cartItemMapper;
    
    @Mock
    private DishMapper dishMapper;
    
    @InjectMocks
    private CartServiceImpl cartService;
    
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_DISH_ID = 100L;
    
    private Dish mockDish;
    private CartItem mockCartItem;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 准备Mock菜品
        mockDish = new Dish();
        mockDish.setId(TEST_DISH_ID);
        mockDish.setName("宫保鸡丁");
        mockDish.setPrice(new BigDecimal("38.00"));
        mockDish.setStock(100);
        mockDish.setStatus(1);
        
        // 准备Mock购物车项
        mockCartItem = new CartItem();
        mockCartItem.setId(1L);
        mockCartItem.setUserId(TEST_USER_ID);
        mockCartItem.setDishId(TEST_DISH_ID);
        mockCartItem.setQuantity(2);
    }
    
    @Test
    @DisplayName("测试添加商品到购物车 - 新商品")
    void testAddItem_WithNewDish_Success() {
        // Given
        CartAddItemDto dto = new CartAddItemDto();
        dto.setDishId(TEST_DISH_ID);
        dto.setQuantity(2);
        
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        when(cartItemMapper.selectByUserIdAndDishId(TEST_USER_ID, TEST_DISH_ID)).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> cartService.addItem(TEST_USER_ID, dto));
        
        // Verify
        verify(dishMapper, times(1)).selectById(TEST_DISH_ID);
        verify(cartItemMapper, times(1)).selectByUserIdAndDishId(TEST_USER_ID, TEST_DISH_ID);
        verify(cartItemMapper, times(1)).insert(any(CartItem.class));
    }
    
    @Test
    @DisplayName("测试添加商品到购物车 - 已存在商品增加数量")
    void testAddItem_WithExistingDish_IncreaseQuantity() {
        // Given
        CartAddItemDto dto = new CartAddItemDto();
        dto.setDishId(TEST_DISH_ID);
        dto.setQuantity(3);
        
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        when(cartItemMapper.selectByUserIdAndDishId(TEST_USER_ID, TEST_DISH_ID)).thenReturn(mockCartItem);
        when(cartItemMapper.update(any(CartItem.class))).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> cartService.addItem(TEST_USER_ID, dto));
        
        // Verify: 应该更新数量而不是插入
        verify(cartItemMapper, times(1)).update(any(CartItem.class));
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }
    
    @Test
    @DisplayName("测试添加商品到购物车 - 菜品不存在")
    void testAddItem_WithNonexistentDish_ThrowException() {
        // Given
        CartAddItemDto dto = new CartAddItemDto();
        dto.setDishId(999L);
        dto.setQuantity(1);
        
        when(dishMapper.selectById(999L)).thenReturn(null);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            cartService.addItem(TEST_USER_ID, dto);
        });
        
        assertEquals(ErrorCode.DISH_NOT_FOUND.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试添加商品到购物车 - 菜品已下架")
    void testAddItem_WithDisabledDish_ThrowException() {
        // Given
        mockDish.setStatus(0);  // 已下架
        CartAddItemDto dto = new CartAddItemDto();
        dto.setDishId(TEST_DISH_ID);
        dto.setQuantity(1);
        
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            cartService.addItem(TEST_USER_ID, dto);
        });
        
        assertTrue(exception.getMessage().contains("下架") || exception.getMessage().contains("售"));
    }
    
    @Test
    @DisplayName("测试添加商品到购物车 - 库存不足")
    void testAddItem_WithInsufficientStock_ThrowException() {
        // Given
        mockDish.setStock(5);
        CartAddItemDto dto = new CartAddItemDto();
        dto.setDishId(TEST_DISH_ID);
        dto.setQuantity(10);  // 超过库存
        
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        when(cartItemMapper.selectByUserIdAndDishId(TEST_USER_ID, TEST_DISH_ID)).thenReturn(null);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            cartService.addItem(TEST_USER_ID, dto);
        });
        
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试查询购物车列表 - 有商品")
    void testGetCartItems_WithItems_ReturnList() {
        // Given
        List<CartItem> cartItems = new ArrayList<>();
        cartItems.add(mockCartItem);
        
        when(cartItemMapper.selectByUserId(TEST_USER_ID)).thenReturn(cartItems);
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        
        // When
        List<CartItemVo> result = cartService.getCartItems(TEST_USER_ID);
        
        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("宫保鸡丁", result.get(0).getDishName());
        
        // Verify
        verify(cartItemMapper, times(1)).selectByUserId(TEST_USER_ID);
    }
    
    @Test
    @DisplayName("测试查询购物车列表 - 空购物车")
    void testGetCartItems_WithEmptyCart_ReturnEmptyList() {
        // Given
        when(cartItemMapper.selectByUserId(TEST_USER_ID)).thenReturn(new ArrayList<>());
        
        // When
        List<CartItemVo> result = cartService.getCartItems(TEST_USER_ID);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("测试删除购物车商品 - 成功")
    void testRemoveItem_WithValidId_Success() {
        // Given
        Long cartItemId = 1L;
        when(cartItemMapper.selectById(cartItemId)).thenReturn(mockCartItem);
        when(cartItemMapper.deleteById(cartItemId)).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> cartService.deleteItem(TEST_USER_ID, cartItemId));
        
        // Verify
        verify(cartItemMapper, times(1)).deleteById(cartItemId);
    }
    
    @Test
    @DisplayName("测试更新购物车商品数量 - 成功")
    void testUpdateQuantity_WithValidQuantity_Success() {
        // Given
        Long cartItemId = 1L;
        int newQuantity = 5;
        
        when(cartItemMapper.selectById(cartItemId)).thenReturn(mockCartItem);
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        when(cartItemMapper.update(any(CartItem.class))).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> cartService.updateItemQuantity(TEST_USER_ID, cartItemId, newQuantity));
        
        // Verify
        verify(cartItemMapper, times(1)).update(any(CartItem.class));
    }
    
    @Test
    @DisplayName("测试更新购物车商品数量 - 数量超过库存")
    void testUpdateQuantity_ExceedStock_ThrowException() {
        // Given
        Long cartItemId = 1L;
        int newQuantity = 200;  // 超过库存100
        mockDish.setStock(100);
        
        when(cartItemMapper.selectById(cartItemId)).thenReturn(mockCartItem);
        when(dishMapper.selectById(TEST_DISH_ID)).thenReturn(mockDish);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            cartService.updateItemQuantity(TEST_USER_ID, cartItemId, newQuantity);
        });
        
        assertEquals(ErrorCode.STOCK_INSUFFICIENT.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试清空购物车 - 成功")
    void testClearCart_Success() {
        // Given
        when(cartItemMapper.deleteByUserId(TEST_USER_ID)).thenReturn(5);
        
        // When & Then
        assertDoesNotThrow(() -> cartService.clearCart(TEST_USER_ID));
        
        // Verify
        verify(cartItemMapper, times(1)).deleteByUserId(TEST_USER_ID);
    }
}

