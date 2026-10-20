@echo off
:: BatchShare Windows Explorer Context Menu Installer
:: Adds "Send to BatchShare" to the right-click menu in File Explorer

echo Installing BatchShare to Windows Explorer context menu...

set "BATCHSHARE_EXE=%~dp0..\desktop\build\compose\binaries\main\app\BatchShare\BatchShare.exe"

if not exist "%BATCHSHARE_EXE%" (
    if exist "%LOCALAPPDATA%\BatchShare\BatchShare.exe" (
        set "BATCHSHARE_EXE=%LOCALAPPDATA%\BatchShare\BatchShare.exe"
    ) else if exist "%ProgramFiles%\BatchShare\BatchShare.exe" (
        set "BATCHSHARE_EXE=%ProgramFiles%\BatchShare\BatchShare.exe"
    )
)

echo Target executable: "%BATCHSHARE_EXE%"

:: Register for all files (*)
reg add "HKCU\Software\Classes\*\shell\BatchShare" /ve /d "Send to BatchShare" /f >nul 2>&1
reg add "HKCU\Software\Classes\*\shell\BatchShare" /v "Icon" /d "%BATCHSHARE_EXE%" /f >nul 2>&1
reg add "HKCU\Software\Classes\*\shell\BatchShare\command" /ve /d "\"%BATCHSHARE_EXE%\" \"%%1\"" /f >nul 2>&1

:: Register for folders/directories
reg add "HKCU\Software\Classes\Directory\shell\BatchShare" /ve /d "Send to BatchShare" /f >nul 2>&1
reg add "HKCU\Software\Classes\Directory\shell\BatchShare" /v "Icon" /d "%BATCHSHARE_EXE%" /f >nul 2>&1
reg add "HKCU\Software\Classes\Directory\shell\BatchShare\command" /ve /d "\"%BATCHSHARE_EXE%\" \"%%1\"" /f >nul 2>&1

echo.
echo [SUCCESS] "Send to BatchShare" added to your right-click context menu!
echo You can now right-click any file or folder in File Explorer to stage it in BatchShare.
echo.
pause
