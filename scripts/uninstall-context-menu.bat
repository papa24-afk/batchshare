@echo off
:: BatchShare Windows Explorer Context Menu Uninstaller

echo Removing BatchShare from Windows Explorer context menu...

reg delete "HKCU\Software\Classes\*\shell\BatchShare" /f >nul 2>&1
reg delete "HKCU\Software\Classes\Directory\shell\BatchShare" /f >nul 2>&1

echo.
echo [SUCCESS] BatchShare removed from context menu.
echo.
pause
