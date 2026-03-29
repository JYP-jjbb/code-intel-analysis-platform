package com.mickey.onlineordering.onlineorderingserver.security;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.mickey.onlineordering.onlineorderingserver.util.IdGeneratorUtil;
import com.mickey.onlineordering.onlineorderingserver.vo.CaptchaVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务类
 * 用于生成和验证验证码
 */
@Slf4j
@Service
public class CaptchaService {
    
    /**
     * 验证码存储Map（生产环境建议使用Redis）
     * key: captchaId, value: {code: 验证码, timestamp: 生成时间}
     */
    private final Map<String, CaptchaData> captchaStore = new ConcurrentHashMap<>();

    private static final long CAPTCHA_EXPIRE_TIME = 5 * 60 * 1000;
    
    /**
     * 生成验证码
     */
    public CaptchaVo generateCaptcha() {
        // 生成验证码ID
        String captchaId = IdGeneratorUtil.generateCaptchaId();
        
        // 创建线性验证码，宽度200，高度80，4位字符，20条干扰线
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(200, 80, 4, 20);
        String code = lineCaptcha.getCode();
        String imageBase64 = lineCaptcha.getImageBase64();
        
        // 存储验证码
        captchaStore.put(captchaId, new CaptchaData(code.toLowerCase(), System.currentTimeMillis()));
        
        log.info("生成验证码：captchaId={}, code={}", captchaId, code);
        
        // 清理过期验证码
        cleanExpiredCaptcha();
        
        return new CaptchaVo(captchaId, "data:image/png;base64," + imageBase64);
    }
    
    /**
     * 验证验证码
     */
    public boolean verifyCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }
        
        CaptchaData captchaData = captchaStore.get(captchaId);
        if (captchaData == null) {
            log.warn("验证码不存在：captchaId={}", captchaId);
            return false;
        }
        
        // 检查是否过期
        if (System.currentTimeMillis() - captchaData.getTimestamp() > CAPTCHA_EXPIRE_TIME) {
            captchaStore.remove(captchaId);
            log.warn("验证码已过期：captchaId={}", captchaId);
            return false;
        }
        
        // 验证码不区分大小写
        boolean isValid = captchaData.getCode().equalsIgnoreCase(captchaCode);
        
        // 验证后立即删除（一次性验证码）
        captchaStore.remove(captchaId);
        
        log.info("验证码验证结果：captchaId={}, isValid={}", captchaId, isValid);
        
        return isValid;
    }
    
    /**
     * 清理过期验证码
     */
    private void cleanExpiredCaptcha() {
        long currentTime = System.currentTimeMillis();
        captchaStore.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getTimestamp() > CAPTCHA_EXPIRE_TIME
        );
    }
    
    /**
     * 验证码数据内部类
     */
    private static class CaptchaData {
        private final String code;
        private final long timestamp;
        
        public CaptchaData(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }
        
        public String getCode() {
            return code;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}












