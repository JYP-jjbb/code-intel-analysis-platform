-- Idempotent SQL Server initialization script for Nutera batch reports.
-- Usage (sqlcmd):
-- sqlcmd -S localhost,1433 -U sa -P "<AdminPassword>" -i backend/scripts/init-code-intel-analysis.sql

SET NOCOUNT ON;

USE [master];
GO

IF DB_ID(N'code_intel_analysis') IS NULL
BEGIN
    PRINT N'Creating database [code_intel_analysis]...';
    CREATE DATABASE [code_intel_analysis];
END
ELSE
BEGIN
    PRINT N'Database [code_intel_analysis] already exists.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.sql_logins WHERE name = N'test')
BEGIN
    PRINT N'Creating SQL login [test]...';
    CREATE LOGIN [test] WITH PASSWORD = N'123456', CHECK_POLICY = OFF, CHECK_EXPIRATION = OFF;
END
ELSE
BEGIN
    PRINT N'Login [test] already exists.';
END
GO

USE [code_intel_analysis];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'test')
BEGIN
    PRINT N'Creating database user [test]...';
    CREATE USER [test] FOR LOGIN [test];
END
ELSE
BEGIN
    PRINT N'Database user [test] already exists.';
END
GO

IF IS_ROLEMEMBER(N'db_owner', N'test') <> 1
BEGIN
    PRINT N'Granting db_owner role to [test]...';
    EXEC sp_addrolemember N'db_owner', N'test';
END
ELSE
BEGIN
    PRINT N'User [test] already has db_owner role.';
END
GO

IF OBJECT_ID(N'dbo.batch_reports', N'U') IS NULL
BEGIN
    PRINT N'Creating table [dbo].[batch_reports]...';
    CREATE TABLE dbo.batch_reports (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        task_id NVARCHAR(64) NOT NULL UNIQUE,
        dataset_name NVARCHAR(128) NULL,
        total_cases INT NOT NULL,
        completed_cases INT NOT NULL,
        proved_count INT NOT NULL,
        not_proved_count INT NOT NULL,
        error_count INT NOT NULL,
        stop_count INT NOT NULL DEFAULT 0,
        started_at DATETIME2 NULL,
        finished_at DATETIME2 NULL,
        status NVARCHAR(32) NOT NULL,
        result_path NVARCHAR(512) NULL,
        updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
ELSE
BEGIN
    PRINT N'Table [dbo].[batch_reports] already exists.';
END
GO

IF OBJECT_ID(N'dbo.batch_report_cases', N'U') IS NULL
BEGIN
    PRINT N'Creating table [dbo].[batch_report_cases]...';
    CREATE TABLE dbo.batch_report_cases (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        task_id NVARCHAR(64) NOT NULL,
        row_index INT NOT NULL,
        case_name NVARCHAR(256) NULL,
        candidate_function NVARCHAR(MAX) NULL,
        verification_status NVARCHAR(32) NULL,
        conclusion NVARCHAR(32) NULL,
        counterexample NVARCHAR(MAX) NULL,
        message NVARCHAR(MAX) NULL,
        attempt_count INT NOT NULL DEFAULT 0,
        final_status NVARCHAR(32) NULL,
        stop_reason NVARCHAR(MAX) NULL
    );
END
ELSE
BEGIN
    PRINT N'Table [dbo].[batch_report_cases] already exists.';
END
GO

IF OBJECT_ID(N'dbo.code_run_tasks', N'U') IS NULL
BEGIN
    PRINT N'Creating table [dbo].[code_run_tasks]...';
    CREATE TABLE dbo.code_run_tasks (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        task_id NVARCHAR(64) NOT NULL UNIQUE,
        language NVARCHAR(20) NOT NULL,
        source_code NVARCHAR(MAX) NULL,
        stdin_text NVARCHAR(MAX) NULL,
        task_status NVARCHAR(20) NOT NULL,
        compile_status NVARCHAR(20) NULL,
        run_status NVARCHAR(30) NULL,
        stdout_text NVARCHAR(MAX) NULL,
        stderr_text NVARCHAR(MAX) NULL,
        exit_code INT NULL,
        time_ms BIGINT NULL,
        error_message NVARCHAR(1000) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        start_time DATETIME2 NULL,
        finish_time DATETIME2 NULL,
        updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
ELSE
BEGIN
    PRINT N'Table [dbo].[code_run_tasks] already exists.';
END
GO

IF COL_LENGTH('dbo.batch_reports', 'stop_count') IS NULL
BEGIN
    ALTER TABLE dbo.batch_reports ADD stop_count INT NOT NULL CONSTRAINT DF_batch_reports_stop_count DEFAULT 0;
END
GO

IF COL_LENGTH('dbo.batch_report_cases', 'attempt_count') IS NULL
BEGIN
    ALTER TABLE dbo.batch_report_cases ADD attempt_count INT NOT NULL CONSTRAINT DF_batch_report_cases_attempt_count DEFAULT 0;
END
GO

IF COL_LENGTH('dbo.batch_report_cases', 'final_status') IS NULL
BEGIN
    ALTER TABLE dbo.batch_report_cases ADD final_status NVARCHAR(32) NULL;
END
GO

IF COL_LENGTH('dbo.batch_report_cases', 'stop_reason') IS NULL
BEGIN
    ALTER TABLE dbo.batch_report_cases ADD stop_reason NVARCHAR(MAX) NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'idx_batch_report_cases_task'
      AND object_id = OBJECT_ID(N'dbo.batch_report_cases')
)
BEGIN
    PRINT N'Creating index [idx_batch_report_cases_task]...';
    CREATE INDEX idx_batch_report_cases_task ON dbo.batch_report_cases(task_id, row_index);
END
ELSE
BEGIN
    PRINT N'Index [idx_batch_report_cases_task] already exists.';
END
GO

PRINT N'SQL Server initialization completed.';
GO
