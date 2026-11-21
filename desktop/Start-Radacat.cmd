@echo off
if not exist "%~dp0.venv\Scripts\pythonw.exe" (
  echo Run setup.ps1 in this folder first. See README.md.
  pause
  exit /b 1
)
start "RADACAT" /D "%~dp0" "%~dp0.venv\Scripts\pythonw.exe" "%~dp0radacat_desktop.pyw"
