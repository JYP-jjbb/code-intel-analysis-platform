package com.mickey.onlineordering.onlineorderingserver.controller;

import com.alibaba.fastjson2.JSON;
import com.mickey.onlineordering.onlineorderingserver.dto.RegisterRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.SendEmailCodeRequestDto;
import com.mickey.onlineordering.onlineorderingserver.security.CaptchaService;
import com.mickey.onlineordering.onlineorderingserver.service.EmailCodeService;
import com.mickey.onlineordering.onlineorderingserver.service.EmailService;
import com.mickey.onlineordering.onlineorderingserver.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证控制器测试类
 * 测试登录、注册、验证码等认证相关API接口
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("认证接口测试")
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private CaptchaService captchaService;
    
    @MockBean
    private EmailCodeService emailCodeService;
    
    @MockBean
    private EmailService emailService;
    
    @BeforeEach
    void setUp() {
        // Mock图形验证码校验，默认通过
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
    }
    
    @Test
    @DisplayName("测试获取图形验证码 - 成功")
    void testGetCaptcha_ReturnSuccess() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.captchaId").exists())
                .andExpect(jsonPath("$.data.captchaImage").exists());
    }
    
    @Test
    @DisplayName("测试发送邮箱验证码 - 成功")
    void testSendEmailCode_WithValidRequest_ReturnSuccess() throws Exception {
        // Given
        SendEmailCodeRequestDto request = new SendEmailCodeRequestDto();
        request.setEmail("test@example.com");
        request.setScene("register");
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        when(emailCodeService.generateAndStoreCode(anyString(), anyString(), anyString()))
                .thenReturn("123456");
        
        // When & Then
        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("验证码已发送，请查收邮件"));
        
        // Verify: 验证码生成被调用
        verify(emailCodeService, times(1)).generateAndStoreCode(
                eq("test@example.com"),
                anyString(),
                eq("register")
        );
        
        // Verify: 异步发送邮件被调用
        verify(emailService, times(1)).sendVerificationCode(
                eq("test@example.com"),
                eq("123456"),
                eq("register")
        );
    }
    
    @Test
    @DisplayName("测试发送邮箱验证码 - 邮箱格式错误")
    void testSendEmailCode_WithInvalidEmail_ReturnError() throws Exception {
        // Given
        SendEmailCodeRequestDto request = new SendEmailCodeRequestDto();
        request.setEmail("invalid-email");  // 错误的邮箱格式
        request.setScene("register");
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        // When & Then
        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isBadRequest());  // 400 参数校验失败
    }
    
    @Test
    @DisplayName("测试发送邮箱验证码 - 图形验证码错误")
    void testSendEmailCode_WithWrongCaptcha_ReturnError() throws Exception {
        // Given
        SendEmailCodeRequestDto request = new SendEmailCodeRequestDto();
        request.setEmail("test@example.com");
        request.setScene("register");
        request.setCaptcha("WRONG");
        request.setCaptchaId("test-captcha-id");
        
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(false);
        
        // When & Then
        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));  // CAPTCHA_ERROR
    }
    
    @Test
    @DisplayName("测试发送邮箱验证码 - 缺少必填参数")
    void testSendEmailCode_WithMissingParameters_ReturnError() throws Exception {
        // Given: 缺少邮箱参数
        SendEmailCodeRequestDto request = new SendEmailCodeRequestDto();
        request.setScene("register");
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        // When & Then
        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isBadRequest());  // 参数校验失败
    }
    
    @Test
    @DisplayName("测试用户注册 - 成功")
    void testRegister_WithValidRequest_ReturnSuccess() throws Exception {
        // Given
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setConfirmPassword("123456");
        request.setPhone("13800138000");
        request.setEmail("test@example.com");
        request.setEmailCode("123456");
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        doNothing().when(userService).register(any(RegisterRequestDto.class));
        
        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("注册成功"));
        
        // Verify: 注册方法被调用
        verify(userService, times(1)).register(any(RegisterRequestDto.class));
    }
    
    @Test
    @DisplayName("测试用户注册 - 用户名格式错误")
    void testRegister_WithInvalidUsername_ReturnError() throws Exception {
        // Given: 用户名过短
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername("ab");  // 少于4位
        request.setPassword("123456");
        request.setConfirmPassword("123456");
        request.setPhone("13800138000");
        request.setEmail("test@example.com");
        request.setEmailCode("123456");
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isBadRequest());  // 参数校验失败
    }
    
    @Test
    @DisplayName("测试用户注册 - 缺少邮箱验证码")
    void testRegister_WithoutEmailCode_ReturnError() throws Exception {
        // Given: 缺少邮箱验证码
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setConfirmPassword("123456");
        request.setPhone("13800138000");
        request.setEmail("test@example.com");
        // 缺少emailCode
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isBadRequest());  // 参数校验失败
    }
    
    @Test
    @DisplayName("测试用户注册 - 手机号格式错误")
    void testRegister_WithInvalidPhone_ReturnError() throws Exception {
        // Given: 手机号格式错误
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setConfirmPassword("123456");
        request.setPhone("12345");  // 格式错误
        request.setEmail("test@example.com");
        request.setEmailCode("123456");
        request.setCaptcha("ABCD");
        request.setCaptchaId("test-captcha-id");
        
        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isBadRequest());  // 参数校验失败
    }
    
    @Test
    @DisplayName("测试完整注册流程 - 发送验证码到注册")
    void testFullRegistrationFlow_Success() throws Exception {
        // Step 1: 发送邮箱验证码
        SendEmailCodeRequestDto codeRequest = new SendEmailCodeRequestDto();
        codeRequest.setEmail("test@example.com");
        codeRequest.setScene("register");
        codeRequest.setCaptcha("ABCD");
        codeRequest.setCaptchaId("test-captcha-id");
        
        when(emailCodeService.generateAndStoreCode(anyString(), anyString(), anyString()))
                .thenReturn("123456");
        
        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(codeRequest)))
                .andExpect(status().isOk());
        
        // Step 2: 用户注册
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("123456");
        registerRequest.setConfirmPassword("123456");
        registerRequest.setPhone("13800138000");
        registerRequest.setEmail("test@example.com");
        registerRequest.setEmailCode("123456");
        registerRequest.setCaptcha("EFGH");
        registerRequest.setCaptchaId("test-captcha-id-2");
        
        doNothing().when(userService).register(any(RegisterRequestDto.class));
        
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        
        // Verify: 完整流程被执行
        verify(emailCodeService, times(1)).generateAndStoreCode(anyString(), anyString(), anyString());
        verify(emailService, times(1)).sendVerificationCode(anyString(), anyString(), anyString());
        verify(userService, times(1)).register(any(RegisterRequestDto.class));
    }
}

