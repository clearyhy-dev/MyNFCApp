@echo off
chcp 65001 >nul
echo ========================================
echo 实时监控Android应用日志
echo ========================================
echo.

REM 尝试多个可能的adb路径
set ADB_PATH=

REM 方法1: 从ANDROID_HOME环境变量获取
if defined ANDROID_HOME (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
    if exist "%ADB_PATH%" (
        echo 找到adb: %ADB_PATH%
        goto :found_adb
    )
)

REM 方法2: 从ANDROID_SDK_ROOT环境变量获取
if defined ANDROID_SDK_ROOT (
    set ADB_PATH=%ANDROID_SDK_ROOT%\platform-tools\adb.exe
    if exist "%ADB_PATH%" (
        echo 找到adb: %ADB_PATH%
        goto :found_adb
    )
)

REM 方法3: 尝试常见的安装路径
set COMMON_PATHS=%LOCALAPPDATA%\Android\sdk\platform-tools\adb.exe ^
    %USERPROFILE%\AppData\Local\Android\sdk\platform-tools\adb.exe ^
    C:\Android\sdk\platform-tools\adb.exe ^
    C:\Program Files\Android\android-sdk\platform-tools\adb.exe

for %%P in (%COMMON_PATHS%) do (
    if exist "%%P" (
        set ADB_PATH=%%P
        echo 找到adb: %%P
        goto :found_adb
    )
)

REM 方法4: 尝试在PATH中查找
where adb >nul 2>&1
if %ERRORLEVEL% == 0 (
    set ADB_PATH=adb
    echo 从PATH中找到adb
    goto :found_adb
)

REM 如果都找不到，显示错误
echo 错误: 找不到adb
echo.
echo 请尝试以下方法之一:
echo 1. 设置ANDROID_HOME环境变量指向Android SDK目录
echo 2. 将platform-tools目录添加到PATH环境变量
echo 3. 手动输入adb的完整路径
echo.
pause
exit /b 1

:found_adb
echo.
echo 请在手机上点击"初始化插件"按钮
echo 然后观察此窗口的日志输出
echo.
echo 按Ctrl+C停止监控
echo ========================================
echo.

REM 清空旧日志
"%ADB_PATH%" logcat -c

REM 实时监控日志，过滤NFCLockPlugin相关
"%ADB_PATH%" logcat -s "NFCLockPlugin:*" "CordovaLog:*" "*:E"

