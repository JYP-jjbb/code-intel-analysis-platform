package com.course.ideology.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class NuteraReportDatabaseStartupCheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(NuteraReportDatabaseStartupCheck.class);

    private final boolean enabled;
    private final boolean strict;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public NuteraReportDatabaseStartupCheck(
            @Value("${app.nutera.report-db.enabled:true}") boolean enabled,
            @Value("${app.nutera.report-db.startup-check.strict:true}") boolean strict,
            @Value("${app.nutera.report-db.url:jdbc:sqlserver://localhost:1433;databaseName=code_intel_analysis;encrypt=false;trustServerCertificate=true}") String jdbcUrl,
            @Value("${app.nutera.report-db.username:test}") String username,
            @Value("${app.nutera.report-db.password:123456}") String password) {
        this.enabled = enabled;
        this.strict = strict;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[Nutera-DB] report-db.enabled=false, startup check skipped.");
            return;
        }

        String configuredDbName = extractDatabaseName(jdbcUrl);
        log.info("[Nutera-DB] JDBC URL: {}", jdbcUrl);
        log.info("[Nutera-DB] Configured Database: {}", configuredDbName);
        log.info("[Nutera-DB] Username: {}", username);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            log.info("[Nutera-DB] Database connection: SUCCESS");
            String currentDb = queryScalar(conn, "SELECT DB_NAME()");
            log.info("[Nutera-DB] Connected Database: {}", currentDb == null ? "(unknown)" : currentDb);

            boolean hasBatchReports = tableExists(conn, "batch_reports");
            boolean hasBatchCases = tableExists(conn, "batch_report_cases");
            log.info("[Nutera-DB] Table batch_reports exists: {}", hasBatchReports);
            log.info("[Nutera-DB] Table batch_report_cases exists: {}", hasBatchCases);

            if (strict && (!hasBatchReports || !hasBatchCases)) {
                throw new IllegalStateException(
                        "Report tables are missing. Please execute backend/scripts/init-sqlserver.ps1 first."
                );
            }
        } catch (Exception ex) {
            String message = buildFriendlyError(ex);
            log.error("[Nutera-DB] Startup check failed: {}", message);
            if (strict) {
                throw new IllegalStateException(message, ex);
            }
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private String queryScalar(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractDatabaseName(String url) {
        if (url == null) {
            return "";
        }
        String marker = "databaseName=";
        int idx = url.toLowerCase().indexOf(marker.toLowerCase());
        if (idx < 0) {
            return "";
        }
        String rest = url.substring(idx + marker.length());
        int end = rest.indexOf(';');
        return end >= 0 ? rest.substring(0, end) : rest;
    }

    private String buildFriendlyError(Exception ex) {
        String raw = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String lower = raw.toLowerCase();
        if (lower.contains("cannot open database") || lower.contains("无法打开登录所请求的数据库")) {
            return "Cannot open database '" + extractDatabaseName(jdbcUrl)
                    + "'. Run backend/scripts/init-sqlserver.ps1 to initialize database/login/user/table permissions.";
        }
        if (lower.contains("login failed") || lower.contains("登录失败")) {
            return "Login failed for user '" + username
                    + "'. Check NUTERA_SQLSERVER_USERNAME / NUTERA_SQLSERVER_PASSWORD or run initialization script.";
        }
        return raw;
    }
}
