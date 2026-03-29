package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.entity.Category;
import com.mickey.onlineordering.onlineorderingserver.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类控制器
 * 处理菜品分类相关请求
 */
@Tag(name = "分类接口", description = "菜品分类相关接口")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    
    @Autowired
    private CategoryService categoryService;
    
    /**
     * 获取所有分类
     */
    @Operation(summary = "获取所有分类")
    @GetMapping
    public Result<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return Result.success(categories);
    }
    
    /**
     * 获取启用的分类
     */
    @Operation(summary = "获取启用的分类")
    @GetMapping("/active")
    public Result<List<Category>> getActiveCategories() {
        List<Category> categories = categoryService.getActiveCategories();
        return Result.success(categories);
    }
    
    /**
     * 根据ID获取分类
     */
    @Operation(summary = "根据ID获取分类")
    @GetMapping("/{id}")
    public Result<Category> getCategoryById(@PathVariable Long id) {
        Category category = categoryService.getCategoryById(id);
        return Result.success(category);
    }
    
    /**
     * 新增分类（管理员）
     */
    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Void> addCategory(@RequestBody Category category) {
        categoryService.addCategory(category);
        return Result.success("新增成功", null);
    }
    
    /**
     * 更新分类（管理员）
     */
    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        categoryService.updateCategory(category);
        return Result.success("更新成功", null);
    }
    
    /**
     * 删除分类（管理员）
     */
    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success("删除成功", null);
    }
}












