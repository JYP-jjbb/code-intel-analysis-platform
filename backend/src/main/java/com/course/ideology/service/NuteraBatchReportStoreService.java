package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraBatchReportDetailResponse;
import com.course.ideology.api.dto.NuteraBatchReportSummaryResponse;
import com.course.ideology.api.dto.NuteraBatchStatusResponse;
import com.course.ideology.api.dto.NuteraGenerateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class NuteraBatchReportStoreService {
    private static final Logger log = LoggerFactory.getLogger(NuteraBatchReportStoreService.class);
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final Pattern LEADING_CHECKER_TRACE_TAG = Pattern.compile("^\\s*(?:\\[checker-runtime-v\\d+\\]\\s*)+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECKER_TRACE_TAG = Pattern.compile("\\[checker-runtime-v\\d+\\]", Pattern.CASE_INSENSITIVE);

    private final boolean enabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private volatile boolean schemaReady = false;

    public NuteraBatchReportStoreService(
            @Value("${app.nutera.report-db.enabled:true}") boolean enabled,
            @Value("${app.nutera.report-db.url:jdbc:sqlserver://localhost:1433;databaseName=code_intel_analysis;encrypt=false;trustServerCertificate=true}") String jdbcUrl,
            @Value("${app.nutera.report-db.username:test}") String username,
            @Value("${app.nutera.report-db.password:123456}") String password
    ) {
        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void saveReport(NuteraBatchStatusResponse status) {
        if (!enabled || status == null) {
            return;
        }
        try {
            ensureSchema();
            try (Connection conn = openConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement deleteCases = conn.prepareStatement(
                            "DELETE FROM batch_report_cases WHERE task_id = ?")) {
                        deleteCases.setString(1, status.getTaskId());
                        deleteCases.executeUpdate();
                    }

                    try (PreparedStatement deleteSummary = conn.prepareStatement(
                            "DELETE FROM batch_reports WHERE task_id = ?")) {
                        deleteSummary.setString(1, status.getTaskId());
                        deleteSummary.executeUpdate();
                    }

                    try (PreparedStatement insertSummary = conn.prepareStatement(
                            "INSERT INTO batch_reports " +
                                    "(task_id, dataset_name, llm_model, llm_config, total_cases, completed_cases, proved_count, not_proved_count, error_count, stop_count, started_at, finished_at, status, result_path, updated_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME())")) {
                        insertSummary.setString(1, status.getTaskId());
                        insertSummary.setString(2, status.getDatasetName());
                        insertSummary.setString(3, status.getLlmModel());
                        insertSummary.setString(4, status.getLlmConfig());
                        insertSummary.setInt(5, status.getTotalCases());
                        insertSummary.setInt(6, status.getCompletedCases());
                        insertSummary.setInt(7, status.getProvedCount());
                        insertSummary.setInt(8, status.getNotProvedCount());
                        insertSummary.setInt(9, status.getErrorCount());
                        insertSummary.setInt(10, status.getStopCount());
                        insertSummary.setTimestamp(11, toTimestamp(status.getStartedAt()));
                        insertSummary.setTimestamp(12, toTimestamp(status.getFinishedAt()));
                        insertSummary.setString(13, status.getStatus());
                        insertSummary.setString(14, status.getResultPath());
                        insertSummary.executeUpdate();
                    }

                    List<NuteraGenerateResponse.BatchCaseResult> caseResults = status.getResults();
                    if (caseResults != null) {
                        try (PreparedStatement insertCase = conn.prepareStatement(
                                "INSERT INTO batch_report_cases " +
                                        "(task_id, row_index, case_name, candidate_function, verification_status, conclusion, counterexample, message, attempt_count, final_status, stop_reason) " +
                                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                            int rowIndex = 0;
                            for (NuteraGenerateResponse.BatchCaseResult item : caseResults) {
                                insertCase.setString(1, status.getTaskId());
                                insertCase.setInt(2, rowIndex++);
                                insertCase.setString(3, item.getCaseName());
                                insertCase.setString(4, item.getCandidateFunction());
                                insertCase.setString(5, item.getVerificationStatus());
                                insertCase.setString(6, item.getConclusion());
                                insertCase.setString(7, item.getCounterexample());
                                insertCase.setString(8, item.getMessage());
                                insertCase.setInt(9, item.getAttemptCount());
                                insertCase.setString(10, item.getFinalStatus());
                                insertCase.setString(11, item.getStopReason());
                                insertCase.addBatch();
                            }
                            insertCase.executeBatch();
                        }
                    }
                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (Exception ex) {
            log.warn("Batch report persistence failed for task {}: {}", status.getTaskId(), ex.getMessage());
        }
    }

    public List<NuteraBatchReportSummaryResponse> listReports(int limit) {
        List<NuteraBatchReportSummaryResponse> items = new ArrayList<>();
        if (!enabled) {
            return items;
        }
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        try {
            ensureSchema();
            try (Connection conn = openConnection();
                PreparedStatement stmt = conn.prepareStatement(
                         "SELECT task_id, dataset_name, llm_model, llm_config, total_cases, completed_cases, proved_count, not_proved_count, error_count, stop_count, started_at, finished_at, status " +
                                 "FROM batch_reports ORDER BY started_at DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY")) {
                stmt.setInt(1, safeLimit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        NuteraBatchReportSummaryResponse item = new NuteraBatchReportSummaryResponse();
                        item.setTaskId(rs.getString("task_id"));
                        item.setDatasetName(rs.getString("dataset_name"));
                        item.setLlmModel(rs.getString("llm_model"));
                        item.setLlmConfig(rs.getString("llm_config"));
                        item.setTotalCases(rs.getInt("total_cases"));
                        item.setCompletedCases(rs.getInt("completed_cases"));
                        item.setProvedCount(rs.getInt("proved_count"));
                        item.setNotProvedCount(rs.getInt("not_proved_count"));
                        item.setErrorCount(rs.getInt("error_count"));
                        item.setStopCount(rs.getInt("stop_count"));
                        Timestamp started = rs.getTimestamp("started_at");
                        Timestamp finished = rs.getTimestamp("finished_at");
                        item.setStartedAt(started == null ? "" : started.toInstant().toString());
                        item.setFinishedAt(finished == null ? "" : finished.toInstant().toString());
                        item.setStatus(rs.getString("status"));
                        items.add(item);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Batch report query failed: {}", ex.getMessage());
        }
        return items;
    }

    public NuteraBatchReportDetailResponse getReport(String taskId) {
        if (!enabled || taskId == null || taskId.isBlank()) {
            return null;
        }
        try {
            ensureSchema();
            try (Connection conn = openConnection()) {
                NuteraBatchReportDetailResponse detail = null;
                try (PreparedStatement summaryStmt = conn.prepareStatement(
                        "SELECT task_id, dataset_name, llm_model, llm_config, total_cases, completed_cases, proved_count, not_proved_count, error_count, stop_count, started_at, finished_at, status, result_path " +
                                "FROM batch_reports WHERE task_id = ?")) {
                    summaryStmt.setString(1, taskId);
                    try (ResultSet rs = summaryStmt.executeQuery()) {
                        if (rs.next()) {
                            detail = new NuteraBatchReportDetailResponse();
                            detail.setTaskId(rs.getString("task_id"));
                            detail.setDatasetName(rs.getString("dataset_name"));
                            detail.setLlmModel(rs.getString("llm_model"));
                            detail.setLlmConfig(rs.getString("llm_config"));
                            detail.setTotalCases(rs.getInt("total_cases"));
                            detail.setCompletedCases(rs.getInt("completed_cases"));
                            detail.setProvedCount(rs.getInt("proved_count"));
                            detail.setNotProvedCount(rs.getInt("not_proved_count"));
                            detail.setErrorCount(rs.getInt("error_count"));
                            detail.setStopCount(rs.getInt("stop_count"));
                            Timestamp started = rs.getTimestamp("started_at");
                            Timestamp finished = rs.getTimestamp("finished_at");
                            detail.setStartedAt(started == null ? "" : started.toInstant().toString());
                            detail.setFinishedAt(finished == null ? "" : finished.toInstant().toString());
                            detail.setStatus(rs.getString("status"));
                            detail.setResultPath(rs.getString("result_path"));
                        }
                    }
                }
                if (detail == null) {
                    return null;
                }

                List<NuteraGenerateResponse.BatchCaseResult> results = new ArrayList<>();
                try (PreparedStatement caseStmt = conn.prepareStatement(
                        "SELECT case_name, candidate_function, verification_status, conclusion, counterexample, message, attempt_count, final_status, stop_reason " +
                                "FROM batch_report_cases WHERE task_id = ? ORDER BY row_index ASC")) {
                    caseStmt.setString(1, taskId);
                    try (ResultSet rs = caseStmt.executeQuery()) {
                        while (rs.next()) {
                            NuteraGenerateResponse.BatchCaseResult row = new NuteraGenerateResponse.BatchCaseResult();
                            row.setCaseName(rs.getString("case_name"));
                            row.setCandidateFunction(rs.getString("candidate_function"));
                            row.setVerificationStatus(rs.getString("verification_status"));
                            row.setConclusion(rs.getString("conclusion"));
                            row.setCounterexample(rs.getString("counterexample"));
                            String storedMessage = rs.getString("message");
                            String userMessage = sanitizeUserMessage(storedMessage);
                            row.setMessage(userMessage);
                            if (!userMessage.equals(storedMessage)) {
                                row.setDebugMessage(storedMessage == null ? "" : storedMessage);
                            }
                            if (hasTraceTag(storedMessage)) {
                                row.setTraceTag("checker-runtime-v2");
                            }
                            row.setAttemptCount(rs.getInt("attempt_count"));
                            row.setFinalStatus(rs.getString("final_status"));
                            row.setStopReason(rs.getString("stop_reason"));
                            results.add(row);
                        }
                    }
                }
                detail.setResults(results);
                return detail;
            }
        } catch (Exception ex) {
            log.warn("Batch report detail query failed for task {}: {}", taskId, ex.getMessage());
            return null;
        }
    }

    public List<NuteraGenerateResponse.BatchCaseResult> getReportCases(String taskId) {
        NuteraBatchReportDetailResponse detail = getReport(taskId);
        if (detail == null) {
            return null;
        }
        return detail.getResults();
    }

    public ExportPayload exportReportCsv(String taskId) {
        NuteraBatchReportDetailResponse detail = getReport(taskId);
        if (detail == null) {
            return null;
        }
        List<NuteraGenerateResponse.BatchCaseResult> rows = detail.getResults() == null ? List.of() : detail.getResults();
        List<String> csvLines = new ArrayList<>();
        csvLines.add("caseName,candidateFunction,finalStatus,verificationStatus,conclusion,counterexample,message,attemptCount,stopReason");
        for (NuteraGenerateResponse.BatchCaseResult row : rows) {
            csvLines.add(String.join(",",
                    toCsv(row.getCaseName()),
                    toCsv(row.getCandidateFunction()),
                    toCsv(row.getFinalStatus()),
                    toCsv(row.getVerificationStatus()),
                    toCsv(row.getConclusion()),
                    toCsv(row.getCounterexample()),
                    toCsv(row.getMessage()),
                    toCsv(String.valueOf(row.getAttemptCount())),
                    toCsv(row.getStopReason())
            ));
        }
        String csv = String.join(System.lineSeparator(), csvLines);
        String dataset = detail.getDatasetName() == null || detail.getDatasetName().isBlank() ? "dataset" : detail.getDatasetName();
        String suffix = detail.getStartedAt() == null || detail.getStartedAt().isBlank()
                ? EXPORT_TIME_FORMATTER.format(Instant.now())
                : safeExportTimestamp(detail.getStartedAt());
        String filename = safeFilename(dataset) + "-" + suffix + "-cases.csv";
        return new ExportPayload(filename, csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean deleteReport(String taskId) {
        if (!enabled || taskId == null || taskId.isBlank()) {
            return false;
        }
        try {
            ensureSchema();
            try (Connection conn = openConnection()) {
                conn.setAutoCommit(false);
                try {
                    int deletedCases;
                    try (PreparedStatement deleteCases = conn.prepareStatement("DELETE FROM batch_report_cases WHERE task_id = ?")) {
                        deleteCases.setString(1, taskId);
                        deletedCases = deleteCases.executeUpdate();
                    }
                    int deletedSummary;
                    try (PreparedStatement deleteSummary = conn.prepareStatement("DELETE FROM batch_reports WHERE task_id = ?")) {
                        deleteSummary.setString(1, taskId);
                        deletedSummary = deleteSummary.executeUpdate();
                    }
                    conn.commit();
                    return deletedSummary > 0 || deletedCases > 0;
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (Exception ex) {
            log.warn("Batch report delete failed for task {}: {}", taskId, ex.getMessage());
            return false;
        }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private Timestamp toTimestamp(String text) {
        Instant instant = parseInstant(text);
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private synchronized void ensureSchema() throws Exception {
        if (schemaReady) {
            return;
        }
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "IF OBJECT_ID('batch_reports', 'U') IS NULL " +
                            "CREATE TABLE batch_reports (" +
                            "id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                            "task_id NVARCHAR(64) NOT NULL UNIQUE, " +
                            "dataset_name NVARCHAR(128) NULL, " +
                            "llm_model NVARCHAR(128) NULL, " +
                            "llm_config NVARCHAR(512) NULL, " +
                            "total_cases INT NOT NULL, " +
                            "completed_cases INT NOT NULL, " +
                            "proved_count INT NOT NULL, " +
                            "not_proved_count INT NOT NULL, " +
                            "error_count INT NOT NULL, " +
                            "stop_count INT NOT NULL DEFAULT 0, " +
                            "started_at DATETIME2 NULL, " +
                            "finished_at DATETIME2 NULL, " +
                            "status NVARCHAR(32) NOT NULL, " +
                            "result_path NVARCHAR(512) NULL, " +
                            "updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()" +
                            ")"
            );
            stmt.execute(
                    "IF OBJECT_ID('batch_report_cases', 'U') IS NULL " +
                            "CREATE TABLE batch_report_cases (" +
                            "id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                            "task_id NVARCHAR(64) NOT NULL, " +
                            "row_index INT NOT NULL, " +
                            "case_name NVARCHAR(256) NULL, " +
                            "candidate_function NVARCHAR(MAX) NULL, " +
                            "verification_status NVARCHAR(32) NULL, " +
                            "conclusion NVARCHAR(32) NULL, " +
                            "counterexample NVARCHAR(MAX) NULL, " +
                            "message NVARCHAR(MAX) NULL, " +
                            "attempt_count INT NOT NULL DEFAULT 0, " +
                            "final_status NVARCHAR(32) NULL, " +
                            "stop_reason NVARCHAR(MAX) NULL" +
                            ")"
            );
            stmt.execute("IF COL_LENGTH('batch_reports', 'stop_count') IS NULL ALTER TABLE batch_reports ADD stop_count INT NOT NULL CONSTRAINT DF_batch_reports_stop_count DEFAULT 0");
            stmt.execute("IF COL_LENGTH('batch_reports', 'llm_model') IS NULL ALTER TABLE batch_reports ADD llm_model NVARCHAR(128) NULL");
            stmt.execute("IF COL_LENGTH('batch_reports', 'llm_config') IS NULL ALTER TABLE batch_reports ADD llm_config NVARCHAR(512) NULL");
            stmt.execute("IF COL_LENGTH('batch_report_cases', 'attempt_count') IS NULL ALTER TABLE batch_report_cases ADD attempt_count INT NOT NULL CONSTRAINT DF_batch_report_cases_attempt_count DEFAULT 0");
            stmt.execute("IF COL_LENGTH('batch_report_cases', 'final_status') IS NULL ALTER TABLE batch_report_cases ADD final_status NVARCHAR(32) NULL");
            stmt.execute("IF COL_LENGTH('batch_report_cases', 'stop_reason') IS NULL ALTER TABLE batch_report_cases ADD stop_reason NVARCHAR(MAX) NULL");
            stmt.execute(
                    "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_batch_report_cases_task' AND object_id = OBJECT_ID('batch_report_cases')) " +
                            "CREATE INDEX idx_batch_report_cases_task ON batch_report_cases(task_id, row_index)"
            );
        }
        schemaReady = true;
    }

    private String toCsv(String value) {
        String text = value == null ? "" : value;
        boolean quoted = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quoted) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String safeFilename(String text) {
        return text.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String safeExportTimestamp(String isoInstant) {
        try {
            Instant instant = Instant.parse(isoInstant);
            return EXPORT_TIME_FORMATTER.format(instant);
        } catch (Exception ignored) {
            return EXPORT_TIME_FORMATTER.format(Instant.now());
        }
    }

    private String sanitizeUserMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return LEADING_CHECKER_TRACE_TAG.matcher(message).replaceFirst("").trim();
    }

    private boolean hasTraceTag(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return CHECKER_TRACE_TAG.matcher(message).find();
    }

    public static final class ExportPayload {
        private final String filename;
        private final byte[] content;

        public ExportPayload(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        public String getFilename() {
            return filename;
        }

        public byte[] getContent() {
            return content;
        }
    }
}
