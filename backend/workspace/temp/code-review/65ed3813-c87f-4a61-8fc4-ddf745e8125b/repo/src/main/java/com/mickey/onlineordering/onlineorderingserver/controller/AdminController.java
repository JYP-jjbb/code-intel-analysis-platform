package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.service.OrderService;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员控制器
 * 处理管理员相关请求
 */
@Tag(name = "管理员接口", description = "管理员相关接口")
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 分页查询所有订单列表
     */
    @Operation(summary = "分页查询所有订单列表")
    @GetMapping("/orders")
    public Result<PageResult<OrderSummaryVo>> getAllOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<OrderSummaryVo> pageResult = orderService.getAllOrders(pageNum, pageSize);
        return Result.success(pageResult);
    }
    
    /**
     * 完成订单
     */
    @Operation(summary = "完成订单")
    @PutMapping("/orders/{orderId}/complete")
    public Result<Void> completeOrder(@PathVariable Long orderId) {
        orderService.completeOrder(orderId);
        return Result.success("订单已完成", null);
    }
}












