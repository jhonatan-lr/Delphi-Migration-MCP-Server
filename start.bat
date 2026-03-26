@echo off
REM ─────────────────────────────────────────────────────────────────────────────
REM start.bat — Delphi Migration MCP Server
REM ─────────────────────────────────────────────────────────────────────────────
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%target\delphi-migration-mcp-1.0.0.jar

REM ── Verifica Java ─────────────────────────────────────────────────────────────
where java >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Java nao encontrado. Instale Java 17+ e tente novamente.
    pause
    exit /b 1
)

REM ── Build se necessário ───────────────────────────────────────────────────────
if not exist "%JAR%" (
    echo [INFO] JAR nao encontrado. Fazendo build com Maven...
    where mvn >nul 2>&1
    if errorlevel 1 (
        echo [ERRO] Maven nao encontrado. Instale Maven 3.8+ e tente novamente.
        pause
        exit /b 1
    )
    cd /d "%SCRIPT_DIR%"
    call mvn clean package -q -DskipTests
    if errorlevel 1 (
        echo [ERRO] Build falhou. Verifique os logs acima.
        pause
        exit /b 1
    )
    echo [OK] Build concluido.
)

REM ── Inicia o servidor ─────────────────────────────────────────────────────────
echo [INFO] Iniciando Delphi Migration MCP Server...
echo [INFO] JAR: %JAR%
echo [INFO] Perfil em: %USERPROFILE%\.delphi-mcp\project-profile.json
echo [INFO] Logs em: %SCRIPT_DIR%logs\delphi-mcp.log
echo.

java -Xmx512m -Xms128m -Dfile.encoding=UTF-8 -jar "%JAR%" %*
