package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.entity.CustomerServiceMessage;

import java.util.List;
import java.util.Map;

/**
 * 客服消息服务接口
 */
public interface CustomerServiceMessageService {

    void saveMessage(CustomerServiceMessage message);
    List<CustomerServiceMessage> getMessagesBySessionId(String sessionId);
    List<CustomerServiceMessage> getMessagesByUserId(Long userId);
    void markMessagesAsRead(String sessionId, Long receiverId);
    int getUnreadMessageCount(Long receiverId);
    List<String> getActiveSessions();
    List<Map<String, Object>> getActiveSessionsWithUserInfo();
}

