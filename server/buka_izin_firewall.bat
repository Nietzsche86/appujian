@echo off
title Izin Firewall Port 8080 - MTS ROUDHOTUT THOWALIB
cd /d "%~dp0"

echo ===================================================================
echo   MEMBUKA IZIN PORT 8080 DI WINDOWS FIREWALL UNTUK HP SISWA
echo ===================================================================
echo.

netsh advfirewall firewall add rule name="Server Proktor MTS 8080" dir=in action=allow protocol=TCP localport=8080

echo.
if %errorlevel% equ 0 (
    echo ===================================================================
    echo  [BERHASIL] Port 8080 telah diizinkan di Windows Firewall!
    echo  Perangkat HP siswa sekarang dapat terhubung ke server proktor.
    echo ===================================================================
) else (
    echo ===================================================================
    echo  [GAGAL] Perlu hak akses Administrator.
    echo  Silakan tutup jendela ini, lalu:
    echo  KLIK KANAN file ini -> pilih "Run as administrator".
    echo ===================================================================
)

echo.
pause
