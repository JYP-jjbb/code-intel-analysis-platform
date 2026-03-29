package com.mickey.onlineordering.onlineorderingserver.service.impl;

import com.mickey.onlineordering.onlineorderingserver.common.Constants;
import com.mickey.onlineordering.onlineorderingserver.common.ErrorCode;
import com.mickey.onlineordering.onlineorderingserver.dto.LoginRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.RegisterRequestDto;
import com.mickey.onlineordering.onlineorderingserver.dto.UserUpdateDto;
import com.mickey.onlineordering.onlineorderingserver.entity.User;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.UserMapper;
import com.mickey.onlineordering.onlineorderingserver.security.CaptchaService;
import com.mickey.onlineordering.onlineorderingserver.service.EmailCodeService;
import com.mickey.onlineordering.onlineorderingserver.service.UserService;
import com.mickey.onlineordering.onlineorderingserver.util.BeanCopyUtil;
import com.mickey.onlineordering.onlineorderingserver.util.JwtUtil;
import com.mickey.onlineordering.onlineorderingserver.util.PasswordEncoderUtil;
import com.mickey.onlineordering.onlineorderingserver.vo.LoginVo;
import com.mickey.onlineordering.onlineorderingserver.vo.UserProfileVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PasswordEncoderUtil passwordEncoderUtil;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private CaptchaService captchaService;
    
    @Autowired
    private EmailCodeService emailCodeService;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequestDto dto) {
        // 1. 验证两次密码是否一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "两次密码输入不一致");
        }
        
        // 2. 验证图形验证码
        if (!captchaService.verifyCaptcha(dto.getCaptchaId(), dto.getCaptcha())) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR);
        }
        
        // 3. 验证邮箱验证码（核心新增逻辑）
        emailCodeService.verifyCode(dto.getEmail(), dto.getEmailCode(), "register");
        
        // 4. 检查用户名是否已存在
        if (userMapper.countByUsername(dto.getUsername()) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        
        // 5. 检查邮箱是否已存在
        if (userMapper.countByEmail(dto.getEmail()) > 0) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }
        
        // 6. 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoderUtil.encode(dto.getPassword()));
        user.setRole(Constants.ROLE_USER);
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(Constants.DEFAULT_AVATAR);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        
        int result = userMapper.insert(user);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "注册失败");
        }
        
        log.info("用户注册成功：username={}, email={}", dto.getUsername(), dto.getEmail());
    }
    
    @Override
    public LoginVo login(LoginRequestDto dto) {
        // 验证验证码
        if (!captchaService.verifyCaptcha(dto.getCaptchaId(), dto.getCaptcha())) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR);
        }
        
        // 查询用户
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_ERROR);
        }
        
        // 验证密码
        if (!passwordEncoderUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_ERROR);
        }
        
        // 检查账号状态
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "账号已被禁用");
        }
        
        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        
        // 返回登录结果
        LoginVo loginVo = new LoginVo();
        loginVo.setToken(token);
        loginVo.setUser(BeanCopyUtil.copyBean(user, UserProfileVo.class));
        
        log.info("用户登录成功：userId={}, username={}", user.getId(), user.getUsername());
        
        return loginVo;
    }
    
    @Override
    public UserProfileVo getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return BeanCopyUtil.copyBean(user, UserProfileVo.class);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, UserUpdateDto dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        
        user.setPhone(dto.getPhone());
        user.setAddress(dto.getAddress());
        user.setAvatar(dto.getAvatar());
        user.setEmail(dto.getEmail());
        user.setUpdateTime(LocalDateTime.now());
        
        int result = userMapper.update(user);
        if (result <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        
        log.info("用户信息更新成功：userId={}", userId);
    }
}









