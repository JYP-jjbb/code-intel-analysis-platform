package com.mickey.onlineordering.onlineorderingserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户信息更新DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private String phone;
    private String address;
    private String avatar;
    private String email;
}












