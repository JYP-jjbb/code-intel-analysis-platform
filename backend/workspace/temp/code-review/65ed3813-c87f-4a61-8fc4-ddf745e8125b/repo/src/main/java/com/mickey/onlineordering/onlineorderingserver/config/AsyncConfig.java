package com.mickey.onlineordering.onlineorderingserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 * 配置线程池用于异步发送邮件
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * 邮件发送线程池
     * 用于异步发送邮件，提升接口响应速度
     */
    @Bean("emailTaskExecutor")
    public Executor emailTaskExecutor() {
        log.info("初始化邮件发送线程池...");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：建议设置为CPU核心数
        executor.setCorePoolSize(4);
        
        // 最大线程数：核心线程数的2倍
        executor.setMaxPoolSize(8);
        
        // 队列容量：当核心线程都在工作时，新任务会放入队列
        executor.setQueueCapacity(100);
        
        // 线程名称前缀，便于排查问题
        executor.setThreadNamePrefix("email-task-");
        
        // 线程空闲时间：超过核心线程数的线程在空闲指定时间后会被销毁（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：当队列满了且线程数达到最大值时，由调用线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）：设置线程池中任务的等待时间，超时后强制关闭
        executor.setAwaitTerminationSeconds(60);
        
        // 初始化线程池
        executor.initialize();
        
        log.info("邮件发送线程池初始化完成");
        
        return executor;
    }
}




