package com.mickey.onlineordering.onlineorderingserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 前端启动服务
 * 负责自动启动 Vue3 前端项目
 */
@Slf4j
@Service
public class FrontendStartupService {

    @Value("${frontend.dir:online-ordering-client}")
    private String frontendDir;

    @Value("${frontend.port:3000}")
    private int frontendPort;

    @Value("${frontend.auto-start:true}")
    private boolean autoStart;

    private Process frontendProcess;

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("windows");

    /**
     * 启动前端项目
     */
    public void startFrontend() {
        if (!autoStart) {
            log.info("前端自动启动已禁用");
            return;
        }

        log.info("========================================");
        log.info("开始启动前端项目...");
        log.info("========================================");

        try {
            File frontendDirectory = new File(frontendDir);

            if (!frontendDirectory.exists() || !frontendDirectory.isDirectory()) {
                log.warn("前端目录不存在: {}", frontendDirectory.getAbsolutePath());
                log.warn("跳过前端启动");
                return;
            }

            // 检查前端端口是否已被占用
            if (isPortInUse(frontendPort)) {
                log.info("前端端口 {} 已被占用，前端可能已经启动", frontendPort);
                log.info("前端访问地址: http://localhost:{}", frontendPort);
                return;
            }

            // 检查 node_modules 是否存在
            File nodeModules = new File(frontendDirectory, "node_modules");
            if (!nodeModules.exists()) {
                log.warn("前端依赖未安装，正在安装依赖...");
                installDependencies(frontendDirectory);
            }

            // 启动前端
            startFrontendProcess(frontendDirectory);

            log.info("========================================");
            log.info("前端启动命令已执行");
            log.info("前端访问地址: http://localhost:{}", frontendPort);
            log.info("========================================");

        } catch (Exception e) {
            log.error("启动前端失败", e);
        }
    }

    /**
     * 安装前端依赖
     *
     * @param frontendDirectory 前端目录
     */
    private void installDependencies(File frontendDirectory) {
        try {
            log.info("正在安装前端依赖，这可能需要几分钟时间...");

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(frontendDirectory);

            if (IS_WINDOWS) {
                processBuilder.command("cmd.exe", "/c", "npm install");
            } else {
                processBuilder.command("sh", "-c", "npm install");
            }

            Process process = processBuilder.start();
            
            // 读取输出
            logProcessOutput(process, "NPM-INSTALL");

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroy();
                log.error("依赖安装超时");
                throw new RuntimeException("依赖安装超时");
            }

            if (process.exitValue() != 0) {
                log.error("依赖安装失败，退出码: {}", process.exitValue());
                throw new RuntimeException("依赖安装失败");
            }

            log.info("前端依赖安装成功");

        } catch (Exception e) {
            log.error("安装前端依赖失败", e);
            throw new RuntimeException("安装前端依赖失败", e);
        }
    }

    /**
     * 启动前端进程
     *
     * @param frontendDirectory 前端目录
     */
    private void startFrontendProcess(File frontendDirectory) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(frontendDirectory);

            if (IS_WINDOWS) {
                processBuilder.command("cmd.exe", "/c", "npm run dev");
            } else {
                processBuilder.command("sh", "-c", "npm run dev");
            }

            frontendProcess = processBuilder.start();

            // 在新线程中读取输出，避免阻塞
            new Thread(() -> logProcessOutput(frontendProcess, "FRONTEND"), "Frontend-Logger").start();

            log.info("前端进程已启动");

            // 添加关闭钩子，确保应用退出时前端进程也被关闭
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (frontendProcess != null && frontendProcess.isAlive()) {
                    log.info("正在关闭前端进程...");
                    frontendProcess.destroy();
                    try {
                        frontendProcess.waitFor(5, TimeUnit.SECONDS);
                        if (frontendProcess.isAlive()) {
                            frontendProcess.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }));

        } catch (Exception e) {
            log.error("启动前端进程失败", e);
            throw new RuntimeException("启动前端进程失败", e);
        }
    }

    /**
     * 记录进程输出
     *
     * @param process 进程
     * @param prefix  日志前缀
     */
    private void logProcessOutput(Process process, String prefix) {
        Charset charset = IS_WINDOWS ? Charset.forName("GBK") : Charset.defaultCharset();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[{}] {}", prefix, line);
            }
        } catch (Exception e) {
            log.debug("读取进程输出时出错: {}", e.getMessage());
        }
    }

    /**
     * 检查端口是否被占用
     *
     * @param port 端口号
     * @return true-占用，false-未占用
     */
    private boolean isPortInUse(int port) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            if (IS_WINDOWS) {
                processBuilder.command("cmd.exe", "/c", 
                    String.format("netstat -ano | findstr \":%d\"", port));
            } else {
                processBuilder.command("sh", "-c", 
                    String.format("lsof -i :%d", port));
            }

            Process process = processBuilder.start();
            
            List<String> output = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            process.waitFor(5, TimeUnit.SECONDS);

            return !output.isEmpty();

        } catch (Exception e) {
            log.debug("检查端口占用时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 停止前端进程
     */
    public void stopFrontend() {
        if (frontendProcess != null && frontendProcess.isAlive()) {
            log.info("正在停止前端进程...");
            frontendProcess.destroy();
            try {
                frontendProcess.waitFor(5, TimeUnit.SECONDS);
                if (frontendProcess.isAlive()) {
                    frontendProcess.destroyForcibly();
                }
                log.info("前端进程已停止");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("停止前端进程时被中断", e);
            }
        }
    }
}

