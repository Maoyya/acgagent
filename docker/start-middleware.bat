@echo off
REM Start middleware containers - double-click to run
REM Starts: mysql / nacos / zipkin / sentinel-dashboard
cd /d "%~dp0"

echo ============================================
echo   Starting middleware containers
echo   mysql / nacos / zipkin / sentinel-dashboard
echo ============================================
echo.

REM Explicitly start the 4 middleware services (their deps, e.g. nacos->mysql, are pulled in automatically)
docker-compose up -d mysql nacos zipkin sentinel-dashboard

if errorlevel 1 (
    echo.
    echo [ERROR] Startup failed. Make sure Docker Desktop is running (whale icon in system tray).
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Done. Current container status:
echo ============================================
docker-compose ps
echo.
pause
