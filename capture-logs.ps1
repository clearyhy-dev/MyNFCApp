# 捕获Android应用日志脚本
$adbPath = "$env:ANDROID_HOME\platform-tools\adb.exe"

if (-not (Test-Path $adbPath)) {
    Write-Host "错误: 找不到adb，请确保ANDROID_HOME环境变量已设置" -ForegroundColor Red
    exit 1
}

Write-Host "开始捕获应用日志..." -ForegroundColor Green
Write-Host "按Ctrl+C停止" -ForegroundColor Yellow
Write-Host ""

# 清空旧日志
& $adbPath logcat -c

# 捕获日志（过滤相关日志）
& $adbPath logcat -s "CordovaLog:*" "Chromium:*" "SystemWebChromeClient:*" "AndroidRuntime:*" "*:E" "com.nfclock.nfcapp:*"

