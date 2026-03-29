package com.mickey.onlineordering.onlineorderingserver.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证码Hash工具类测试
 * 测试验证码生成、Hash计算和验证的正确性
 *
 * @author Mickey
 * @date 2025-12-21
 */
@DisplayName("验证码Hash工具类测试")
class CodeHashUtilTest {
    
    @Test
    @DisplayName("测试生成验证码 - 默认6位")
    void testGenerateCode_WithDefaultLength_ReturnSixDigits() {
        // When
        String code = CodeHashUtil.generateCode();
        
        // Then
        assertNotNull(code, "验证码不应为null");
        assertEquals(6, code.length(), "验证码长度应为6位");
        assertTrue(code.matches("\\d{6}"), "验证码应全部为数字");
    }
    
    @Test
    @DisplayName("测试生成验证码 - 指定位数")
    void testGenerateCode_WithSpecifiedLength_ReturnCorrectDigits() {
        // Given
        int length = 4;
        
        // When
        String code = CodeHashUtil.generateCode(length);
        
        // Then
        assertEquals(length, code.length(), "验证码长度应为指定位数");
        assertTrue(code.matches("\\d{4}"), "验证码应全部为数字");
    }
    
    @Test
    @DisplayName("测试生成验证码 - 无效长度抛异常")
    void testGenerateCode_WithInvalidLength_ThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.generateCode(0);
        }, "长度为0应抛出异常");
        
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.generateCode(11);
        }, "长度超过10应抛出异常");
        
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.generateCode(-1);
        }, "负数长度应抛出异常");
    }
    
    @Test
    @DisplayName("测试生成盐值 - 不为空且长度合理")
    void testGenerateSalt_ReturnValidSalt() {
        // When
        String salt = CodeHashUtil.generateSalt();
        
        // Then
        assertNotNull(salt, "盐值不应为null");
        assertFalse(salt.isEmpty(), "盐值不应为空字符串");
        assertTrue(salt.length() > 10, "盐值长度应大于10");
    }
    
    @Test
    @DisplayName("测试生成盐值 - 每次生成应不同")
    void testGenerateSalt_GenerateTwice_ReturnDifferentValues() {
        // When
        String salt1 = CodeHashUtil.generateSalt();
        String salt2 = CodeHashUtil.generateSalt();
        
        // Then
        assertNotEquals(salt1, salt2, "每次生成的盐值应不相同");
    }
    
    @Test
    @DisplayName("测试计算Hash - 正常输入")
    void testHashCode_WithValidInput_ReturnHash() {
        // Given
        String code = "123456";
        String salt = "randomSalt123";
        
        // When
        String hash = CodeHashUtil.hashCode(code, salt);
        
        // Then
        assertNotNull(hash, "Hash值不应为null");
        assertEquals(64, hash.length(), "SHA-256的Hash值应为64位十六进制");
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash值应为小写十六进制");
    }
    
    @Test
    @DisplayName("测试计算Hash - 相同输入产生相同Hash")
    void testHashCode_WithSameInput_ReturnSameHash() {
        // Given
        String code = "123456";
        String salt = "randomSalt123";
        
        // When
        String hash1 = CodeHashUtil.hashCode(code, salt);
        String hash2 = CodeHashUtil.hashCode(code, salt);
        
        // Then
        assertEquals(hash1, hash2, "相同的输入应产生相同的Hash");
    }
    
    @Test
    @DisplayName("测试计算Hash - 不同验证码产生不同Hash")
    void testHashCode_WithDifferentCode_ReturnDifferentHash() {
        // Given
        String salt = "randomSalt123";
        
        // When
        String hash1 = CodeHashUtil.hashCode("123456", salt);
        String hash2 = CodeHashUtil.hashCode("654321", salt);
        
        // Then
        assertNotEquals(hash1, hash2, "不同的验证码应产生不同的Hash");
    }
    
    @Test
    @DisplayName("测试计算Hash - 不同盐值产生不同Hash")
    void testHashCode_WithDifferentSalt_ReturnDifferentHash() {
        // Given
        String code = "123456";
        
        // When
        String hash1 = CodeHashUtil.hashCode(code, "salt1");
        String hash2 = CodeHashUtil.hashCode(code, "salt2");
        
        // Then
        assertNotEquals(hash1, hash2, "不同的盐值应产生不同的Hash");
    }
    
    @Test
    @DisplayName("测试计算Hash - 验证码为空抛异常")
    void testHashCode_WithEmptyCode_ThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.hashCode("", "salt");
        }, "验证码为空应抛出异常");
        
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.hashCode(null, "salt");
        }, "验证码为null应抛出异常");
    }
    
    @Test
    @DisplayName("测试计算Hash - 盐值为空抛异常")
    void testHashCode_WithEmptySalt_ThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.hashCode("123456", "");
        }, "盐值为空应抛出异常");
        
        assertThrows(IllegalArgumentException.class, () -> {
            CodeHashUtil.hashCode("123456", null);
        }, "盐值为null应抛出异常");
    }
    
    @Test
    @DisplayName("测试验证码校验 - 正确的验证码返回true")
    void testVerifyCode_WithCorrectCode_ReturnTrue() {
        // Given
        String code = "123456";
        String salt = CodeHashUtil.generateSalt();
        String hash = CodeHashUtil.hashCode(code, salt);
        
        // When
        boolean result = CodeHashUtil.verifyCode(code, salt, hash);
        
        // Then
        assertTrue(result, "正确的验证码应验证通过");
    }
    
    @Test
    @DisplayName("测试验证码校验 - 错误的验证码返回false")
    void testVerifyCode_WithWrongCode_ReturnFalse() {
        // Given
        String correctCode = "123456";
        String wrongCode = "654321";
        String salt = CodeHashUtil.generateSalt();
        String hash = CodeHashUtil.hashCode(correctCode, salt);
        
        // When
        boolean result = CodeHashUtil.verifyCode(wrongCode, salt, hash);
        
        // Then
        assertFalse(result, "错误的验证码应验证失败");
    }
    
    @Test
    @DisplayName("测试验证码校验 - null参数返回false")
    void testVerifyCode_WithNullParameter_ReturnFalse() {
        // Given
        String code = "123456";
        String salt = "salt";
        String hash = "hash";
        
        // When & Then
        assertFalse(CodeHashUtil.verifyCode(null, salt, hash), "验证码为null应返回false");
        assertFalse(CodeHashUtil.verifyCode(code, null, hash), "盐值为null应返回false");
        assertFalse(CodeHashUtil.verifyCode(code, salt, null), "Hash为null应返回false");
    }
    
    @Test
    @DisplayName("测试完整流程 - 生成、Hash、验证")
    void testFullWorkflow_GenerateHashVerify_Success() {
        // Given: 生成验证码和盐值
        String code = CodeHashUtil.generateCode();
        String salt = CodeHashUtil.generateSalt();
        
        // When: 计算Hash
        String hash = CodeHashUtil.hashCode(code, salt);
        
        // Then: 验证应成功
        boolean result = CodeHashUtil.verifyCode(code, salt, hash);
        assertTrue(result, "完整流程应验证成功");
        
        // And: 使用错误的验证码应失败
        boolean wrongResult = CodeHashUtil.verifyCode("000000", salt, hash);
        assertFalse(wrongResult, "错误的验证码应验证失败");
    }
}

