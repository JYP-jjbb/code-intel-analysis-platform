package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import com.mickey.onlineordering.onlineorderingserver.dto.LoginRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.RegisterRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.SendEmailCodeRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.UserUpdateDto;
import com.mickey.onlineordering.onlineorderingserver.security.CaptchaService;
import com.mickey.onlineordering.onlineorderingserver.service.EmailCodeService;
import com.mickey.onlineordering.onlineorderingserver.service.EmailService;
import com.mickey.onlineordering.onlineorderingserver.service.UserService;
import com.mickey.onlineordering.onlineorderingserver.vo.CaptchaVo;
import com.mickey.onlineordering.onlineorderingserver.vo.LoginVo;
import com.mickey.onlineordering.onlineorderingserver.vo.UserProfileVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理登录、注册、验证码等相关请求
 */
@Slf4j
@Tag(name = "认证接口", description = "用户认证相关接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private CaptchaService captchaService;
    
    @Autowired
    private EmailCodeService emailCodeService;
    
    @Autowired
    private EmailService emailService;
    
    /**
     * 获取图形验证码
     */
    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<CaptchaVo> getCaptcha() {
        CaptchaVo captcha = captchaService.generateCaptcha();
        return Result.success(captcha);
    }
    
    /**
     * 发送邮箱验证码
     * 企业级功能：包含完整的限流、安全验证、异步发送机制
     */
    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email/code")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequestDto dto,
                                      HttpServletRequest request) {
        log.info("收到发送邮箱验证码请求: email={}, scene={}", dto.getEmail(), dto.getScene());
        
        // 1. 验证图形验证码（防止机器人攻击）
        if (!captchaService.verifyCaptcha(dto.getCaptchaId(), dto.getCaptcha())) {
            return Result.error(com.mickey.onlineordering.onlineorderingserver.common.ErrorCode.CAPTCHA_ERROR);
        }
        
        // 2. 获取客户端IP地址
        String clientIp = getClientIpAddress(request);
        log.info("客户端IP: {}", clientIp);
        
        // 3. 生成并存储验证码（包含限流检查）
        String code = emailCodeService.generateAndStoreCode(dto.getEmail(), clientIp, dto.getScene());
        
        // 4. 异步发送邮件
        emailService.sendVerificationCode(dto.getEmail(), code, dto.getScene());
        
        log.info("邮箱验证码发送请求处理完成: email={}", dto.getEmail());
        
        return Result.success("验证码已发送，请查收邮件", null);
    }
    
    /**
     * 用户注册
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequestDto dto) {
        userService.register(dto);
        return Result.success("注册成功", null);
    }
    
    /**
     * 用户登录
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVo> login(@Valid @RequestBody LoginRequestDto dto) {
        LoginVo loginVo = userService.login(dto);
        return Result.success(loginVo);
    }
    
    /**
     * 获取当前用户信息
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/profile")
    public Result<UserProfileVo> getProfile(@RequestAttribute("userId") Long userId) {
        UserProfileVo userProfile = userService.getUserById(userId);
        return Result.success(userProfile);
    }
    
    /**
     * 更新用户信息
     */
    @Operation(summary = "更新用户信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestAttribute("userId") Long userId,
                                      @RequestBody UserUpdateDto dto) {
        userService.updateUser(userId, dto);
        return Result.success("更新成功", null);
    }
    
    /**
     * 获取客户端真实IP地址
     * 考虑了各种代理和负载均衡的情况
     *
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 对于多级代理的情况，第一个IP为客户端真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}









