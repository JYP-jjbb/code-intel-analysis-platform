REM ====================================================================
REM 在线订餐系统 - 一键启动脚本（增强版）
REM 功能：自动检测并清理端口冲突、安装前端依赖
REM ====================================================================

echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║                                                                ║
echo ║              在线订餐系统 - 一键启动程序                        ║
echo ║                                                                ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

REM 设置项目根目录
set PROJECT_DIR=%~dp0
cd /d %PROJECT_DIR%

REM 设置端口
set BACKEND_PORT=8080
set FRONTEND_PORT=3000

REM ====================================================================
REM 第一步：环境检查
REM ====================================================================

REM 检查 Java 环境
echo [检查] 正在检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Java 环境，请先安装 JDK 17 或更高版本
    echo [提示] 下载地址: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)
echo [成功] Java 环境检查通过
echo.

REM 检查 Maven 环境
echo [检查] 正在检查 Maven 环境...
call mvnw.cmd -v >nul 2>&1
if errorlevel 1 (
    echo [警告] Maven 检查失败，将使用 Maven Wrapper
)
echo [成功] Maven 环境检查通过
echo.

REM 检查 Node.js 环境
echo [检查] 正在检查 Node.js 环境...
node -v >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Node.js 环境，请先安装 Node.js
    echo [提示] 下载地址: https://nodejs.org/
    pause
    exit /b 1
)
echo [成功] Node.js 环境检查通过
echo.

REM ====================================================================
REM 第二步：端口冲突检测与清理
REM ====================================================================

echo ╔════════════════════════════════════════════════════════════════╗
echo ║                                                                ║
echo ║              检测并清理端口占用...                              ║
echo ║                                                                ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

REM 检查后端端口 8080
echo [检查] 正在检查后端端口 %BACKEND_PORT%...
netstat -ano | findstr ":%BACKEND_PORT%" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [警告] 端口 %BACKEND_PORT% 已被占用，正在清理...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT%" ^| findstr "LISTENING"') do (
        echo [操作] 正在终止进程 PID: %%a
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
    echo [成功] 端口 %BACKEND_PORT% 已清理
) else (
    echo [成功] 端口 %BACKEND_PORT% 可用
)
echo.

REM 检查前端端口 3000
echo [检查] 正在检查前端端口 %FRONTEND_PORT%...
netstat -ano | findstr ":%FRONTEND_PORT%" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [警告] 端口 %FRONTEND_PORT% 已被占用，正在清理...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%FRONTEND_PORT%" ^| findstr "LISTENING"') do (
        echo [操作] 正在终止进程 PID: %%a
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
    echo [成功] 端口 %FRONTEND_PORT% 已清理
) else (
    echo [成功] 端口 %FRONTEND_PORT% 可用
)
echo.

REM ====================================================================
REM 第三步：检查并安装前端依赖
REM ====================================================================

echo ╔════════════════════════════════════════════════════════════════╗
echo ║                                                                ║
echo ║              检查前端依赖...                                    ║
echo ║                                                                ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

cd online-ordering-client

REM 检查 node_modules 是否存在
if not exist "node_modules\" (
    echo [信息] 未检测到 node_modules，正在安装所有依赖...
    call npm install
    if errorlevel 1 (
        echo [错误] 前端依赖安装失败
        pause
        exit /b 1
    )
    echo [成功] 前端依赖安装完成
) else (
    echo [信息] node_modules 已存在，检查新增依赖...
    
    REM 检查 vue-i18n 是否已安装
    if not exist "node_modules\vue-i18n\" (
        echo [信息] 正在安装 vue-i18n...
        call npm install vue-i18n
        if errorlevel 1 (
            echo [警告] vue-i18n 安装失败，但不影响基础功能
        ) else (
            echo [成功] vue-i18n 安装完成
        )
    ) else (
        echo [成功] vue-i18n 已安装
    )
    
    REM 检查其他可能缺失的依赖
    echo [信息] 正在检查并安装其他缺失的依赖...
    call npm install >nul 2>&1
)

echo.
cd ..

REM ====================================================================
REM 第四步：检查数据库配置
REM ====================================================================

echo ╔════════════════════════════════════════════════════════════════╗
echo ║                                                                ║
echo ║              数据库配置提示                                     ║
echo ║                                                                ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo [提示] 请确保 SQL Server 数据库服务已启动
echo [提示] 数据库配置信息在 src\main\resources\application.properties
echo [提示] 微信支付配置信息在 src\main\resources\wxpay.properties
echo.

REM ====================================================================
REM 第五步：启动后端服务
REM ====================================================================

echo ╔════════════════════════════════════════════════════════════════╗
echo ║                                                                ║
echo ║              正在启动后端服务...                                ║
echo ║                                                                ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo [信息] 后端将自动执行以下操作：
echo [信息]   1. 检测数据库是否存在
echo [信息]   2. 如不存在，自动执行 init.sql 初始化脚本
echo [信息]   3. 自动启动前端服务
echo [信息]   4. 显示访问地址和端口信息
echo.
echo [信息] 后端服务启动中，请稍候...
echo [信息] 访问地址: http://localhost:%BACKEND_PORT%
echo.

REM 使用 Maven Wrapper 启动 Spring Boot 应用
call mvnw.cmd spring-boot:run

REM 如果启动失败
if errorlevel 1 (
    echo.
    echo ╔════════════════════════════════════════════════════════════════╗
    echo ║                                                                ║
    echo ║              启动失败！                                         ║
    echo ║                                                                ║
    echo ╚════════════════════════════════════════════════════════════════╝
    echo.
    echo [错误] 应用启动失败，请检查以下内容：
    echo   1. 数据库服务是否已启动
    echo   2. 数据库连接配置是否正确（application.properties）
    echo   3. 端口是否被占用（默认：后端 8080，前端 3000）
    echo   4. 微信支付私钥文件是否存在（apiclient_key.pem）
    echo   5. 查看上方错误日志获取详细信息
    echo.
    echo [提示] 如果是微信支付相关错误，可以暂时禁用微信支付功能：
    echo   在 src\main\resources\wxpay.properties 中添加：
    echo   wxpay.enabled=false
    echo.
    pause
    exit /b 1
)

pause
