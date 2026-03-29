package com.mickey.onlineordering.onlineorderingserver.service.impl;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.entity.Category;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.CategoryMapper;
import com.mickey.onlineordering.onlineorderingserver.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类服务实现类
 */
@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {
    
    @Autowired
    private CategoryMapper categoryMapper;
    
    @Override
    public List<Category> getAllCategories() {
        return categoryMapper.selectAll();
    }
    
    @Override
    public List<Category> getActiveCategories() {
        return categoryMapper.selectByStatus(1);
    }
    
    @Override
    public Category getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCategory(Category category) {
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        
        int result = categoryMapper.insert(category);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "新增分类失败");
        }
        
        log.info("新增分类成功：categoryId={}", category.getId());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Category category) {
        Category existingCategory = categoryMapper.selectById(category.getId());
        if (existingCategory == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        
        category.setUpdateTime(LocalDateTime.now());
        
        int result = categoryMapper.update(category);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "更新分类失败");
        }
        
        log.info("更新分类成功：categoryId={}", category.getId());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        int result = categoryMapper.deleteById(id);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "删除分类失败");
        }
        
        log.info("删除分类成功：categoryId={}", id);
    }
}












