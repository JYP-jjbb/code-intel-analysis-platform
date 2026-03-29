package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.dto.LoginRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.RegisterRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.UserUpdateDto;
import com.mickey.onlineordering.onlineorderingserver.vo.LoginVo;
import com.mickey.onlineordering.onlineorderingserver.vo.UserProfileVo;

/**
 * 用户服务接口
 */
public interface UserService {

    void register(RegisterRequestDto dto);
    LoginVo login(LoginRequestDto dto);
    UserProfileVo getUserById(Long userId);
    void updateUser(Long userId, UserUpdateDto dto);
}











