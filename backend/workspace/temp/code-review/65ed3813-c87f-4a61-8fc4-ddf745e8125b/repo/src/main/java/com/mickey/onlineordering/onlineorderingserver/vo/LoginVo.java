package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录返回VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private String token;
    private UserProfileVo user;
}











