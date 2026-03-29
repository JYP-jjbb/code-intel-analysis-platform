package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 邮箱验证码服务测试类
 * 测试验证码生成、验证、限流等核心业务逻辑
 */
@SpringBootTest
@DisplayName("邮箱验证码服务测试")
class EmailCodeServiceTest {
    
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    @InjectMocks
    private EmailCodeService emailCodeService;
    
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_IP = "192.168.1.1";
    private static final String TEST_SCENE = "register";
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Test
    @DisplayName("测试生成验证码 - 正常流程")
    void testGenerateAndStoreCode_WithValidInput_Success() {
        // Given: Mock Redis操作，所有检查都通过
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // When: 生成验证码
        String code = emailCodeService.generateAndStoreCode(TEST_EMAIL, TEST_IP, TEST_SCENE);
        
        // Then: 验证结果
        assertNotNull(code, "验证码不应为null");
        assertEquals(6, code.length(), "验证码应为6位");
        assertTrue(code.matches("\\d{6}"), "验证码应全部为数字");
        
        // And: 验证Redis操作被调用
        verify(valueOperations, times(2)).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(valueOperations, atLeastOnce()).increment(anyString());
    }
    
    @Test
    @DisplayName("测试生成验证码 - 邮箱被锁定")
    void testGenerateAndStoreCode_WithLockedEmail_ThrowException() {
        // Given: 邮箱已被锁定
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(true);
        when(stringRedisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(5L);
        
        // When & Then: 应抛出邮箱锁定异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.generateAndStoreCode(TEST_EMAIL, TEST_IP, TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_LOCKED.getCode(), exception.getCode(), "应返回邮箱锁定错误码");
    }
    
    @Test
    @DisplayName("测试生成验证码 - IP请求过于频繁")
    void testGenerateAndStoreCode_WithFrequentIp_ThrowException() {
        // Given: IP请求次数已达上限
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(false);
        when(valueOperations.get(contains("ip:send:count"))).thenReturn("20");
        
        // When & Then: 应抛出IP限流异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.generateAndStoreCode(TEST_EMAIL, TEST_IP, TEST_SCENE);
        });
        
        assertEquals(ErrorCode.IP_SEND_TOO_FREQUENT.getCode(), exception.getCode(), "应返回IP限流错误码");
    }
    
    @Test
    @DisplayName("测试生成验证码 - 邮箱60秒内已发送")
    void testGenerateAndStoreCode_WithinSixtySeconds_ThrowException() {
        // Given: 60秒内已发送
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(false);
        when(valueOperations.get(contains("ip:send:count"))).thenReturn("0");
        when(stringRedisTemplate.hasKey(contains("send:limit"))).thenReturn(true);
        when(stringRedisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(30L);
        
        // When & Then: 应抛出发送频繁异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.generateAndStoreCode(TEST_EMAIL, TEST_IP, TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_CODE_TOO_FREQUENT.getCode(), exception.getCode(), "应返回发送频繁错误码");
    }
    
    @Test
    @DisplayName("测试生成验证码 - 邮箱今日发送次数达上限")
    void testGenerateAndStoreCode_ExceedDailyLimit_ThrowException() {
        // Given: 今日发送次数已达10次
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(false);
        when(stringRedisTemplate.hasKey(contains("send:limit"))).thenReturn(false);
        when(valueOperations.get(contains("ip:send:count"))).thenReturn("0");
        when(valueOperations.get(contains("send:count"))).thenReturn("10");
        
        // When & Then: 应抛出每日限额异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.generateAndStoreCode(TEST_EMAIL, TEST_IP, TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_CODE_DAILY_LIMIT.getCode(), exception.getCode(), "应返回每日限额错误码");
    }
    
    @Test
    @DisplayName("测试验证验证码 - 验证码不存在或已过期")
    void testVerifyCode_WithExpiredCode_ThrowException() {
        // Given: Redis中没有验证码
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(false);
        when(valueOperations.get(contains("email:code"))).thenReturn(null);
        
        // When & Then: 应抛出验证码过期异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.verifyCode(TEST_EMAIL, "123456", TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_CODE_EXPIRED.getCode(), exception.getCode(), "应返回验证码过期错误码");
    }
    
    @Test
    @DisplayName("测试验证验证码 - 场景不匹配")
    void testVerifyCode_WithDifferentScene_ThrowException() {
        // Given: 场景不匹配
        String codeData = "{\"hash\":\"testhash\",\"salt\":\"testsalt\",\"scene\":\"reset_password\",\"createTime\":1703164800000}";
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(false);
        when(valueOperations.get(contains("email:code"))).thenReturn(codeData);
        
        // When & Then: 应抛出验证码错误异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.verifyCode(TEST_EMAIL, "123456", TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_CODE_INVALID.getCode(), exception.getCode(), "应返回验证码错误错误码");
    }
    
    @Test
    @DisplayName("测试验证验证码 - 邮箱被锁定时不能验证")
    void testVerifyCode_WithLockedEmail_ThrowException() {
        // Given: 邮箱已被锁定
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(true);
        when(stringRedisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(5L);
        
        // When & Then: 应抛出邮箱锁定异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.verifyCode(TEST_EMAIL, "123456", TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_LOCKED.getCode(), exception.getCode(), "应返回邮箱锁定错误码");
    }
    
    @Test
    @DisplayName("测试限流 - 验证失败次数限制")
    void testVerifyCode_ExceedFailLimit_LockEmail() {
        // Given: 已失败4次，这是第5次失败
        String codeData = "{\"hash\":\"wronghash\",\"salt\":\"testsalt\",\"scene\":\"register\",\"createTime\":1703164800000}";
        when(stringRedisTemplate.hasKey(contains("lock"))).thenReturn(false);
        when(valueOperations.get(contains("email:code"))).thenReturn(codeData);
        when(valueOperations.increment(contains("verify:fail"))).thenReturn(5L);
        
        // When & Then: 应抛出验证次数超限异常
        BizException exception = assertThrows(BizException.class, () -> {
            emailCodeService.verifyCode(TEST_EMAIL, "123456", TEST_SCENE);
        });
        
        assertEquals(ErrorCode.EMAIL_CODE_TRY_LIMIT.getCode(), exception.getCode(), "应返回验证次数超限错误码");
        
        // And: 应锁定邮箱
        verify(valueOperations).set(contains("lock"), eq("1"), eq(10L), eq(TimeUnit.MINUTES));
        
        // And: 应删除验证码
        verify(stringRedisTemplate).delete(contains("email:code"));
    }
    
    @Test
    @DisplayName("测试并发安全 - 多个请求同时生成验证码")
    void testConcurrentGenerate_HandleCorrectly() {
        // Given: 正常的Redis操作
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        
        // When: 模拟并发请求
        String code1 = emailCodeService.generateAndStoreCode(TEST_EMAIL, TEST_IP, TEST_SCENE);
        String code2 = emailCodeService.generateAndStoreCode("test2@example.com", TEST_IP, TEST_SCENE);
        
        // Then: 两次生成的验证码应不同
        assertNotEquals(code1, code2, "并发生成的验证码应不同");
    }
    
    @Test
    @DisplayName("测试边界情况 - 邮箱格式")
    void testWithVariousEmailFormats_HandleCorrectly() {
        // Given
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // When & Then: 测试各种邮箱格式
        assertDoesNotThrow(() -> {
            emailCodeService.generateAndStoreCode("test@example.com", TEST_IP, TEST_SCENE);
            emailCodeService.generateAndStoreCode("test.user@example.com", TEST_IP, TEST_SCENE);
            emailCodeService.generateAndStoreCode("test+tag@example.co.uk", TEST_IP, TEST_SCENE);
        }, "各种合法邮箱格式都应能正常处理");
    }
}

