@echo off
setlocal
cd /d "%~dp0"
echo AAII MOOC PC V1.1.5 - System Watchdog
echo.
echo Keep this window open. If the app disappears, the watchdog will collect the Windows exit code and recent system error events.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0WATCHDOG.ps1"
echo.
echo Diagnostic complete. A file named AAII_MOOC_PC_SYSTEM_DIAG.txt was written to your Desktop.
pause
