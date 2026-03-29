package com.mickey.onlineordering.onlineorderingserver.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码加密工具类测试
 * 测试密码的加密和验证功能
 */
@SpringBootTest
@DisplayName("密码加密工具类测试")
class PasswordEncoderUtilTest {
    
    @Autowired
    private PasswordEncoderUtil passwordEncoderUtil;
    
    @Test
    @DisplayName("测试密码加密 - 正常密码")
    void testEncode_WithNormalPassword_ReturnEncoded() {
        // Given
        String rawPassword = "123456";
        
        // When
        String encodedPassword = passwordEncoderUtil.encode(rawPassword);
        
        // Then
        assertNotNull(encodedPassword, "加密后的密码不应为null");
        assertNotEquals(rawPassword, encodedPassword, "加密后应与原密码不同");
        assertTrue(encodedPassword.startsWith("$2a$"), "BCrypt加密应以$2a$开头");
    }
    
    @Test
    @DisplayName("测试密码加密 - 相同密码生成不同Hash")
    void testEncode_SamePasswordDifferentHash() {
        // Given
        String rawPassword = "123456";
        
        // When
        String hash1 = passwordEncoderUtil.encode(rawPassword);
        String hash2 = passwordEncoderUtil.encode(rawPassword);
        
        // Then
        assertNotEquals(hash1, hash2, "相同密码每次加密应生成不同的Hash（因为salt不同）");
    }
    
    @Test
    @DisplayName("测试密码验证 - 正确密码")
    void testMatches_WithCorrectPassword_ReturnTrue() {
        // Given
        String rawPassword = "123456";
        String encodedPassword = passwordEncoderUtil.encode(rawPassword);
        
        // When
        boolean matches = passwordEncoderUtil.matches(rawPassword, encodedPassword);
        
        // Then
        assertTrue(matches, "正确的密码应验证通过");
    }
    
    @Test
    @DisplayName("测试密码验证 - 错误密码")
    void testMatches_WithWrongPassword_ReturnFalse() {
        // Given
        String rawPassword = "123456";
        String wrongPassword = "654321";
        String encodedPassword = passwordEncoderUtil.encode(rawPassword);
        
        // When
        boolean matches = passwordEncoderUtil.matches(wrongPassword, encodedPassword);
        
        // Then
        assertFalse(matches, "错误的密码应验证失败");
    }
    
    @Test
    @DisplayName("测试密码验证 - 空密码")
    void testMatches_WithEmptyPassword_ReturnFalse() {
        // Given
        String encodedPassword = passwordEncoderUtil.encode("123456");
        
        // When & Then
        assertFalse(passwordEncoderUtil.matches("", encodedPassword), "空密码应验证失败");
        assertFalse(passwordEncoderUtil.matches(null, encodedPassword), "null密码应验证失败");
    }
    
    @Test
    @DisplayName("测试密码强度 - 各种长度的密码")
    void testEncode_WithVariousLengthPasswords() {
        // Given & When & Then
        assertDoesNotThrow(() -> {
            passwordEncoderUtil.encode("123");          // 短密码
            passwordEncoderUtil.encode("123456");       // 普通密码
            passwordEncoderUtil.encode("a1b2c3d4e5");   // 中等密码
            passwordEncoderUtil.encode("VeryLongPassword123456!@#$%"); // 长密码
        }, "各种长度的密码都应能正常加密");
    }
    
    @Test
    @DisplayName("测试密码验证 - 大小写敏感")
    void testMatches_CaseSensitive() {
        // Given
        String password = "Password123";
        String encodedPassword = passwordEncoderUtil.encode(password);
        
        // When & Then
        assertTrue(passwordEncoderUtil.matches("Password123", encodedPassword), "完全匹配应成功");
        assertFalse(passwordEncoderUtil.matches("password123", encodedPassword), "大小写不同应失败");
        assertFalse(passwordEncoderUtil.matches("PASSWORD123", encodedPassword), "大小写不同应失败");
    }
    
    @Test
    @DisplayName("测试完整流程 - 注册到登录")
    void testFullWorkflow_RegisterAndLogin() {
        // Given: 模拟用户注册时加密密码
        String userInputPassword = "myPassword123";
        String storedPassword = passwordEncoderUtil.encode(userInputPassword);
        
        // When: 模拟用户登录时验证密码
        boolean loginSuccess = passwordEncoderUtil.matches(userInputPassword, storedPassword);
        boolean loginFail = passwordEncoderUtil.matches("wrongPassword", storedPassword);
        
        // Then
        assertTrue(loginSuccess, "使用正确密码登录应成功");
        assertFalse(loginFail, "使用错误密码登录应失败");
    }
    
    @Test
    @DisplayName("测试特殊字符密码")
    void testEncode_WithSpecialCharacters() {
        // Given
        String specialPassword = "P@ssw0rd!#$%^&*()";
        
        // When
        String encodedPassword = passwordEncoderUtil.encode(specialPassword);
        
        // Then
        assertNotNull(encodedPassword);
        assertTrue(passwordEncoderUtil.matches(specialPassword, encodedPassword), "特殊字符密码应正确加密和验证");
    }
    
    @Test
    @DisplayName("测试中文密码")
    void testEncode_WithChineseCharacters() {
        // Given
        String chinesePassword = "中文密码123";
        
        // When
        String encodedPassword = passwordEncoderUtil.encode(chinesePassword);
        
        // Then
        assertNotNull(encodedPassword);
        assertTrue(passwordEncoderUtil.matches(chinesePassword, encodedPassword), "中文密码应正确加密和验证");
    }
}


