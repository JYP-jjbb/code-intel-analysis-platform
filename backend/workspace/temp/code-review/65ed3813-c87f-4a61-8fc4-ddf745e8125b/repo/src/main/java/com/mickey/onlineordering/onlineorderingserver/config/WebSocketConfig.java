package com.mickey.onlineordering.onlineorderingserver.config;

import com.mickey.onlineordering.onlineorderingserver.websocket.CustomerServiceWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 配置客服聊天的WebSocket端点
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CustomerServiceWebSocketHandler customerServiceWebSocketHandler;

    public WebSocketConfig(CustomerServiceWebSocketHandler customerServiceWebSocketHandler) {
        this.customerServiceWebSocketHandler = customerServiceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器，设置连接端点
        registry.addHandler(customerServiceWebSocketHandler, "/ws/customer-service")
                .setAllowedOrigins("*"); // 允许跨域访问
    }
}

