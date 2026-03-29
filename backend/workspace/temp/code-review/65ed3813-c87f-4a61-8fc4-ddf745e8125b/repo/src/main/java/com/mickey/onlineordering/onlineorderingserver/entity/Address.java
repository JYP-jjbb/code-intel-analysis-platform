package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收货地址实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

