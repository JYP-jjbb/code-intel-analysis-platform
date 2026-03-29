package com.mickey.onlineordering.onlineorderingserver.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mickey.onlineordering.onlineorderingserver.entity.CustomerServiceMessage;
import com.mickey.onlineordering.onlineorderingserver.service.CustomerServiceMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服WebSocket处理器
 * 处理客服聊天的WebSocket连接和消息
 */
@Slf4j
@Component
public class CustomerServiceWebSocketHandler extends TextWebSocketHandler {

    private final CustomerServiceMessageService messageService;

    // 存储所有在线的WebSocket会话，key为sessionId，value为WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储用户ID到sessionId的映射
    private final Map<Long, String> userSessionMap = new ConcurrentHashMap<>();

    public CustomerServiceWebSocketHandler(CustomerServiceMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 连接建立后调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket连接建立，sessionId: {}", sessionId);
    }

    /**
     * 接收到消息时调用
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("收到消息: {}", payload);

        try {
            JSONObject jsonMessage = JSON.parseObject(payload);
            String type = jsonMessage.getString("type");

            if ("register".equals(type)) {
                // 用户注册连接
                Long userId = jsonMessage.getLong("userId");
                String sessionId = session.getId();
                userSessionMap.put(userId, sessionId);
                log.info("用户 {} 注册WebSocket连接，sessionId: {}", userId, sessionId);
                
                // 发送注册成功消息
                sendMessageToSession(session, createSystemMessage("连接成功"));
                
            } else if ("message".equals(type)) {
                // 接收并保存消息
                CustomerServiceMessage msg = new CustomerServiceMessage();
                msg.setSessionId(jsonMessage.getString("sessionId"));
                msg.setSenderId(jsonMessage.getLong("senderId"));
                msg.setSenderName(jsonMessage.getString("senderName"));
                msg.setSenderRole(jsonMessage.getString("senderRole"));
                msg.setReceiverId(jsonMessage.getLong("receiverId"));
                msg.setMessageType(jsonMessage.getString("messageType"));
                msg.setContent(jsonMessage.getString("content"));
                msg.setIsRead(0);
                msg.setCreateTime(LocalDateTime.now());

                // 保存消息到数据库
                messageService.saveMessage(msg);

                // 转发消息给接收者
                Long receiverId = msg.getReceiverId();
                if (receiverId != null && userSessionMap.containsKey(receiverId)) {
                    String receiverSessionId = userSessionMap.get(receiverId);
                    WebSocketSession receiverSession = sessions.get(receiverSessionId);
                    if (receiverSession != null && receiverSession.isOpen()) {
                        sendMessageToSession(receiverSession, jsonMessage.toJSONString());
                    }
                }

                // 回复发送者消息已送达
                sendMessageToSession(session, createSystemMessage("消息已发送"));
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            sendMessageToSession(session, createSystemMessage("消息处理失败: " + e.getMessage()));
        }
    }

    /**
     * 连接关闭后调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        // 移除用户映射
        userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
        
        log.info("WebSocket连接关闭，sessionId: {}, 状态: {}", sessionId, status);
    }

    /**
     * 传输错误时调用
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误，sessionId: {}", session.getId(), exception);
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * 发送消息到指定会话
     */
    private void sendMessageToSession(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }

    /**
     * 创建系统消息
     */
    private String createSystemMessage(String content) {
        JSONObject msg = new JSONObject();
        msg.put("type", "system");
        msg.put("content", content);
        msg.put("timestamp", System.currentTimeMillis());
        return msg.toJSONString();
    }
}

