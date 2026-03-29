package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件服务类
 * 负责异步发送邮件
 * 使用@Async注解实现异步发送，提升接口响应速度
 */
@Slf4j
@Service
public class EmailService {
    
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.application.name:在线订餐系统}")
    private String systemName;
    
    /**
     * 异步发送验证码邮件
     * 使用@Async注解，方法将在独立的线程池中执行
     *
     * @param toEmail 收件人邮箱
     * @param code    验证码
     * @param scene   场景类型（register/reset_password/change_email）
     */
    @Async("emailTaskExecutor")
    public void sendVerificationCode(String toEmail, String code, String scene) {
        log.info("开始异步发送验证码邮件: toEmail={}, scene={}", toEmail, scene);
        
        try {
            // 根据场景类型确定邮件标题和内容
            String subject = getEmailSubject(scene);
            String content = buildEmailContent(code, scene);
            
            // 发送邮件（带重试机制）
            sendEmailWithRetry(toEmail, subject, content, 3);
            
            log.info("验证码邮件发送成功: toEmail={}, scene={}", toEmail, scene);
            
        } catch (Exception e) {
            log.error("验证码邮件发送失败: toEmail={}, scene={}", toEmail, scene, e);
            // 异步方法中的异常不会抛出给调用方，这里只记录日志
            // 如果需要通知用户，可以通过MQ、WebSocket等方式
        }
    }
    
    /**
     * 发送邮件（带重试机制）
     *
     * @param toEmail      收件人
     * @param subject      主题
     * @param content      内容（HTML格式）
     * @param maxRetries   最大重试次数
     */
    private void sendEmailWithRetry(String toEmail, String subject, String content, int maxRetries) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                sendEmail(toEmail, subject, content);
                return; // 发送成功，直接返回
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("邮件发送失败，正在重试: toEmail={}, 重试次数={}/{}", toEmail, retryCount, maxRetries);
                
                // 重试前等待一段时间（指数退避策略）
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000L * retryCount); // 1s, 2s, 3s...
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // 所有重试都失败
        log.error("邮件发送失败，已达最大重试次数: toEmail={}, maxRetries={}", toEmail, maxRetries, lastException);
        throw new BizException(ErrorCode.EMAIL_SEND_FAILED);
    }
    
    /**
     * 发送邮件（底层方法）
     *
     * @param toEmail 收件人
     * @param subject 主题
     * @param content 内容（HTML格式）
     */
    private void sendEmail(String toEmail, String subject, String content) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(content, true); // true表示支持HTML
        
        mailSender.send(message);
    }
    
    /**
     * 根据场景获取邮件主题
     */
    private String getEmailSubject(String scene) {
        return switch (scene) {
            case "register" -> String.format("【%s】注册验证码", systemName);
            case "reset_password" -> String.format("【%s】重置密码验证码", systemName);
            case "change_email" -> String.format("【%s】修改邮箱验证码", systemName);
            default -> String.format("【%s】验证码", systemName);
        };
    }
    
    /**
     * 构建邮件内容（HTML格式）
     */
    private String buildEmailContent(String code, String scene) {
        String sceneName = getSceneName(scene);
        
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: 'Microsoft YaHei', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .container {
                        background: #f9f9f9;
                        border-radius: 10px;
                        padding: 30px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        color: #4CAF50;
                        margin: 0;
                    }
                    .code-box {
                        background: #fff;
                        border: 2px dashed #4CAF50;
                        border-radius: 8px;
                        padding: 20px;
                        text-align: center;
                        margin: 20px 0;
                    }
                    .code {
                        font-size: 32px;
                        font-weight: bold;
                        color: #4CAF50;
                        letter-spacing: 5px;
                        font-family: 'Courier New', monospace;
                    }
                    .tips {
                        background: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .tips ul {
                        margin: 10px 0;
                        padding-left: 20px;
                    }
                    .tips li {
                        margin: 5px 0;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 30px;
                        color: #999;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>%s</h1>
                        <p>您正在进行%s操作</p>
                    </div>
                    
                    <div class="code-box">
                        <p style="margin: 0 0 10px 0; color: #666;">您的验证码为：</p>
                        <div class="code">%s</div>
                        <p style="margin: 10px 0 0 0; color: #999; font-size: 14px;">验证码5分钟内有效</p>
                    </div>
                    
                    <div class="tips">
                        <strong>⚠️ 安全提示：</strong>
                        <ul>
                            <li>此验证码仅用于%s，请勿泄露给他人</li>
                            <li>如非本人操作，请忽略此邮件</li>
                            <li>验证码5分钟内有效，过期后请重新获取</li>
                            <li>为了您的账号安全，请不要将验证码告诉任何人</li>
                        </ul>
                    </div>
                    
                    <div class="footer">
                        <p>此邮件由系统自动发送，请勿直接回复</p>
                        <p>&copy; %s - 在线订餐系统</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(systemName, sceneName, code, sceneName, systemName);
    }
    
    /**
     * 获取场景中文名称
     */
    private String getSceneName(String scene) {
        return switch (scene) {
            case "register" -> "用户注册";
            case "reset_password" -> "重置密码";
            case "change_email" -> "修改邮箱";
            default -> "身份验证";
        };
    }
}



