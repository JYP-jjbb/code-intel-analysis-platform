package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.entity.CustomerServiceMessage;
import com.mickey.onlineordering.onlineorderingserver.service.CustomerServiceMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服消息控制器
 */
@Slf4j
@Tag(name = "客服接口", description = "客服聊天相关接口")
@RestController
@RequestMapping("/api/customer-service")
public class CustomerServiceController {

    @Autowired
    private CustomerServiceMessageService messageService;

    /**
     * 获取会话的所有消息
     */
    @Operation(summary = "获取会话消息")
    @GetMapping("/messages/{sessionId}")
    public Result<List<CustomerServiceMessage>> getMessages(
            @PathVariable String sessionId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<CustomerServiceMessage> messages = messageService.getMessagesBySessionId(sessionId);
        
        // 标记为已读
        if (userId != null) {
            messageService.markMessagesAsRead(sessionId, userId);
        }
        
        return Result.success(messages);
    }

    /**
     * 获取用户的历史消息
     */
    @Operation(summary = "获取用户历史消息")
    @GetMapping("/history")
    public Result<List<CustomerServiceMessage>> getHistory(
            @RequestAttribute("userId") Long userId) {
        List<CustomerServiceMessage> messages = messageService.getMessagesByUserId(userId);
        return Result.success(messages);
    }

    /**
     * 获取未读消息数量
     */
    @Operation(summary = "获取未读消息数量")
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount(@RequestAttribute("userId") Long userId) {
        int count = messageService.getUnreadMessageCount(userId);
        return Result.success(count);
    }

    /**
     * 获取所有活跃会话（管理员）
     */
    @Operation(summary = "获取所有活跃会话")
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> getActiveSessions() {
        List<Map<String, Object>> sessions = messageService.getActiveSessionsWithUserInfo();
        return Result.success(sessions);
    }
}

