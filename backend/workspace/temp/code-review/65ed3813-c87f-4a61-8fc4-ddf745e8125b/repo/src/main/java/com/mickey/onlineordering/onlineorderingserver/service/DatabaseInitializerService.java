package com.mickey.onlineordering.onlineorderingserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库初始化服务
 * 负责检测数据库是否存在，如不存在则自动执行初始化脚本
 */
@Slf4j
@Service
public class DatabaseInitializerService {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private static final String DATABASE_NAME = "online_ordering";
    private static final String INIT_SQL_PATH = "/init.sql";

    public DatabaseInitializerService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 初始化数据库
     * 检查数据库是否存在，如不存在则执行初始化脚本
     * 如果数据库存在但缺少必要的表，则执行迁移脚本
     */
    public void initializeDatabase() {
        log.info("========================================");
        log.info("开始检查数据库状态...");
        log.info("========================================");

        try {
            if (isDatabaseExists()) {
                log.info("数据库 [{}] 已存在", DATABASE_NAME);
                logTableStatistics();
                
                // 检查必要的表是否存在
                if (!checkRequiredTables()) {
                    log.warn("检测到缺少必要的表，开始执行迁移脚本...");
                    executeMigrationScript();
                    log.info("数据库迁移完成！");
                    logTableStatistics();
                } else {
                    log.info("所有必要的表都已存在，无需迁移");
                }
            } else {
                log.warn("数据库 [{}] 不存在，开始执行初始化脚本...", DATABASE_NAME);
                executeInitScript();
                log.info("数据库初始化完成！");
                logTableStatistics();
            }
        } catch (Exception e) {
            log.error("数据库初始化检查失败", e);
            throw new RuntimeException("数据库初始化失败: " + e.getMessage(), e);
        }

        log.info("========================================");
        log.info("数据库检查完成");
        log.info("========================================");
    }

    /**
     * 检查数据库是否存在
     *
     * @return true-存在，false-不存在
     */
    private boolean isDatabaseExists() {
        String masterUrl = getMasterUrl();
        
        try (Connection conn = java.sql.DriverManager.getConnection(
                masterUrl, username, password);
             Statement stmt = conn.createStatement()) {

            String sql = String.format(
                "SELECT database_id FROM sys.databases WHERE name = '%s'",
                DATABASE_NAME
            );

            try (ResultSet rs = stmt.executeQuery(sql)) {
                boolean exists = rs.next();
                log.debug("数据库存在检查结果: {}", exists);
                return exists;
            }
        } catch (Exception e) {
            log.error("检查数据库是否存在时出错", e);
            return false;
        }
    }

    /**
     * 执行初始化脚本
     */
    private void executeInitScript() {
        try {
            log.info("正在读取初始化脚本: {}", INIT_SQL_PATH);
            
            // 读取 SQL 脚本文件
            String sqlScript = readSqlScript();
            
            if (sqlScript == null || sqlScript.trim().isEmpty()) {
                throw new RuntimeException("初始化脚本为空或不存在");
            }

            log.info("脚本读取成功，开始执行初始化...");
            
            // 执行 SQL 脚本
            executeSqlScript(sqlScript);
            
            log.info("初始化脚本执行成功");
            
        } catch (Exception e) {
            log.error("执行初始化脚本失败", e);
            throw new RuntimeException("执行初始化脚本失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取 SQL 脚本文件
     *
     * @return SQL 脚本内容
     */
    private String readSqlScript() {
        try {
            // 首先尝试从 classpath 读取
            var inputStream = getClass().getResourceAsStream(INIT_SQL_PATH);
            
            // 如果 classpath 中没有，尝试从项目根目录读取
            if (inputStream == null) {
                java.io.File sqlFile = new java.io.File("sql/init.sql");
                if (sqlFile.exists()) {
                    inputStream = new java.io.FileInputStream(sqlFile);
                    log.info("从项目目录读取 SQL 脚本: {}", sqlFile.getAbsolutePath());
                } else {
                    log.error("找不到初始化脚本文件");
                    return null;
                }
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.error("读取 SQL 脚本失败", e);
            return null;
        }
    }

    /**
     * 执行 SQL 脚本
     */
    private void executeSqlScript(String sqlScript) {
        String masterUrl = getMasterUrl();

        try (Connection conn = java.sql.DriverManager.getConnection(
                masterUrl, username, password);
             Statement stmt = conn.createStatement()) {

            // 按 GO 分隔符拆分 SQL 语句
            String[] sqlStatements = sqlScript.split("(?i)\\bGO\\b");
            
            int executedCount = 0;
            for (String sql : sqlStatements) {
                String trimmedSql = sql.trim();
                
                // 跳过空语句和注释
                if (trimmedSql.isEmpty() || 
                    trimmedSql.startsWith("--") || 
                    trimmedSql.startsWith("/*")) {
                    continue;
                }

                try {
                    stmt.execute(trimmedSql);
                    executedCount++;
                    log.debug("执行 SQL 语句成功 [{}]", executedCount);
                } catch (Exception e) {
                    log.warn("执行 SQL 语句时出现警告: {}", e.getMessage());
                    // 某些语句可能会失败（如数据库已存在），继续执行
                }
            }

            log.info("共执行 {} 条 SQL 语句", executedCount);

        } catch (Exception e) {
            log.error("执行 SQL 脚本失败", e);
            throw new RuntimeException("执行 SQL 脚本失败", e);
        }
    }

    /**
     * 获取 master 数据库连接 URL
     *
     * @return master 数据库 URL
     */
    private String getMasterUrl() {
        // 将数据库名称替换为 master 以连接到主数据库
        return datasourceUrl.replaceAll("databaseName=[^;]+", "databaseName=master");
    }

    /**
     * 检查必要的表是否存在
     * 
     * @return true-所有表都存在，false-缺少表
     */
    private boolean checkRequiredTables() {
        // 定义必要的表列表
        String[] requiredTables = {
            "tb_user", "tb_category", "tb_dish", "tb_cart_item", 
            "tb_order", "tb_order_item", "tb_address"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 获取所有用户表
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE TABLE_TYPE = 'BASE TABLE'";

            List<String> existingTables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    existingTables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            // 检查每个必要的表是否存在
            List<String> missingTables = new ArrayList<>();
            for (String requiredTable : requiredTables) {
                if (!existingTables.contains(requiredTable.toLowerCase())) {
                    missingTables.add(requiredTable);
                }
            }

            if (!missingTables.isEmpty()) {
                log.warn("缺少以下表: {}", String.join(", ", missingTables));
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("检查表是否存在时出错", e);
            return false;
        }
    }

    /**
     * 执行迁移脚本
     * 用于在现有数据库上添加缺失的表
     */
    private void executeMigrationScript() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            log.info("开始执行数据库迁移...");

            // 检查并创建地址表
            String checkTableSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                                  "WHERE TABLE_NAME = 'tb_address'";
            
            try (ResultSet rs = stmt.executeQuery(checkTableSql)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    log.info("创建 tb_address 表...");
                    
                    // 删除表（如果存在）
                    try {
                        stmt.execute("IF OBJECT_ID('tb_address', 'U') IS NOT NULL DROP TABLE tb_address");
                    } catch (Exception e) {
                        log.debug("删除表时出错（可能不存在）: {}", e.getMessage());
                    }
                    
                    // 创建地址表
                    String createTableSql = 
                        "CREATE TABLE tb_address ( " +
                        "    id BIGINT PRIMARY KEY IDENTITY(1,1), " +
                        "    user_id BIGINT NOT NULL, " +
                        "    receiver_name NVARCHAR(50) NOT NULL, " +
                        "    receiver_phone NVARCHAR(20) NOT NULL, " +
                        "    address NVARCHAR(200) NOT NULL, " +
                        "    is_default BIT NOT NULL DEFAULT 0, " +
                        "    created_at DATETIME2 NOT NULL DEFAULT GETDATE(), " +
                        "    updated_at DATETIME2 NOT NULL DEFAULT GETDATE(), " +
                        "    FOREIGN KEY (user_id) REFERENCES tb_user(id) " +
                        ")";
                    stmt.execute(createTableSql);
                    log.info("✓ tb_address 表创建成功");
                    
                    // 创建索引
                    try {
                        stmt.execute("CREATE INDEX idx_address_user_id ON tb_address(user_id)");
                        stmt.execute("CREATE INDEX idx_address_is_default ON tb_address(is_default)");
                        log.info("✓ 地址表索引创建成功");
                    } catch (Exception e) {
                        log.warn("创建索引时出错: {}", e.getMessage());
                    }
                    
                } else {
                    log.info("○ tb_address 表已存在，跳过");
                }
            }

            log.info("数据库迁移完成");

        } catch (Exception e) {
            log.error("执行迁移脚本失败", e);
            throw new RuntimeException("执行迁移脚本失败: " + e.getMessage(), e);
        }
    }

    /**
     * 记录数据库表统计信息
     */
    private void logTableStatistics() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 获取所有用户表
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE TABLE_TYPE = 'BASE TABLE' " +
                        "ORDER BY TABLE_NAME";

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            if (tables.isEmpty()) {
                log.warn("数据库中没有找到任何表");
            } else {
                log.info("数据库 [{}] 包含 {} 个表:", DATABASE_NAME, tables.size());
                for (String tableName : tables) {
                    log.info("  - {}", tableName);
                }
            }

        } catch (Exception e) {
            log.debug("获取表统计信息失败: {}", e.getMessage());
        }
    }
}

