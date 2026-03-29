package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.entity.Address;
import com.mickey.onlineordering.onlineorderingserver.mapper.AddressMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 地址服务类
 */
@Slf4j
@Service
public class AddressService {

    @Autowired
    private AddressMapper addressMapper;

    /**
     * 获取用户的所有地址
     */
    public List<Address> getUserAddresses(Long userId) {
        log.info("获取用户地址列表，用户ID: {}", userId);
        return addressMapper.findByUserId(userId);
    }

    /**
     * 根据ID获取地址详情
     */
    public Address getAddressById(Long id) {
        log.info("获取地址详情，地址ID: {}", id);
        return addressMapper.findById(id);
    }

    /**
     * 添加地址
     */
    @Transactional
    public Address addAddress(Address address) {
        log.info("添加地址，用户ID: {}, 地址: {}", address.getUserId(), address.getAddress());
        
        // 如果设置为默认地址，先取消其他默认地址
        if (address.getIsDefault() != null && address.getIsDefault()) {
            addressMapper.clearDefaultByUserId(address.getUserId());
        }
        
        addressMapper.insert(address);
        return address;
    }

    /**
     * 更新地址
     */
    @Transactional
    public Address updateAddress(Long id, Address address) {
        log.info("更新地址，地址ID: {}", id);
        
        Address existingAddress = addressMapper.findById(id);
        if (existingAddress == null) {
            throw new RuntimeException("地址不存在");
        }
        
        address.setId(id);
        address.setUserId(existingAddress.getUserId());
        
        // 如果设置为默认地址，先取消其他默认地址
        if (address.getIsDefault() != null && address.getIsDefault()) {
            addressMapper.clearDefaultByUserId(existingAddress.getUserId());
        }
        
        addressMapper.update(address);
        return addressMapper.findById(id);
    }

    /**
     * 删除地址
     */
    @Transactional
    public void deleteAddress(Long id) {
        log.info("删除地址，地址ID: {}", id);
        addressMapper.deleteById(id);
    }

    /**
     * 设置默认地址
     */
    @Transactional
    public void setDefaultAddress(Long id, Long userId) {
        log.info("设置默认地址，地址ID: {}, 用户ID: {}", id, userId);
        
        // 先取消该用户的所有默认地址
        addressMapper.clearDefaultByUserId(userId);
        
        // 设置新的默认地址
        addressMapper.setDefaultById(id);
    }
}

