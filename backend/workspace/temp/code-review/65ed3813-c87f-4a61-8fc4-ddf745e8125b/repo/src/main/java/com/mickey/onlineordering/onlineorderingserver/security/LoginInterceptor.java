package com.mickey.onlineordering.onlineorderingserver.security;

import com.alibaba.fastjson2.JSON;
import com.mickey.onlineordering.onlineorderingserver.common.Constants;
import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 * 拦截需要登录才能访问的接口
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取Token
        String token = request.getHeader(Constants.HEADER_TOKEN);
        
        if (token == null || token.isEmpty()) {
            log.warn("请求未携带Token：{}", request.getRequestURI());
            responseUnauthorized(response, "请先登录");
            return false;
        }
        
        // 去除Bearer前缀
        if (token.startsWith(Constants.TOKEN_PREFIX)) {
            token = token.substring(Constants.TOKEN_PREFIX.length());
        }
        
        // 验证Token
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token验证失败：{}", request.getRequestURI());
            responseUnauthorized(response, "登录已过期，请重新登录");
            return false;
        }
        
        // 将用户信息存入Request属性中
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);
        request.setAttribute("role", role);
        
        log.debug("用户通过认证：userId={}, username={}", userId, username);
        
        return true;
    }
    
    /**
     * 返回未授权响应
     */
    private void responseUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        
        Result<?> result = Result.error(ErrorCode.UNAUTHORIZED, message);
        response.getWriter().write(JSON.toJSONString(result));
    }
}












