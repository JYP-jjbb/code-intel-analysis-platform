package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.entity.Category;

import java.util.List;

/**
 * 分类服务接口
 */
public interface CategoryService {

    List<Category> getAllCategories();
    List<Category> getActiveCategories();
    Category getCategoryById(Long id);
    void addCategory(Category category);
    void updateCategory(Category category);
    void deleteCategory(Long id);
}











