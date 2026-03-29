package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    int insert(User user);
    int update(User user);
    int deleteById(@Param("id") Long id);
    int countByUsername(@Param("username") String username);
    int countByEmail(@Param("email") String email);
}









