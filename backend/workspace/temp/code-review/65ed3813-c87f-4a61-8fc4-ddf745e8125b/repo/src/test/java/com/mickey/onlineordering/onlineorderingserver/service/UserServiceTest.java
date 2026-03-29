package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.dto.LoginRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.RegisterRequestDto;
import com.mickey.onlineordering.onlineorderingserver.entity.User;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.UserMapper;
import com.mickey.onlineordering.onlineorderingserver.security.CaptchaService;
import com.mickey.onlineordering.onlineorderingserver.service.impl.UserServiceImpl;
import com.mickey.onlineordering.onlineorderingserver.util.JwtUtil;
import com.mickey.onlineordering.onlineorderingserver.util.PasswordEncoderUtil;
import com.mickey.onlineordering.onlineorderingserver.vo.LoginVo;
import com.mickey.onlineordering.onlineorderingserver.vo.UserProfileVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务测试类
 * 测试用户注册、登录、信息管理等功能
 */
@SpringBootTest
@DisplayName("用户服务测试")
class UserServiceTest {
    
    @Mock
    private UserMapper userMapper;
    
    @Mock
    private PasswordEncoderUtil passwordEncoderUtil;
    
    @Mock
    private JwtUtil jwtUtil;
    
    @Mock
    private CaptchaService captchaService;
    
    @Mock
    private EmailCodeService emailCodeService;
    
    @InjectMocks
    private UserServiceImpl userService;
    
    private RegisterRequestDto registerRequest;
    private LoginRequestDto loginRequest;
    private User mockUser;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 准备注册请求数据
        registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("123456");
        registerRequest.setConfirmPassword("123456");
        registerRequest.setPhone("13800138000");
        registerRequest.setEmail("test@example.com");
        registerRequest.setEmailCode("123456");
        registerRequest.setCaptcha("ABCD");
        registerRequest.setCaptchaId("test-captcha-id");
        
        // 准备登录请求数据
        loginRequest = new LoginRequestDto();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("123456");
        loginRequest.setCaptcha("ABCD");
        loginRequest.setCaptchaId("test-captcha-id");
        
        // 准备Mock用户数据
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPassword("$2a$10$encoded_password");
        mockUser.setEmail("test@example.com");
        mockUser.setPhone("13800138000");
        mockUser.setRole("user");
        mockUser.setStatus(1);
    }
    
    @Test
    @DisplayName("测试用户注册 - 成功场景")
    void testRegister_WithValidData_Success() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        doNothing().when(emailCodeService).verifyCode(anyString(), anyString(), anyString());
        when(userMapper.countByUsername(anyString())).thenReturn(0);
        when(userMapper.countByEmail(anyString())).thenReturn(0);
        when(passwordEncoderUtil.encode(anyString())).thenReturn("$2a$10$encoded_password");
        when(userMapper.insert(any(User.class))).thenReturn(1);
        
        // When & Then
        assertDoesNotThrow(() -> userService.register(registerRequest));
        
        // Verify
        verify(captchaService, times(1)).verifyCaptcha(anyString(), anyString());
        verify(emailCodeService, times(1)).verifyCode(anyString(), anyString(), anyString());
        verify(userMapper, times(1)).countByUsername(anyString());
        verify(userMapper, times(1)).countByEmail(anyString());
        verify(userMapper, times(1)).insert(any(User.class));
    }
    
    @Test
    @DisplayName("测试用户注册 - 两次密码不一致")
    void testRegister_WithMismatchedPassword_ThrowException() {
        // Given
        registerRequest.setConfirmPassword("different_password");
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.register(registerRequest);
        });
        
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("两次密码"));
    }
    
    @Test
    @DisplayName("测试用户注册 - 图形验证码错误")
    void testRegister_WithWrongCaptcha_ThrowException() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(false);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.register(registerRequest);
        });
        
        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试用户注册 - 用户名已存在")
    void testRegister_WithExistingUsername_ThrowException() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        doNothing().when(emailCodeService).verifyCode(anyString(), anyString(), anyString());
        when(userMapper.countByUsername(anyString())).thenReturn(1);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.register(registerRequest);
        });
        
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试用户注册 - 邮箱已存在")
    void testRegister_WithExistingEmail_ThrowException() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        doNothing().when(emailCodeService).verifyCode(anyString(), anyString(), anyString());
        when(userMapper.countByUsername(anyString())).thenReturn(0);
        when(userMapper.countByEmail(anyString())).thenReturn(1);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.register(registerRequest);
        });
        
        assertEquals(ErrorCode.EMAIL_EXISTS.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试用户登录 - 成功场景")
    void testLogin_WithValidCredentials_Success() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectByUsername(anyString())).thenReturn(mockUser);
        when(passwordEncoderUtil.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("mock-jwt-token");
        
        // When
        LoginVo result = userService.login(loginRequest);
        
        // Then
        assertNotNull(result);
        assertEquals("mock-jwt-token", result.getToken());
        assertNotNull(result.getUser());
        assertEquals("testuser", result.getUser().getUsername());
        
        // Verify
        verify(userMapper, times(1)).selectByUsername("testuser");
        verify(passwordEncoderUtil, times(1)).matches(anyString(), anyString());
        verify(jwtUtil, times(1)).generateToken(anyLong(), anyString(), anyString());
    }
    
    @Test
    @DisplayName("测试用户登录 - 用户不存在")
    void testLogin_WithNonexistentUser_ThrowException() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectByUsername(anyString())).thenReturn(null);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.login(loginRequest);
        });
        
        assertEquals(ErrorCode.LOGIN_ERROR.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试用户登录 - 密码错误")
    void testLogin_WithWrongPassword_ThrowException() {
        // Given
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectByUsername(anyString())).thenReturn(mockUser);
        when(passwordEncoderUtil.matches(anyString(), anyString())).thenReturn(false);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.login(loginRequest);
        });
        
        assertEquals(ErrorCode.LOGIN_ERROR.getCode(), exception.getCode());
    }
    
    @Test
    @DisplayName("测试用户登录 - 账号被禁用")
    void testLogin_WithDisabledAccount_ThrowException() {
        // Given
        mockUser.setStatus(0);  // 禁用状态
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectByUsername(anyString())).thenReturn(mockUser);
        when(passwordEncoderUtil.matches(anyString(), anyString())).thenReturn(true);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.login(loginRequest);
        });
        
        assertTrue(exception.getMessage().contains("禁用"));
    }
    
    @Test
    @DisplayName("测试获取用户信息 - 成功")
    void testGetUserById_WithValidId_ReturnUser() {
        // Given
        when(userMapper.selectById(anyLong())).thenReturn(mockUser);
        
        // When
        UserProfileVo result = userService.getUserById(1L);
        
        // Then
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        
        // Verify
        verify(userMapper, times(1)).selectById(1L);
    }
    
    @Test
    @DisplayName("测试获取用户信息 - 用户不存在")
    void testGetUserById_WithNonexistentId_ThrowException() {
        // Given
        when(userMapper.selectById(anyLong())).thenReturn(null);
        
        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            userService.getUserById(999L);
        });
        
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
    }
}


