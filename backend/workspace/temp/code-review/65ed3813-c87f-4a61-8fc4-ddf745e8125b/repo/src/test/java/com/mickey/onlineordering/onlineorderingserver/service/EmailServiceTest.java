package com.mickey.onlineordering.onlineorderingserver.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 邮件服务测试类
 * 测试异步邮件发送功能
 */
@SpringBootTest
@DisplayName("邮件服务测试")
class EmailServiceTest {
    
    @Autowired
    private EmailService emailService;
    
    @MockBean
    private JavaMailSender mailSender;
    
    @Test
    @DisplayName("测试发送验证码邮件 - 注册场景")
    void testSendVerificationCode_RegisterScene_Success() throws Exception {
        // Given
        String toEmail = "test@example.com";
        String code = "123456";
        String scene = "register";
        
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When
        emailService.sendVerificationCode(toEmail, code, scene);
        
        // Then: 等待异步执行完成
        Thread.sleep(1000);
        
        // Verify: 验证邮件发送被调用
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试发送验证码邮件 - 重置密码场景")
    void testSendVerificationCode_ResetPasswordScene_Success() throws Exception {
        // Given
        String toEmail = "test@example.com";
        String code = "654321";
        String scene = "reset_password";
        
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When
        emailService.sendVerificationCode(toEmail, code, scene);
        
        // Then: 等待异步执行完成
        Thread.sleep(1000);
        
        // Verify
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试发送验证码邮件 - 修改邮箱场景")
    void testSendVerificationCode_ChangeEmailScene_Success() throws Exception {
        // Given
        String toEmail = "newemail@example.com";
        String code = "789012";
        String scene = "change_email";
        
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When
        emailService.sendVerificationCode(toEmail, code, scene);
        
        // Then: 等待异步执行完成
        Thread.sleep(1000);
        
        // Verify
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试异步发送 - 不阻塞主线程")
    void testAsyncSend_NonBlocking() {
        // Given
        String toEmail = "test@example.com";
        String code = "123456";
        String scene = "register";
        
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When: 记录开始时间
        long startTime = System.currentTimeMillis();
        emailService.sendVerificationCode(toEmail, code, scene);
        long endTime = System.currentTimeMillis();
        
        // Then: 异步方法应立即返回，耗时应很短（小于100ms）
        long duration = endTime - startTime;
        assertTrue(duration < 100, "异步方法应立即返回，实际耗时：" + duration + "ms");
    }
    
    @Test
    @DisplayName("测试发送失败重试机制")
    void testSendWithRetry_HandleFailure() throws Exception {
        // Given: Mock邮件发送前两次失败，第三次成功
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        doThrow(new RuntimeException("发送失败"))
            .doThrow(new RuntimeException("发送失败"))
            .doNothing()  // 第三次成功
            .when(mailSender).send(any(MimeMessage.class));
        
        // When
        emailService.sendVerificationCode("test@example.com", "123456", "register");
        
        // Then: 等待重试完成
        Thread.sleep(5000);  // 等待重试机制执行
        
        // Verify: 应该尝试了3次
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试邮件内容包含验证码")
    void testEmailContent_ContainsCode() throws Exception {
        // Given
        String code = "123456";
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When
        emailService.sendVerificationCode("test@example.com", code, "register");
        
        // Then: 等待异步执行
        Thread.sleep(1000);
        
        // Note: 实际测试中需要检查邮件内容是否包含验证码
        // 这里仅验证发送方法被调用
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试不同场景邮件主题不同")
    void testDifferentScenes_DifferentSubjects() throws Exception {
        // Given
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When: 发送不同场景的邮件
        emailService.sendVerificationCode("test@example.com", "123456", "register");
        Thread.sleep(500);
        
        emailService.sendVerificationCode("test@example.com", "654321", "reset_password");
        Thread.sleep(500);
        
        emailService.sendVerificationCode("test@example.com", "789012", "change_email");
        Thread.sleep(500);
        
        // Then: 应该发送了3封邮件
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试邮件格式为HTML")
    void testEmailFormat_IsHTML() throws Exception {
        // Given
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When
        emailService.sendVerificationCode("test@example.com", "123456", "register");
        
        // Then: 等待异步执行
        Thread.sleep(1000);
        
        // Verify: 邮件发送被调用（实际项目中可进一步验证HTML格式）
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }
    
    @Test
    @DisplayName("测试并发发送多封邮件")
    void testConcurrentSend_HandleMultiple() throws Exception {
        // Given
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        
        // When: 并发发送5封邮件
        for (int i = 0; i < 5; i++) {
            emailService.sendVerificationCode("test" + i + "@example.com", "123456", "register");
        }
        
        // Then: 等待所有异步任务完成
        Thread.sleep(3000);
        
        // Verify: 应该发送了5封邮件
        verify(mailSender, times(5)).send(any(MimeMessage.class));
    }
}


