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
 * 管理员权限拦截器
 * 拦截需要管理员权限才能访问的接口
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // GET 和 OPTIONS 请求放行（查看操作公开）
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        
        // POST/PUT/DELETE 请求需要管理员权限
        // 先尝试从request attribute获取role（LoginInterceptor已设置）
        String role = (String) request.getAttribute("role");
        
        // 如果attribute中没有，尝试从token中获取（处理登录拦截器排除的路径）
        if (role == null) {
            String token = request.getHeader(Constants.HEADER_TOKEN);
            if (token != null && !token.isEmpty()) {
                if (token.startsWith(Constants.TOKEN_PREFIX)) {
                    token = token.substring(Constants.TOKEN_PREFIX.length());
                }
                if (jwtUtil.validateToken(token)) {
                    role = jwtUtil.getRoleFromToken(token);
                    // 同时设置userId，供后续使用
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String username = jwtUtil.getUsernameFromToken(token);
                    request.setAttribute("userId", userId);
                    request.setAttribute("username", username);
                    request.setAttribute("role", role);
                }
            }
        }
        
        if (!Constants.ROLE_ADMIN.equals(role)) {
            log.warn("非管理员尝试执行管理操作：{} {}, role={}", method, request.getRequestURI(), role);
            responseForbidden(response);
            return false;
        }
        
        log.debug("管理员权限验证通过：{} {}", method, request.getRequestURI());
        return true;
    }
    

    // 返回禁止访问响应
    private void responseForbidden(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        
        Result<?> result = Result.error(ErrorCode.FORBIDDEN, "无权限访问，需要管理员权限");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
