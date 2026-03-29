package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地址Mapper接口
 */
@Mapper
public interface AddressMapper {

    List<Address> findByUserId(@Param("userId") Long userId);
    Address findById(@Param("id") Long id);
    int insert(Address address);
    int update(Address address);
    int deleteById(@Param("id") Long id);
    int clearDefaultByUserId(@Param("userId") Long userId);
    int setDefaultById(@Param("id") Long id);
}

