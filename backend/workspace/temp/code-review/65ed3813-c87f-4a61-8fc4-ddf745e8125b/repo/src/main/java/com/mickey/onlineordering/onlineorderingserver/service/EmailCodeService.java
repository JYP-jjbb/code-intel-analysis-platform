package com.mickey.onlineordering.onlineorderingserver.service;

import com.alibaba.fastjson2.JSON;
import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.util.CodeHashUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务类
 * 负责邮箱验证码的生成、存储、验证、限流等核心业务逻辑
 * 
 * Redis Key设计规范：
 * 1. auth:email:code:{email}                    - 验证码Hash和盐值（TTL: 5分钟）
 * 2. auth:email:send:limit:{email}              - 邮箱60秒内发送限制（TTL: 60秒）
 * 3. auth:email:send:count:{email}:{yyyyMMdd}   - 邮箱当天发送次数（TTL: 当天剩余秒数）
 * 4. auth:email:verify:fail:{email}             - 验证失败次数（TTL: 10分钟）
 * 5. auth:email:lock:{email}                    - 邮箱锁定标记（TTL: 10分钟）
 * 6. auth:ip:send:count:{ip}:{minute}           - IP每分钟发送次数（TTL: 60秒）
 */
@Slf4j
@Service
public class EmailCodeService {
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    // ==================== Redis Key 前缀常量 ====================
    private static final String CODE_KEY_PREFIX = "auth:email:code:";
    private static final String SEND_LIMIT_KEY_PREFIX = "auth:email:send:limit:";
    private static final String DAILY_COUNT_KEY_PREFIX = "auth:email:send:count:";
    private static final String FAIL_COUNT_KEY_PREFIX = "auth:email:verify:fail:";
    private static final String LOCK_KEY_PREFIX = "auth:email:lock:";
    private static final String IP_COUNT_KEY_PREFIX = "auth:ip:send:count:";
    
    // ==================== 配置常量 ====================
    

    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final int DAILY_MAX_SEND_COUNT = 10;
    private static final int IP_MAX_PER_MINUTE = 20;
    private static final int MAX_VERIFY_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MINUTES = 10;
    private static final int CODE_LENGTH = 6;
    
    // ==================== 核心业务方法 ====================
    
    /**
     * 生成并存储邮箱验证码
     * 包含完整的限流检查逻辑
     *
     * @param email 邮箱地址
     * @param ip    请求IP地址
     * @param scene 场景类型（register/reset_password/change_email）
     * @return 生成的验证码明文（用于发送邮件）
     */
    public String generateAndStoreCode(String email, String ip, String scene) {
        log.info("开始生成邮箱验证码: email={}, ip={}, scene={}", email, ip, scene);
        
        // 1. 检查邮箱是否被锁定
        checkEmailLock(email);
        
        // 2. 检查IP限流
        checkIpRateLimit(ip);
        
        // 3. 检查邮箱60秒发送间隔
        checkEmailSendInterval(email);
        
        // 4. 检查邮箱每日发送次数
        checkEmailDailyLimit(email);
        
        // 5. 生成验证码
        String code = CodeHashUtil.generateCode(CODE_LENGTH);
        
        // 6. 生成盐值
        String salt = CodeHashUtil.generateSalt();
        
        // 7. 计算Hash值
        String hash = CodeHashUtil.hashCode(code, salt);
        
        // 8. 构建存储对象
        CodeData codeData = new CodeData(hash, salt, scene, System.currentTimeMillis());
        String codeDataJson = JSON.toJSONString(codeData);
        
        // 9. 存储到Redis
        String codeKey = CODE_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(codeKey, codeDataJson, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        // 10. 记录发送限制（60秒）
        String sendLimitKey = SEND_LIMIT_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(sendLimitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // 11. 增加每日发送次数
        incrementDailySendCount(email);
        
        // 12. 记录IP发送次数
        incrementIpSendCount(ip);
        
        log.info("邮箱验证码生成成功: email={}", email);
        
        return code;
    }
    
    /**
     * 验证邮箱验证码
     * 包含失败次数统计和锁定逻辑
     *
     * @param email     邮箱地址
     * @param inputCode 用户输入的验证码
     * @param scene     场景类型
     */
    public void verifyCode(String email, String inputCode, String scene) {
        log.info("开始验证邮箱验证码: email={}, scene={}", email, scene);
        
        // 1. 检查邮箱是否被锁定
        checkEmailLock(email);
        
        // 2. 从Redis获取验证码数据
        String codeKey = CODE_KEY_PREFIX + email;
        String codeDataJson = stringRedisTemplate.opsForValue().get(codeKey);
        
        if (codeDataJson == null) {
            log.warn("验证码不存在或已过期: email={}", email);
            throw new BizException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        
        // 3. 解析验证码数据
        CodeData codeData = JSON.parseObject(codeDataJson, CodeData.class);
        
        // 4. 验证场景是否匹配
        if (!scene.equals(codeData.getScene())) {
            log.warn("验证码场景不匹配: email={}, expected={}, actual={}", email, codeData.getScene(), scene);
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID);
        }
        
        // 5. 验证码校验
        boolean isValid = CodeHashUtil.verifyCode(inputCode, codeData.getSalt(), codeData.getHash());
        
        if (isValid) {
            // 验证成功：删除验证码和失败计数
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(FAIL_COUNT_KEY_PREFIX + email);
            log.info("邮箱验证码验证成功: email={}", email);
        } else {
            // 验证失败：增加失败次数
            int failCount = incrementFailCount(email);
            log.warn("邮箱验证码验证失败: email={}, failCount={}", email, failCount);
            
            // 达到最大失败次数，锁定邮箱
            if (failCount >= MAX_VERIFY_FAIL_COUNT) {
                lockEmail(email);
                // 删除验证码
                stringRedisTemplate.delete(codeKey);
                log.warn("邮箱因验证失败次数过多被锁定: email={}", email);
                throw new BizException(ErrorCode.EMAIL_CODE_TRY_LIMIT);
            }
            
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID, 
                String.format("验证码错误，还剩%d次机会", MAX_VERIFY_FAIL_COUNT - failCount));
        }
    }
    
    // ==================== 限流检查方法 ====================
    
    /**
     * 检查邮箱是否被锁定
     */
    private void checkEmailLock(String email) {
        String lockKey = LOCK_KEY_PREFIX + email;
        Boolean isLocked = stringRedisTemplate.hasKey(lockKey);
        
        if (Boolean.TRUE.equals(isLocked)) {
            Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.MINUTES);
            log.warn("邮箱已被锁定: email={}, 剩余时间={}分钟", email, ttl);
            throw new BizException(ErrorCode.EMAIL_LOCKED, 
                String.format("该邮箱因多次验证失败已被锁定，请%d分钟后再试", ttl));
        }
    }
    
    /**
     * 检查IP限流
     */
    private void checkIpRateLimit(String ip) {
        long currentMinute = System.currentTimeMillis() / 60000;
        String ipCountKey = IP_COUNT_KEY_PREFIX + ip + ":" + currentMinute;
        
        String countStr = stringRedisTemplate.opsForValue().get(ipCountKey);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        
        if (count >= IP_MAX_PER_MINUTE) {
            log.warn("IP请求过于频繁: ip={}, count={}", ip, count);
            throw new BizException(ErrorCode.IP_SEND_TOO_FREQUENT);
        }
    }
    
    /**
     * 检查邮箱60秒发送间隔
     */
    private void checkEmailSendInterval(String email) {
        String sendLimitKey = SEND_LIMIT_KEY_PREFIX + email;
        Boolean exists = stringRedisTemplate.hasKey(sendLimitKey);
        
        if (Boolean.TRUE.equals(exists)) {
            Long ttl = stringRedisTemplate.getExpire(sendLimitKey, TimeUnit.SECONDS);
            log.warn("邮箱发送过于频繁: email={}, 剩余时间={}秒", email, ttl);
            throw new BizException(ErrorCode.EMAIL_CODE_TOO_FREQUENT, 
                String.format("验证码发送过于频繁，请%d秒后再试", ttl));
        }
    }
    
    /**
     * 检查邮箱每日发送次数
     */
    private void checkEmailDailyLimit(String email) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dailyCountKey = DAILY_COUNT_KEY_PREFIX + email + ":" + today;
        
        String countStr = stringRedisTemplate.opsForValue().get(dailyCountKey);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        
        if (count >= DAILY_MAX_SEND_COUNT) {
            log.warn("邮箱今日发送次数已达上限: email={}, count={}", email, count);
            throw new BizException(ErrorCode.EMAIL_CODE_DAILY_LIMIT);
        }
    }
    
    // ==================== 计数器方法 ====================
    
    /**
     * 增加每日发送次数
     */
    private void incrementDailySendCount(String email) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dailyCountKey = DAILY_COUNT_KEY_PREFIX + email + ":" + today;
        
        Long count = stringRedisTemplate.opsForValue().increment(dailyCountKey);
        
        // 如果是第一次，设置过期时间为当天结束
        if (count != null && count == 1) {
            long secondsUntilEndOfDay = getSecondsUntilEndOfDay();
            stringRedisTemplate.expire(dailyCountKey, secondsUntilEndOfDay, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 增加IP发送次数
     */
    private void incrementIpSendCount(String ip) {
        long currentMinute = System.currentTimeMillis() / 60000;
        String ipCountKey = IP_COUNT_KEY_PREFIX + ip + ":" + currentMinute;
        
        Long count = stringRedisTemplate.opsForValue().increment(ipCountKey);
        
        // 设置60秒过期
        if (count != null && count == 1) {
            stringRedisTemplate.expire(ipCountKey, 60, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 增加验证失败次数
     *
     * @return 当前失败次数
     */
    private int incrementFailCount(String email) {
        String failCountKey = FAIL_COUNT_KEY_PREFIX + email;
        Long count = stringRedisTemplate.opsForValue().increment(failCountKey);
        
        // 设置10分钟过期
        if (count != null && count == 1) {
            stringRedisTemplate.expire(failCountKey, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
        
        return count == null ? 0 : count.intValue();
    }
    
    /**
     * 锁定邮箱
     */
    private void lockEmail(String email) {
        String lockKey = LOCK_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取距离当天结束的秒数
     */
    private long getSecondsUntilEndOfDay() {
        long now = System.currentTimeMillis();
        long endOfDay = (now / 86400000 + 1) * 86400000 - 1; // 当天23:59:59
        return (endOfDay - now) / 1000;
    }
    
    // ==================== 内部类：验证码数据结构 ====================
    
    /**
     * 验证码数据结构
     * 存储在Redis中的JSON格式数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeData {
        /**
         * 验证码Hash值
         */
        private String hash;
        
        /**
         * 盐值
         */
        private String salt;
        
        /**
         * 场景类型
         */
        private String scene;
        
        /**
         * 创建时间戳
         */
        private Long createTime;
    }
}




