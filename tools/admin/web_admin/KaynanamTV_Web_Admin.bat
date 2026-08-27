@echo off
chcp 65001 > nul
title KaynanamTV — Ultra-Modern Web Yonetici Paneli
color 0B

echo ======================================================================
echo           KaynanamTV - Ultra-Modern Web Yonetim Paneli
echo ======================================================================
echo.
echo [1/2] Web Sunucusu Baslatiliyor (http://localhost:5000)...

cd /d "%~dp0"

:: Tarayicida otomatik olarak 2 saniye sonra ac
start "" cmd /c "timeout /t 2 /nobreak >nul && start http://localhost:5000"

:: Python sunucusunu calistir
python server.py

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [HATA] Python sunucusu baslatilamadi!
    echo Lutfen python ve firebase-admin kurulu oldugundan emin olun.
    pause
)
