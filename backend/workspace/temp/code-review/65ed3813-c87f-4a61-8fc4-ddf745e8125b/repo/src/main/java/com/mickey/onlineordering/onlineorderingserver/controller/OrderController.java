package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.dto.OrderSubmitDto;
import com.mickey.onlineordering.onlineorderingserver.service.OrderService;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 * 处理订单相关请求
 */
@Tag(name = "订单接口", description = "订单相关接口")
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 提交订单
     */
    @Operation(summary = "提交订单")
    @PostMapping
    public Result<OrderDetailVo> submitOrder(@RequestAttribute("userId") Long userId,
                                              @Valid @RequestBody OrderSubmitDto dto) {
        OrderDetailVo orderDetail = orderService.submitOrder(userId, dto);
        return Result.success(orderDetail);
    }
    
    /**
     * 取消订单
     */
    @Operation(summary = "取消订单")
    @PutMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(@RequestAttribute("userId") Long userId,
                                     @PathVariable Long orderId) {
        orderService.cancelOrder(userId, orderId);
        return Result.success("取消成功", null);
    }
    
    /**
     * 支付订单
     */
    @Operation(summary = "支付订单")
    @PutMapping("/{orderId}/pay")
    public Result<Void> payOrder(@RequestAttribute("userId") Long userId,
                                  @PathVariable Long orderId) {
        orderService.payOrder(userId, orderId);
        return Result.success("支付成功", null);
    }
    
    /**
     * 根据ID获取订单详情
     */
    @Operation(summary = "根据ID获取订单详情")
    @GetMapping("/{orderId}")
    public Result<OrderDetailVo> getOrderById(@RequestAttribute("userId") Long userId,
                                               @PathVariable Long orderId) {
        OrderDetailVo orderDetail = orderService.getOrderById(userId, orderId);
        return Result.success(orderDetail);
    }
    
    /**
     * 分页查询用户订单列表
     */
    @Operation(summary = "分页查询用户订单列表")
    @GetMapping
    public Result<PageResult<OrderSummaryVo>> getUserOrders(@RequestAttribute("userId") Long userId,
                                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<OrderSummaryVo> pageResult = orderService.getUserOrders(userId, pageNum, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 分页查询所有订单列表（管理员）
     */
    @Operation(summary = "分页查询所有订单列表")
    @GetMapping("/admin")
    public Result<PageResult<OrderSummaryVo>> getAllOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<OrderSummaryVo> pageResult = orderService.getAllOrders(pageNum, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 更新订单状态（管理员）
     */
    @Operation(summary = "更新订单状态")
    @PutMapping("/admin/{orderId}/status/{status}")
    public Result<Void> updateStatus(@PathVariable Long orderId, @PathVariable Integer status) {
        orderService.updateStatus(orderId, status);
        return Result.success("状态更新成功", null);
    }
}








