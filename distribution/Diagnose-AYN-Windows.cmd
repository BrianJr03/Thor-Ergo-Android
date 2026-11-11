@echo off
setlocal EnableExtensions DisableDelayedExpansion
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Installers\install-windows.ps1" -AynDiagnostic
exit /b %errorlevel%
