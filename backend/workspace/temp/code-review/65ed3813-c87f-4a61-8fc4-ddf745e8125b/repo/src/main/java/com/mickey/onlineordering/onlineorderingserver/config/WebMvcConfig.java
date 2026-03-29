package com.mickey.onlineordering.onlineorderingserver.config;

import com.mickey.onlineordering.onlineorderingserver.security.AdminInterceptor;
import com.mickey.onlineordering.onlineorderingserver.security.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 配置拦截器、跨域、静态资源等
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private LoginInterceptor loginInterceptor;
    
    @Autowired
    private AdminInterceptor adminInterceptor;
    
    /**
     * 配置拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器 - 先执行
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/captcha",
                        "/api/auth/email/code",  // 发送邮箱验证码（注册前使用，无需登录）
                        "/api/categories/**",
                        "/api/dishes/**",
                        // Payment endpoints (frontend needs create QR + poll status)
                        "/api/payment/create",
                        "/api/payment/status/**",
                        // Payment provider callbacks
                        "/api/pay/wechat/notify",
                        "/api/pay/alipay/notify",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**"
                )
                .order(1);
        
        // 管理员拦截器 - 后执行，拦截管理后台和菜品、分类的增删改操作
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/orders/admin/**")
                .addPathPatterns("/api/categories/**")
                .addPathPatterns("/api/dishes/**")
                .order(2);
    }
    
    /**
     * 配置跨域
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    
    /**
     * 配置静态资源
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/static/images/");
    }
}
