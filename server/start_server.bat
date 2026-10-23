@echo off
title Server Proktor Ujian - MTS ROUDHOTUT THOWALIB
cd /d "%~dp0"

echo ===================================================================
echo     MEMULAI SERVER PROKTOR UJIAN - MTS ROUDHOTUT THOWALIB
echo ===================================================================
echo.

python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python tidak terdeteksi di komputer ini.
    echo Silakan install Python 3 dari python.org dan centang "Add to PATH".
    pause
    exit /b 1
)

python -c "import cryptography" >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Menginstal modul pendukung 'cryptography'...
    pip install cryptography
)

echo.
echo Membuka Panel Kontrol Web Proktor di Browser...
start "" "http://localhost:8080"

echo Menjalankan Server Proktor Lokal di Port 8080...
python server_proktor.py --port 8080

pause
