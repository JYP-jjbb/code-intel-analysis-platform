package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.dto.DishQueryDto;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜品Mapper接口
 */
@Mapper
public interface DishMapper {

    Dish selectById(@Param("id") Long id);
    List<Dish> selectByCondition(DishQueryDto queryDto);
    Long countByCondition(DishQueryDto queryDto);
    int insert(Dish dish);
    int update(Dish dish);
    int deleteById(@Param("id") Long id);
    int updateSales(@Param("id") Long id, @Param("quantity") Integer quantity);
    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}












