package com.mickey.onlineordering.onlineorderingserver.listener;

import com.mickey.onlineordering.onlineorderingserver.service.DatabaseInitializerService;
import com.mickey.onlineordering.onlineorderingserver.service.FrontendStartupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 应用启动监听器
 * 在应用启动完成后执行数据库初始化和前端启动
 */
@Slf4j
@Component
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final DatabaseInitializerService databaseInitializerService;
    private final FrontendStartupService frontendStartupService;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Value("${frontend.port:3000}")
    private int frontendPort;

    public ApplicationStartupListener(
            DatabaseInitializerService databaseInitializerService,
            FrontendStartupService frontendStartupService) {
        this.databaseInitializerService = databaseInitializerService;
        this.frontendStartupService = frontendStartupService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║                                                                ║");
        log.info("║           在线订餐系统 - 正在执行启动初始化...                  ║");
        log.info("║                                                                ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
        log.info("");

        try {
            // 步骤1: 初始化数据库
            log.info("【步骤 1/2】数据库初始化检查");
            databaseInitializerService.initializeDatabase();
            log.info("");

            // 等待一秒，确保数据库初始化完成
            Thread.sleep(1000);

            // 步骤2: 启动前端
            log.info("【步骤 2/2】前端项目启动");
            frontendStartupService.startFrontend();
            log.info("");

            // 打印启动成功信息
            printStartupSuccessInfo();

        } catch (Exception e) {
            log.error("应用启动初始化失败", e);
            printStartupFailureInfo(e);
        }
    }

    /**
     * 打印启动成功信息
     */
    private void printStartupSuccessInfo() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            String contextPathDisplay = "/".equals(contextPath) ? "" : contextPath;

            log.info("");
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║                                                                ║");
            log.info("║              🎉 在线订餐系统启动成功！🎉                        ║");
            log.info("║                                                                ║");
            log.info("╠════════════════════════════════════════════════════════════════╣");
            log.info("║                                                                ║");
            log.info("║  【后端服务】                                                   ║");
            log.info("║    本地访问: http://localhost:{}{}                    ║", 
                     padRight(serverPort, 4), padRight(contextPathDisplay, 25));
            log.info("║    网络访问: http://{}:{}{}              ║", 
                     padRight(hostAddress, 15), padRight(serverPort, 4), padRight(contextPathDisplay, 13));
            log.info("║    API文档:  http://localhost:{}/swagger-ui.html           ║", 
                     padRight(serverPort, 4));
            log.info("║                                                                ║");
            log.info("║  【前端服务】                                                   ║");
            log.info("║    本地访问: http://localhost:{}                          ║", 
                     padRight(frontendPort, 4));
            log.info("║    网络访问: http://{}:{}                        ║", 
                     padRight(hostAddress, 15), padRight(frontendPort, 4));
            log.info("║                                                                ║");
            log.info("╠════════════════════════════════════════════════════════════════╣");
            log.info("║                                                                ║");
            log.info("║  提示:                                                          ║");
            log.info("║    1. 前端正在启动中，请稍等片刻后访问                          ║");
            log.info("║    2. 如需停止服务，请按 Ctrl+C                                ║");
            log.info("║    3. 前端和后端将一起关闭                                      ║");
            log.info("║                                                                ║");
            log.info("╚════════════════════════════════════════════════════════════════╝");
            log.info("");

        } catch (Exception e) {
            log.error("打印启动信息时出错", e);
        }
    }

    /**
     * 打印启动失败信息
     *
     * @param e 异常信息
     */
    private void printStartupFailureInfo(Exception e) {
        log.error("");
        log.error("╔════════════════════════════════════════════════════════════════╗");
        log.error("║                                                                ║");
        log.error("║              ❌ 系统启动失败！❌                                ║");
        log.error("║                                                                ║");
        log.error("╠════════════════════════════════════════════════════════════════╣");
        log.error("║                                                                ║");
        log.error("║  错误信息: {}                                    ║", 
                  padRight(e.getMessage(), 40));
        log.error("║                                                                ║");
        log.error("║  请检查:                                                        ║");
        log.error("║    1. 数据库服务是否已启动                                      ║");
        log.error("║    2. 数据库连接配置是否正确                                    ║");
        log.error("║    3. 前端目录是否存在                                          ║");
        log.error("║    4. Node.js 是否已安装                                       ║");
        log.error("║                                                                ║");
        log.error("╚════════════════════════════════════════════════════════════════╝");
        log.error("");
    }

    /**
     * 字符串右填充
     *
     * @param value 值
     * @param length 长度
     * @return 填充后的字符串
     */
    private String padRight(Object value, int length) {
        String str = String.valueOf(value);
        if (str.length() >= length) {
            return str.substring(0, length);
        }
        return str + " ".repeat(length - str.length());
    }
}

