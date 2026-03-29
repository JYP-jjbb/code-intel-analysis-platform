package com.mickey.onlineordering.onlineorderingserver.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT工具类测试
 * 测试JWT Token的生成、解析和验证
 */
@SpringBootTest
@DisplayName("JWT工具类测试")
class JwtUtilTest {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_ROLE = "user";
    
    @Test
    @DisplayName("测试生成Token - 正常参数")
    void testGenerateToken_WithValidParameters_ReturnToken() {
        // When
        String token = jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        
        // Then
        assertNotNull(token, "Token不应为null");
        assertFalse(token.isEmpty(), "Token不应为空字符串");
        assertTrue(token.split("\\.").length == 3, "JWT应包含三部分");
    }
    
    @Test
    @DisplayName("测试解析Token - 有效Token")
    void testParseToken_WithValidToken_ReturnClaims() {
        // Given
        String token = jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        
        // When
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        
        // Then
        assertEquals(TEST_USER_ID, userId, "用户ID应匹配");
        assertEquals(TEST_USERNAME, username, "用户名应匹配");
        assertEquals(TEST_ROLE, role, "角色应匹配");
    }
    
    @Test
    @DisplayName("测试验证Token - 有效Token")
    void testValidateToken_WithValidToken_ReturnTrue() {
        // Given
        String token = jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        
        // When
        boolean isValid = jwtUtil.validateToken(token);
        
        // Then
        assertTrue(isValid, "有效Token应验证通过");
    }
    
    @Test
    @DisplayName("测试验证Token - 无效Token")
    void testValidateToken_WithInvalidToken_ReturnFalse() {
        // Given
        String invalidToken = "invalid.token.string";
        
        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);
        
        // Then
        assertFalse(isValid, "无效Token应验证失败");
    }
    
    @Test
    @DisplayName("测试验证Token - Token为null")
    void testValidateToken_WithNullToken_ReturnFalse() {
        // When
        boolean isValid = jwtUtil.validateToken(null);
        
        // Then
        assertFalse(isValid, "null Token应验证失败");
    }
    
    @Test
    @DisplayName("测试Token一致性 - 相同参数生成不同Token")
    void testTokenConsistency_SameParametersDifferentTokens() {
        // When: 使用相同参数生成两次
        String token1 = jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        String token2 = jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        
        // Then: 因为包含时间戳，所以应该不同
        assertNotEquals(token1, token2, "相同参数生成的Token应包含不同的时间信息");
        
        // But: 解析出的信息应相同
        assertEquals(jwtUtil.getUserIdFromToken(token1), jwtUtil.getUserIdFromToken(token2));
        assertEquals(jwtUtil.getUsernameFromToken(token1), jwtUtil.getUsernameFromToken(token2));
    }
    
    @Test
    @DisplayName("测试不同用户生成不同Token")
    void testDifferentUsers_GenerateDifferentTokens() {
        // When
        String token1 = jwtUtil.generateToken(1L, "user1", "user");
        String token2 = jwtUtil.generateToken(2L, "user2", "user");
        
        // Then
        assertNotEquals(token1, token2, "不同用户应生成不同Token");
        assertNotEquals(jwtUtil.getUserIdFromToken(token1), jwtUtil.getUserIdFromToken(token2));
    }
    
    @Test
    @DisplayName("测试管理员Token")
    void testAdminToken_HandleCorrectly() {
        // Given
        String adminToken = jwtUtil.generateToken(1L, "admin", "admin");
        
        // When
        String role = jwtUtil.getRoleFromToken(adminToken);
        
        // Then
        assertEquals("admin", role, "应正确识别管理员角色");
    }
}


