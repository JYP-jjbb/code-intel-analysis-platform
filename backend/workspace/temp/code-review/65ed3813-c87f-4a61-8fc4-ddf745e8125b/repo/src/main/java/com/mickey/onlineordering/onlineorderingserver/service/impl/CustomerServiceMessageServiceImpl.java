package com.mickey.onlineordering.onlineorderingserver.service.impl;

import com.mickey.onlineordering.onlineorderingserver.entity.CustomerServiceMessage;
import com.mickey.onlineordering.onlineorderingserver.mapper.CustomerServiceMessageMapper;
import com.mickey.onlineordering.onlineorderingserver.mapper.UserMapper;
import com.mickey.onlineordering.onlineorderingserver.service.CustomerServiceMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服消息服务实现类
 */
@Slf4j
@Service
public class CustomerServiceMessageServiceImpl implements CustomerServiceMessageService {

    @Autowired
    private CustomerServiceMessageMapper messageMapper;
    
    @Autowired
    private UserMapper userMapper;

    @Override
    public void saveMessage(CustomerServiceMessage message) {
        log.info("保存消息: sessionId={}, senderId={}, content={}", 
                message.getSessionId(), message.getSenderId(), message.getContent());
        messageMapper.insert(message);
    }

    @Override
    public List<CustomerServiceMessage> getMessagesBySessionId(String sessionId) {
        log.info("查询会话消息: sessionId={}", sessionId);
        return messageMapper.selectBySessionId(sessionId);
    }

    @Override
    public List<CustomerServiceMessage> getMessagesByUserId(Long userId) {
        log.info("查询用户历史消息: userId={}", userId);
        return messageMapper.selectByUserId(userId);
    }

    @Override
    public void markMessagesAsRead(String sessionId, Long receiverId) {
        log.info("标记消息已读: sessionId={}, receiverId={}", sessionId, receiverId);
        messageMapper.markAsRead(sessionId, receiverId);
    }

    @Override
    public int getUnreadMessageCount(Long receiverId) {
        return messageMapper.countUnreadMessages(receiverId);
    }

    @Override
    public List<String> getActiveSessions() {
        return messageMapper.selectActiveSessions();
    }
    
    @Override
    public List<Map<String, Object>> getActiveSessionsWithUserInfo() {
        List<String> sessionIds = messageMapper.selectActiveSessions();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (String sessionId : sessionIds) {
            // sessionId格式: session_userId_admin
            String[] parts = sessionId.split("_");
            if (parts.length >= 3) {
                try {
                    Long userId = Long.parseLong(parts[1]);
                    
                    // 查询用户信息
                    var user = userMapper.selectById(userId);
                    
                    // 只添加非admin用户的会话
                    if (user != null && !"admin".equals(user.getRole())) {
                        Map<String, Object> sessionInfo = new HashMap<>();
                        sessionInfo.put("sessionId", sessionId);
                        sessionInfo.put("userId", userId);
                        sessionInfo.put("username", user.getUsername());
                        sessionInfo.put("userRole", user.getRole());
                        
                        // 查询该会话中管理员的未读消息数（管理员ID为1）
                        int unreadCount = messageMapper.countUnreadMessagesBySession(sessionId, 1L);
                        sessionInfo.put("unreadCount", unreadCount);
                        
                        result.add(sessionInfo);
                    }
                } catch (NumberFormatException e) {
                    log.warn("解析sessionId失败: {}", sessionId);
                }
            }
        }
        
        return result;
    }
}

