# Windows desktop companion

For normal use, download the Windows ZIP from Releases, extract it and run `RADACAT-Community-Companion.exe`. No Python installation is needed. This is a 64-bit Windows build and requires Bluetooth LE.

The executable stores local settings and diagnostic logs in `%LOCALAPPDATA%\RadacatCommunityCompanion`. It does not import settings from older source-based test builds automatically. Those source builds keep data beside their Python script.

## Run from source

Install Python 3.11+ with the launcher and Tcl/Tk from python.org. In this folder, run `powershell -ExecutionPolicy Bypass -File .\setup.ps1`, then double-click `Start-Radacat.cmd`. The setup policy option applies only to that process. Keep the repository's `assets` folder beside `desktop`.

## Build a standalone EXE

On 64-bit Windows, create a virtual environment and install `requirements-build.txt`, then run `python build_windows.py` using that environment. The result is `dist/RADACAT-Community-Companion.exe`. The build collects the icon, Bluetooth dependencies and dependency licenses. Build outputs and local data must not be committed.

Run tests with `python -m unittest discover -s . -p "test_*.py"`.

The preview executable is not Authenticode signed. Included dependency licenses retain their original terms; the community application is MIT licensed.
