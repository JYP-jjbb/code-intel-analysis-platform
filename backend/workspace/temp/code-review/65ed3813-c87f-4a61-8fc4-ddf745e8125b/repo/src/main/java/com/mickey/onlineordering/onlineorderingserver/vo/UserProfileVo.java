package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String username;
    private String role;
    private String phone;
    private String address;
    private String avatar;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
}











