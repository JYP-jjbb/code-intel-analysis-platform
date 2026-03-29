package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.dto.DishQueryDto;
import com.mickey.onlineordering.onlineorderingserver.dto.DishSaveDto;
import com.mickey.onlineordering.onlineorderingserver.service.DishService;
import com.mickey.onlineordering.onlineorderingserver.vo.DishDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.DishListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 菜品控制器
 * 处理菜品相关请求
 */
@Tag(name = "菜品接口", description = "菜品相关接口")
@RestController
@RequestMapping("/api/dishes")
public class DishController {
    
    @Autowired
    private DishService dishService;
    
    /**
     * 分页查询菜品列表
     */
    @Operation(summary = "分页查询菜品列表")
    @GetMapping
    public Result<PageResult<DishListVo>> getDishPage(DishQueryDto queryDto) {
        PageResult<DishListVo> pageResult = dishService.getDishPage(queryDto);
        return Result.success(pageResult);
    }
    
    /**
     * 根据ID获取菜品详情
     */
    @Operation(summary = "根据ID获取菜品详情")
    @GetMapping("/{id}")
    public Result<DishDetailVo> getDishById(@PathVariable Long id) {
        DishDetailVo dishDetail = dishService.getDishById(id);
        return Result.success(dishDetail);
    }
    
    /**
     * 新增菜品（管理员）
     */
    @Operation(summary = "新增菜品")
    @PostMapping
    public Result<Void> addDish(@Valid @RequestBody DishSaveDto dto) {
        dishService.addDish(dto);
        return Result.success("新增成功", null);
    }
    
    /**
     * 更新菜品（管理员）
     */
    @Operation(summary = "更新菜品")
    @PutMapping("/{id}")
    public Result<Void> updateDish(@PathVariable Long id, @Valid @RequestBody DishSaveDto dto) {
        dto.setId(id);
        dishService.updateDish(dto);
        return Result.success("更新成功", null);
    }
    
    /**
     * 删除菜品（管理员）
     */
    @Operation(summary = "删除菜品")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDish(@PathVariable Long id) {
        dishService.deleteDish(id);
        return Result.success("删除成功", null);
    }
}












