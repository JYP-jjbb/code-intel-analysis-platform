package com.mickey.onlineordering.onlineorderingserver.service.impl;

import com.mickey.onlineordering.onlineorderingserver.common.Constants;
import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.dto.DishQueryDto;
import com.mickey.onlineordering.onlineorderingserver.dto.DishSaveDto;
import com.mickey.onlineordering.onlineorderingserver.entity.Category;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.CategoryMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.DishMapper;
import com.mickey.onlineordering.onlineorderingserver.service.DishService;
import com.mickey.onlineordering.onlineorderingserver.util.BeanCopyUtil;
import com.mickey.onlineordering.onlineorderingserver.vo.DishDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.DishListVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品服务实现类
 */
@Slf4j
@Service
public class DishServiceImpl implements DishService {
    
    @Autowired
    private DishMapper dishMapper;
    
    @Autowired
    private CategoryMapper categoryMapper;
    
    @Override
    public PageResult<DishListVo> getDishPage(DishQueryDto queryDto) {
        // 设置默认分页参数
        if (queryDto.getPageNum() == null || queryDto.getPageNum() < 1) {
            queryDto.setPageNum(Constants.DEFAULT_PAGE_NUM);
        }
        if (queryDto.getPageSize() == null || queryDto.getPageSize() < 1) {
            queryDto.setPageSize(Constants.DEFAULT_PAGE_SIZE);
        }
        if (queryDto.getPageSize() > Constants.MAX_PAGE_SIZE) {
            queryDto.setPageSize(Constants.MAX_PAGE_SIZE);
        }
        
        // 计算偏移量
        int offset = (queryDto.getPageNum() - 1) * queryDto.getPageSize();
        queryDto.setPageNum(offset);
        
        // 查询数据
        List<Dish> dishList = dishMapper.selectByCondition(queryDto);
        Long total = dishMapper.countByCondition(queryDto);
        
        // 转换为VO
        List<DishListVo> voList = dishList.stream().map(dish -> {
            DishListVo vo = BeanCopyUtil.copyBean(dish, DishListVo.class);
            // 查询分类名称
            Category category = categoryMapper.selectById(dish.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
            return vo;
        }).toList();
        
        return new PageResult<>(voList, total, queryDto.getPageNum() / queryDto.getPageSize() + 1, queryDto.getPageSize());
    }
    
    @Override
    public DishDetailVo getDishById(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }
        
        DishDetailVo vo = BeanCopyUtil.copyBean(dish, DishDetailVo.class);
        
        // 查询分类名称
        Category category = categoryMapper.selectById(dish.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }
        
        return vo;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDish(DishSaveDto dto) {
        Dish dish = BeanCopyUtil.copyBean(dto, Dish.class);
        dish.setSales(0);
        dish.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        dish.setCreateTime(LocalDateTime.now());
        dish.setUpdateTime(LocalDateTime.now());
        
        int result = dishMapper.insert(dish);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "新增菜品失败");
        }
        
        log.info("新增菜品成功：dishId={}", dish.getId());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDish(DishSaveDto dto) {
        Dish existingDish = dishMapper.selectById(dto.getId());
        if (existingDish == null) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }
        
        Dish dish = BeanCopyUtil.copyBean(dto, Dish.class);
        dish.setUpdateTime(LocalDateTime.now());
        
        int result = dishMapper.update(dish);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "更新菜品失败");
        }
        
        log.info("更新菜品成功：dishId={}", dish.getId());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDish(Long id) {
        int result = dishMapper.deleteById(id);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "删除菜品失败");
        }
        
        log.info("删除菜品成功：dishId={}", id);
    }
}












