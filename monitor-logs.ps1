# PowerShell版本的日志监控脚本
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "实时监控Android应用日志" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 查找adb路径
$adbPath = $null

# 方法1: 从环境变量获取
if ($env:ANDROID_HOME) {
    $adbPath = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    if (Test-Path $adbPath) {
        Write-Host "找到adb: $adbPath" -ForegroundColor Green
    } else {
        $adbPath = $null
    }
}

# 方法2: 从ANDROID_SDK_ROOT获取
if (-not $adbPath -and $env:ANDROID_SDK_ROOT) {
    $adbPath = Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
    if (Test-Path $adbPath) {
        Write-Host "找到adb: $adbPath" -ForegroundColor Green
    } else {
        $adbPath = $null
    }
}

# 方法3: 尝试常见路径
if (-not $adbPath) {
    $commonPaths = @(
        "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\sdk\platform-tools\adb.exe",
        "C:\Android\sdk\platform-tools\adb.exe",
        "C:\Program Files\Android\android-sdk\platform-tools\adb.exe"
    )
    
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            $adbPath = $path
            Write-Host "找到adb: $adbPath" -ForegroundColor Green
            break
        }
    }
}

# 方法4: 从PATH中查找
if (-not $adbPath) {
    $adbInPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbInPath) {
        $adbPath = $adbInPath.Path
        Write-Host "从PATH中找到adb: $adbPath" -ForegroundColor Green
    }
}

# 如果找不到，显示错误
if (-not $adbPath) {
    Write-Host "错误: 找不到adb" -ForegroundColor Red
    Write-Host ""
    Write-Host "请尝试以下方法之一:" -ForegroundColor Yellow
    Write-Host "1. 设置ANDROID_HOME环境变量指向Android SDK目录"
    Write-Host "2. 将platform-tools目录添加到PATH环境变量"
    Write-Host "3. 手动输入adb的完整路径"
    Write-Host ""
    Read-Host "按Enter键退出"
    exit 1
}

Write-Host ""
Write-Host "请在手机上点击'初始化插件'按钮" -ForegroundColor Yellow
Write-Host "然后观察此窗口的日志输出" -ForegroundColor Yellow
Write-Host ""
Write-Host "按Ctrl+C停止监控" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 清空旧日志
& $adbPath logcat -c | Out-Null

# 实时监控日志，过滤NFCLockPlugin相关
& $adbPath logcat -s "NFCLockPlugin:*" "CordovaLog:*" "*:E"

