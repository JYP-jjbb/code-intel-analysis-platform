package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.dto.DishSaveDto;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.DishMapper;
import com.mickey.onlineordering.onlineorderingserver.service.impl.DishServiceImpl;
import com.mickey.onlineordering.onlineorderingserver.vo.DishDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.DishListVo;
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
 * 菜品服务测试类
 * 测试菜品的CRUD操作
 */
@SpringBootTest
@DisplayName("菜品服务测试")
class DishServiceTest {
    
    @Mock
    private DishMapper dishMapper;
    
    @InjectMocks
    private DishServiceImpl dishService;
    
    private Dish mockDish;
    private DishSaveDto dishSaveDto;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 准备Mock菜品
        mockDish = new Dish();
        mockDish.setId(1L);
        mockDish.setName("宫保鸡丁");
        mockDish.setPrice(new BigDecimal("38.00"));
        mockDish.setDescription("经典川菜");
        mockDish.setCategoryId(10L);
        mockDish.setImageUrl("/images/dish1.jpg");
        mockDish.setStock(100);
        mockDish.setStatus(1);
        
        // 准备DTO
        dishSaveDto = new DishSaveDto();
        dishSaveDto.setName("鱼香肉丝");
        dishSaveDto.setPrice(new BigDecimal("32.00"));
        dishSaveDto.setDescription("经典家常菜");
        dishSaveDto.setCategoryId(10L);
        dishSaveDto.setImageUrl("/images/dish2.jpg");
        dishSaveDto.setStock(50);
    }
    
    @Test
    @DisplayName("测试创建菜品 - 成功")
    void testCreateDish_WithValidData_Success() {
        // Given
        when(dishMapper.insert(any(Dish.class))).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> dishService.addDish(dishSaveDto));
        
        // Verify
        verify(dishMapper, times(1)).insert(any(Dish.class));
    }
    
    @Test
    @DisplayName("测试创建菜品 - 价格为负数")
    void testCreateDish_WithNegativePrice_ThrowException() {
        // Given
        dishSaveDto.setPrice(new BigDecimal("-10.00"));
        
        // When & Then: 应该在Service层或Controller层验证
        // 这里假设Service层有验证
        assertThrows(BizException.class, () -> {
            dishService.addDish(dishSaveDto);
        });
    }
    
    @Test
    @DisplayName("测试根据ID查询菜品 - 存在")
    void testGetDishById_WithExistingId_ReturnDish() {
        // Given
        when(dishMapper.selectById(1L)).thenReturn(mockDish);
        
        // When
        DishDetailVo result = dishService.getDishById(1L);
        
        // Then
        assertNotNull(result);
        assertEquals("宫保鸡丁", result.getName());
        assertEquals(new BigDecimal("38.00"), result.getPrice());
        
        // Verify
        verify(dishMapper, times(1)).selectById(1L);
    }
    
    @Test
    @DisplayName("测试根据ID查询菜品 - 不存在")
    void testGetDishById_WithNonexistentId_ThrowException() {
        // Given
        when(dishMapper.selectById(999L)).thenReturn(null);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            dishService.getDishById(999L);
        });
        
        assertEquals(ErrorCode.DISH_NOT_FOUND.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试更新菜品 - 成功")
    void testUpdateDish_WithValidData_Success() {
        // Given
        dishSaveDto.setId(1L);
        dishSaveDto.setName("宫保鸡丁（改良版）");
        
        when(dishMapper.selectById(1L)).thenReturn(mockDish);
        when(dishMapper.update(any(Dish.class))).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> dishService.updateDish(dishSaveDto));
        
        // Verify
        verify(dishMapper, times(1)).update(any(Dish.class));
    }
    
    @Test
    @DisplayName("测试更新菜品 - 菜品不存在")
    void testUpdateDish_WithNonexistentId_ThrowException() {
        // Given
        dishSaveDto.setId(999L);
        when(dishMapper.selectById(999L)).thenReturn(null);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            dishService.updateDish(dishSaveDto);
        });
        
        assertEquals(ErrorCode.DISH_NOT_FOUND.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试删除菜品 - 成功")
    void testDeleteDish_WithValidId_Success() {
        // Given
        Long dishId = 1L;
        when(dishMapper.deleteById(dishId)).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> dishService.deleteDish(dishId));
        
        // Verify
        verify(dishMapper, times(1)).deleteById(dishId);
    }
}

