# 自动获取并分析日志
$adbPath = "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    $adbPath = "$env:ANDROID_HOME\platform-tools\adb.exe"
}

if (-not (Test-Path $adbPath)) {
    Write-Host "错误: 找不到adb" -ForegroundColor Red
    exit 1
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "准备获取日志" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "请按以下步骤操作:" -ForegroundColor Yellow
Write-Host "1. 打开应用，点击'打开H5页面'" -ForegroundColor White
Write-Host "2. 点击'初始化插件'按钮" -ForegroundColor White
Write-Host "3. 等待3秒后，我会自动获取日志" -ForegroundColor White
Write-Host ""
Write-Host "正在清空旧日志..." -ForegroundColor Green
& $adbPath logcat -c | Out-Null

Write-Host ""
Write-Host "等待您操作（10秒）..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "获取NFCLockPlugin相关日志" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
& $adbPath logcat -d -s "NFCLockPlugin:*" | Select-Object -Last 50

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "获取所有包含execute、init的日志" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
& $adbPath logcat -d | Select-String -Pattern "execute方法被调用|init方法被调用|PluginManager|NFCLock" -CaseSensitive:$false | Select-Object -Last 40

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "分析完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

