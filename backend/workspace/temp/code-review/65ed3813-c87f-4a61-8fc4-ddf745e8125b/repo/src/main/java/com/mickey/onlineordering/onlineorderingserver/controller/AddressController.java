package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.entity.Address;
import com.mickey.onlineordering.onlineorderingserver.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地址管理控制器
 * 处理收货地址相关请求
 */
@Tag(name = "地址接口", description = "收货地址相关接口")
@Slf4j
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressService addressService;

    /**
     * 获取当前用户的所有地址
     */
    @Operation(summary = "获取地址列表")
    @GetMapping
    public Result<List<Address>> getAddresses(@RequestAttribute("userId") Long userId) {
        log.info("获取地址列表，用户ID: {}", userId);
        List<Address> addresses = addressService.getUserAddresses(userId);
        return Result.success(addresses);
    }

    /**
     * 获取地址详情
     */
    @Operation(summary = "获取地址详情")
    @GetMapping("/{id}")
    public Result<Address> getAddressById(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        log.info("获取地址详情，地址ID: {}, 用户ID: {}", id, userId);
        Address address = addressService.getAddressById(id);
        
        if (address == null) {
            return Result.error(404, "地址不存在");
        }
        
        // 验证地址是否属于当前用户
        if (!address.getUserId().equals(userId)) {
            return Result.error(403, "无权访问该地址");
        }
        
        return Result.success(address);
    }

    /**
     * 添加地址
     */
    @Operation(summary = "添加地址")
    @PostMapping
    public Result<Address> addAddress(@RequestAttribute("userId") Long userId, @RequestBody Address address) {
        log.info("添加地址，用户ID: {}, 地址: {}", userId, address.getAddress());
        
        // 设置用户ID
        address.setUserId(userId);
        
        Address savedAddress = addressService.addAddress(address);
        return Result.success(savedAddress);
    }

    /**
     * 更新地址
     */
    @Operation(summary = "更新地址")
    @PutMapping("/{id}")
    public Result<Address> updateAddress(@PathVariable Long id, @RequestBody Address address, 
                                         @RequestAttribute("userId") Long userId) {
        log.info("更新地址，地址ID: {}, 用户ID: {}", id, userId);
        
        // 验证地址是否属于当前用户
        Address existingAddress = addressService.getAddressById(id);
        if (existingAddress == null) {
            return Result.error(404, "地址不存在");
        }
        
        if (!existingAddress.getUserId().equals(userId)) {
            return Result.error(403, "无权修改该地址");
        }
        
        Address updatedAddress = addressService.updateAddress(id, address);
        return Result.success(updatedAddress);
    }

    /**
     * 删除地址
     */
    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        log.info("删除地址，地址ID: {}, 用户ID: {}", id, userId);
        
        // 验证地址是否属于当前用户
        Address existingAddress = addressService.getAddressById(id);
        if (existingAddress == null) {
            return Result.error(404, "地址不存在");
        }
        
        if (!existingAddress.getUserId().equals(userId)) {
            return Result.error(403, "无权删除该地址");
        }
        
        addressService.deleteAddress(id);
        return Result.success(null);
    }

    /**
     * 设置默认地址
     */
    @Operation(summary = "设置默认地址")
    @PutMapping("/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        log.info("设置默认地址，地址ID: {}, 用户ID: {}", id, userId);
        
        // 验证地址是否属于当前用户
        Address existingAddress = addressService.getAddressById(id);
        if (existingAddress == null) {
            return Result.error(404, "地址不存在");
        }
        
        if (!existingAddress.getUserId().equals(userId)) {
            return Result.error(403, "无权操作该地址");
        }
        
        addressService.setDefaultAddress(id, userId);
        return Result.success(null);
    }
}

