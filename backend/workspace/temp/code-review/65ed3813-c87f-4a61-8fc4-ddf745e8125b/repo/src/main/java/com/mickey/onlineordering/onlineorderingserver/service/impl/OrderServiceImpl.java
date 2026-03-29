package com.mickey.onlineordering.onlineorderingserver.service.impl;

import com.mickey.onlineordering.onlineorderingserver.common.Constants;
import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.dto.OrderSubmitDto;
import com.mickey.onlineordering.onlineorderingserver.entity.CartItem;
import com.mickey.onlineordering.onlineorderingserver.entity.Dish;
import com.mickey.onlineordering.onlineorderingserver.entity.Order;
import com.mickey.onlineordering.onlineorderingserver.entity.OrderItem;
import com.mickey.onlineordering.onlineorderingserver.entity.User;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.CartItemMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.DishMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderItemMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.UserMapper;
import com.mickey.onlineordering.onlineorderingserver.service.OrderService;
import com.mickey.onlineordering.onlineorderingserver.util.BeanCopyUtil;
import com.mickey.onlineordering.onlineorderingserver.util.IdGeneratorUtil;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderItemVo;
import com.mickey.onlineordering.onlineorderingserver.vo.OrderSummaryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderItemMapper orderItemMapper;
    
    @Autowired
    private CartItemMapper cartItemMapper;
    
    @Autowired
    private DishMapper dishMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    private static final Map<Integer, String> ORDER_STATUS_MAP = new HashMap<>();
    
    static {
        ORDER_STATUS_MAP.put(Constants.ORDER_STATUS_PENDING, "待支付");
        ORDER_STATUS_MAP.put(Constants.ORDER_STATUS_PAID, "待接单");
        ORDER_STATUS_MAP.put(Constants.ORDER_STATUS_PREPARING, "制作中");
        ORDER_STATUS_MAP.put(Constants.ORDER_STATUS_DELIVERING, "已派送");
        ORDER_STATUS_MAP.put(Constants.ORDER_STATUS_COMPLETED, "已送达");
        ORDER_STATUS_MAP.put(Constants.ORDER_STATUS_CANCELLED, "已取消");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVo submitOrder(Long userId, OrderSubmitDto dto) {
        // 查询购物车
        List<CartItem> cartItems = cartItemMapper.selectByUserId(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.CART_EMPTY);
        }
        
        // 计算总金额并检查库存
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        for (CartItem cartItem : cartItems) {
            Dish dish = dishMapper.selectById(cartItem.getDishId());
            if (dish == null) {
                throw new BizException(ErrorCode.DISH_NOT_FOUND);
            }
            
            // 检查库存
            if (dish.getStock() < cartItem.getQuantity()) {
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT, dish.getName() + " 库存不足");
            }
            
            // 创建订单明细
            OrderItem orderItem = new OrderItem();
            orderItem.setDishId(dish.getId());
            orderItem.setDishName(dish.getName());
            orderItem.setPrice(dish.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setSubtotal(dish.getPrice().multiply(new BigDecimal(cartItem.getQuantity())));
            orderItem.setImageUrl(dish.getImageUrl());
            orderItem.setCreateTime(LocalDateTime.now());
            orderItems.add(orderItem);
            
            totalAmount = totalAmount.add(orderItem.getSubtotal());
        }
        
        // 创建订单
        Order order = new Order();
        order.setOrderNo(IdGeneratorUtil.generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(Constants.ORDER_STATUS_PENDING);
        order.setAddress(dto.getAddress());
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setRemark(dto.getRemark());
        order.setPaymentMethod(dto.getPaymentMethod());
        
        // 处理预约配送时间
        if (dto.getDeliveryTime() != null && !dto.getDeliveryTime().isEmpty()) {
            try {
                // 尝试解析ISO格式的时间字符串
                LocalDateTime deliveryTime;
                if (dto.getDeliveryTime().contains("T")) {
                    // ISO格式：2024-12-03T14:00:00.000Z
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(dto.getDeliveryTime());
                    deliveryTime = zonedDateTime.toLocalDateTime();
                } else {
                    // 标准格式：2024-12-03 14:00:00
                    deliveryTime = LocalDateTime.parse(dto.getDeliveryTime(), 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                
                order.setDeliveryTime(deliveryTime);
                // 计算预计送达时间（预约时间 + 30分钟）
                order.setEstimatedDeliveryTime(deliveryTime.plusMinutes(30));
                
                log.info("解析预约配送时间成功：{} -> {}", dto.getDeliveryTime(), deliveryTime);
            } catch (Exception e) {
                log.error("解析预约配送时间失败：{}", dto.getDeliveryTime(), e);
                // 不抛出异常，允许订单继续创建（作为立即配送）
            }
        }
        
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        int result = orderMapper.insert(order);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "创建订单失败");
        }
        
        // 如果用户选择保存为默认地址，更新用户信息
        if (dto.getSaveAsDefault() != null && dto.getSaveAsDefault()) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setDefaultAddress(dto.getAddress());
                user.setDefaultReceiverName(dto.getReceiverName());
                user.setDefaultReceiverPhone(dto.getReceiverPhone());
                user.setUpdateTime(LocalDateTime.now());
                userMapper.update(user);
                log.info("已保存用户默认地址：userId={}", userId);
            }
        }
        
        // 插入订单明细
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
        }
        orderItemMapper.batchInsert(orderItems);
        
        // 更新库存和销量
        for (CartItem cartItem : cartItems) {
            dishMapper.updateStock(cartItem.getDishId(), cartItem.getQuantity());
            dishMapper.updateSales(cartItem.getDishId(), cartItem.getQuantity());
        }
        
        // 清空购物车
        cartItemMapper.deleteByUserId(userId);
        
        log.info("提交订单成功：orderId={}, orderNo={}", order.getId(), order.getOrderNo());
        
        // 返回订单详情
        return getOrderById(userId, order.getId());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        // 只有待支付状态的订单才能取消
        if (!Constants.ORDER_STATUS_PENDING.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单状态异常，无法取消");
        }
        
        // 恢复库存
        List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : orderItems) {
            dishMapper.updateStock(item.getDishId(), -item.getQuantity());
            dishMapper.updateSales(item.getDishId(), -item.getQuantity());
        }
        
        // 更新订单状态
        orderMapper.updateStatus(orderId, Constants.ORDER_STATUS_CANCELLED);
        
        log.info("取消订单成功：orderId={}", orderId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        // 只有待支付状态的订单才能支付
        if (!Constants.ORDER_STATUS_PENDING.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单状态异常，无法支付");
        }
        
        // 更新订单状态
        order.setStatus(Constants.ORDER_STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.update(order);
        
        log.info("支付订单成功：orderId={}", orderId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        // 只有已派送状态的订单才能完成
        if (!Constants.ORDER_STATUS_DELIVERING.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单状态异常，无法完成");
        }
        
        // 更新订单状态
        orderMapper.updateStatus(orderId, Constants.ORDER_STATUS_COMPLETED);
        
        log.info("完成订单成功：orderId={}", orderId);
    }
    
    @Override
    public OrderDetailVo getOrderById(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        OrderDetailVo vo = BeanCopyUtil.copyBean(order, OrderDetailVo.class);
        vo.setStatusDesc(ORDER_STATUS_MAP.get(order.getStatus()));
        
        // 查询订单明细
        List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
        List<OrderItemVo> itemVos = BeanCopyUtil.copyList(orderItems, OrderItemVo.class);
        vo.setItems(itemVos);
        
        return vo;
    }
    
    @Override
    public PageResult<OrderSummaryVo> getUserOrders(Long userId, Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            pageNum = Constants.DEFAULT_PAGE_NUM;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = Constants.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > Constants.MAX_PAGE_SIZE) {
            pageSize = Constants.MAX_PAGE_SIZE;
        }
        
        int offset = (pageNum - 1) * pageSize;
        
        List<Order> orders = orderMapper.selectByUserId(userId, offset, pageSize);
        Long total = orderMapper.countByUserId(userId);
        
        List<OrderSummaryVo> voList = orders.stream().map(order -> {
            OrderSummaryVo vo = BeanCopyUtil.copyBean(order, OrderSummaryVo.class);
            vo.setStatusDesc(ORDER_STATUS_MAP.get(order.getStatus()));
            
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
            List<OrderItemVo> itemVos = BeanCopyUtil.copyList(orderItems, OrderItemVo.class);
            vo.setItems(itemVos);
            vo.setItemCount(itemVos != null ? itemVos.size() : 0);
            
            return vo;
        }).toList();
        
        return new PageResult<>(voList, total, pageNum, pageSize);
    }
    
    @Override
    public PageResult<OrderSummaryVo> getAllOrders(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            pageNum = Constants.DEFAULT_PAGE_NUM;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = Constants.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > Constants.MAX_PAGE_SIZE) {
            pageSize = Constants.MAX_PAGE_SIZE;
        }
        
        int offset = (pageNum - 1) * pageSize;
        
        List<Order> orders = orderMapper.selectAll(offset, pageSize);
        Long total = orderMapper.countAll();
        
        List<OrderSummaryVo> voList = orders.stream().map(order -> {
            OrderSummaryVo vo = BeanCopyUtil.copyBean(order, OrderSummaryVo.class);
            vo.setStatusDesc(ORDER_STATUS_MAP.get(order.getStatus()));
            
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
            List<OrderItemVo> itemVos = BeanCopyUtil.copyList(orderItems, OrderItemVo.class);
            vo.setItems(itemVos);
            vo.setItemCount(itemVos != null ? itemVos.size() : 0);
            
            return vo;
        }).toList();
        
        return new PageResult<>(voList, total, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long orderId, Integer status) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        // 更新订单状态
        orderMapper.updateStatus(orderId, status);
        log.info("管理员更新订单状态：orderId={}, status={}", orderId, status);
    }
}








