package com.mickey.onlineordering.onlineorderingserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 静态资源配置
 * 配置图片访问路径
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取项目根目录的绝对路径
        String projectPath = System.getProperty("user.dir");
        String uploadPath = "file:" + projectPath + File.separator + "uploads" + File.separator;
        
        System.out.println("配置静态资源路径: " + uploadPath);
        
        // 配置上传图片的访问路径
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }
}

