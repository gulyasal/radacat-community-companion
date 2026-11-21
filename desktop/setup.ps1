$ErrorActionPreference = 'Stop'
& py -3 -m venv (Join-Path $PSScriptRoot '.venv')
if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 or newer from python.org, including its Windows launcher, then retry.' }
& (Join-Path $PSScriptRoot '.venv\Scripts\python.exe') -m pip install -r (Join-Path $PSScriptRoot 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
Write-Output 'Setup complete. Double-click Start-Radacat.cmd.'
