package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库表：tb_user
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String username;
    private String password;
    private String role;
    private String phone;
    private String address;
    private String defaultAddress;
    private String defaultReceiverName;
    private String defaultReceiverPhone;
    private String avatar;
    private String email;
    private Integer status; // 账号状态（0-禁用，1-启用）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}












