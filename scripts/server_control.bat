@echo off
setlocal enabledelayedexpansion

:: Server control script for Windows
set SERVER_DIR=%~dp0..
set PID_FILE=%TEMP%\genesis_ai.pid
set LOG_FILE=%SERVER_DIR%\server.log

:: Function to check if server is running
:CheckRunning
if exist "%PID_FILE%" (
    for /f "tokens=*" %%i in (%PID_FILE%) do set PID=%%i
    tasklist /FI "PID eq !PID!" | find "python.exe" > nul
    if !errorlevel! equ 0 (
        set RUNNING=1
    ) else (
        set RUNNING=0
    )
) else (
    set RUNNING=0
)
goto :eof

:: Function to start the server
:StartServer
call :CheckRunning
if !RUNNING! equ 1 (
    echo Server is already running (PID: !PID!)
    goto :eof
)

echo Starting Genesis AI server...
cd /d "%SERVER_DIR%" || (
    echo Error: Could not change to server directory
    exit /b 1
)

:: Install requirements if not already installed
pip show fastapi uvicorn firebase-admin >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Installing Python dependencies...
    pip install -r requirements.txt || (
        echo Failed to install dependencies
        exit /b 1
    )
)

:: Start the server in the background
start "Genesis AI Server" /MIN python -m uvicorn server.app:app --host 0.0.0.0 --port 8000 >> "%LOG_FILE%" 2>&1

echo %ERRORLEVEL% > "%PID_FILE%"
echo Server started with PID: %ERRORLEVEL%
echo Logs: %LOG_FILE%
goto :eof

:: Function to stop the server
:StopServer
call :CheckRunning
if !RUNNING! equ 0 (
    echo Server is not running
    goto :eof
)

echo Stopping Genesis AI server (PID: !PID!)...
taskkill /F /PID !PID! >nul 2>&1
del "%PID_FILE" >nul 2>&1
echo Server stopped
goto :eof

:: Function to check server status
:StatusServer
call :CheckRunning
if !RUNNING! equ 1 (
    echo Server is running (PID: !PID!)
    echo API: http://localhost:8000
    echo Docs: http://localhost:8000/docs
) else (
    echo Server is not running
    if exist "%PID_FILE%" del "%PID_FILE"
)
goto :eof

:: Function to view logs
:ViewLogs
if not exist "%LOG_FILE%" (
    echo No log file found at %LOG_FILE%
    exit /b 1
)

echo Tailing server logs (press Ctrl+C to exit)...
type "%LOG_FILE%"
goto :eof

:: Main script logic
if "%~1"=="" (
    echo Usage: %~nx0 {start^|stop^|restart^|status^|logs}
    echo   start   - Start the server
    echo   stop    - Stop the server
    echo   restart - Restart the server
    echo   status  - Check server status
    echo   logs    - View server logs
    exit /b 1
)

if /i "%~1"=="start" (
    call :StartServer
) else if /i "%~1"=="stop" (
    call :StopServer
) else if /i "%~1"=="restart" (
    call :StopServer
    timeout /t 2 >nul
    call :StartServer
) else if /i "%~1"=="status" (
    call :StatusServer
) else if /i "%~1"=="logs" (
    call :ViewLogs
) else (
    echo Invalid command: %~1
    exit /b 1
)

exit /b 0
