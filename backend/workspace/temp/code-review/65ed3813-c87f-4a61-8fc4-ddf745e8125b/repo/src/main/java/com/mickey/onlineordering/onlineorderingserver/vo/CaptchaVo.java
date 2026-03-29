package com.mickey.onlineordering.onlineorderingserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 验证码VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private String captchaId;
    private String captchaImage;
}











