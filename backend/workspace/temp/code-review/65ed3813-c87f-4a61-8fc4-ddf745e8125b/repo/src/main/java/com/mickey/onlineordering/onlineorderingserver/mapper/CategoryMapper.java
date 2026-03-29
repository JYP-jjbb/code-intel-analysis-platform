package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类Mapper接口
 */
@Mapper
public interface CategoryMapper {

    Category selectById(@Param("id") Long id);
    List<Category> selectAll();
    List<Category> selectByStatus(@Param("status") Integer status);
    int insert(Category category);
    int update(Category category);
    int deleteById(@Param("id") Long id);
}












