package com.mickey.onlineordering.onlineorderingserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 登录请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String captcha;

    private String captchaId;
}












