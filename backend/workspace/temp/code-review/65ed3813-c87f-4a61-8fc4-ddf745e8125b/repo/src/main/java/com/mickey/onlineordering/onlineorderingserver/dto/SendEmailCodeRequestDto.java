package com.mickey.onlineordering.onlineorderingserver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailCodeRequestDto implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "场景类型不能为空")
    private String scene;

    @NotBlank(message = "图形验证码不能为空")
    private String captcha;

    @NotBlank(message = "图形验证码ID不能为空")
    private String captchaId;
}




