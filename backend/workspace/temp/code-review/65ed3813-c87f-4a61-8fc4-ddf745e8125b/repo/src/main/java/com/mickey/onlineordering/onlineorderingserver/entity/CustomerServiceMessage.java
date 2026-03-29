package com.mickey.onlineordering.onlineorderingserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客服消息实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Long id;
    private String sessionId;
    private Long senderId;
    private String senderName;
    private String senderRole;
    private Long receiverId;
    private String messageType;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
