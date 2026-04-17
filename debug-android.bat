@echo off
chcp 65001 >nul
echo ================================================
echo NFC App 调试脚本 - Android
echo ================================================
echo.

REM 检查是否连接了设备
echo [1/5] 检查Android设备连接...
adb devices
if %ERRORLEVEL% NEQ 0 (
    echo 错误: adb未找到或无法连接到设备
    echo 请确保:
    echo   1. 已安装Android SDK Platform Tools
    echo   2. 手机已开启USB调试
    echo   3. 已授权此电脑调试
    pause
    exit /b 1
)

echo.
echo [2/5] 清理旧构建...
call cordova clean android
if %ERRORLEVEL% NEQ 0 (
    echo 警告: cordova clean 失败，继续...
)

echo.
echo [3/5] 准备构建...
call cordova prepare android
if %ERRORLEVEL% NEQ 0 (
    echo 错误: cordova prepare 失败
    pause
    exit /b 1
)

echo.
echo [4/5] 构建Debug APK...
call cordova build android --debug
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 构建失败
    pause
    exit /b 1
)

echo.
echo [5/5] 安装到设备并运行...
call cordova run android --device
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 安装或运行失败
    echo 请检查设备连接和权限
    pause
    exit /b 1
)

echo.
echo ================================================
echo 完成! 应用已安装到设备
echo ================================================
echo.
echo 调试提示:
echo   1. 在Chrome浏览器中打开: chrome://inspect
echo   2. 找到你的设备，点击 inspect
echo   3. 查看Console标签页查看调试日志
echo.
pause

