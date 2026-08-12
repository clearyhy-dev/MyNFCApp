# 聚如信 NFC 智能锁

基于 Apache Cordova 的 Android 应用，通过 NFC 贴卡完成智能锁的开锁、关锁操作。密码从 NFC 卡读取，不使用固定默认密码。

## 功能特性

- **开锁 / 关锁**：手动触发，完整流程为「读锁 ID → 贴卡读密码 → 充电 → 电机动作」
- **贴卡读密码**：锁 ID 读取成功后，在同一 NFC 会话内继续读取密码，无需移开卡片
- **电量圆环**：充电过程中实时显示电量百分比与变化速率，用于判断贴卡信号强弱
- **流程反馈**：读 ID 阶段提示「请贴卡」，读密码阶段提示「请保持贴卡」；成功或失败后圆环显示 100%
- **调试日志**：页面内置可折叠日志区，便于现场排查
- **可独立 SDK**：核心逻辑封装在 `nfcJrx.js`，可集成到其他 Cordova / WebView 页面

## 技术栈

| 项目 | 说明 |
|------|------|
| 框架 | Apache Cordova 12 + cordova-android 12 |
| 平台 | Android（minSdk 22，targetSdk 33） |
| 原生插件 | `cordova-plugin-nfc-lock`（NFCLockPlugin） |
| 前端 | 原生 HTML / CSS / JavaScript（无框架依赖） |

## 项目结构

```
MyNFCApp/
├── config.xml                 # Cordova 应用配置
├── package.json               # 依赖与 Cordova 平台/插件声明
├── icon.png                   # 应用图标源文件
├── www/
│   ├── index.html             # 主界面（按钮、圆环、日志）
│   └── resources/
│       ├── appConfig.js       # 超时、重试、权限接口等运行时配置
│       ├── lockPermission.js  # 锁权限与远程密码接口（可选）
│       └── nfcJrx.js          # NFC 开/关锁 JS SDK
├── plugins/                   # Cordova 插件（npm install / cordova prepare 后生成）
├── platforms/                 # Android 工程（cordova platform add 后生成）
├── hooks/                     # Cordova 构建钩子（如图标生成）
└── scripts/                   # 辅助脚本
```

## 环境要求

- **Node.js** 16+（建议 LTS）
- **Cordova CLI**：`npm install -g cordova`
- **JDK 17**
- **Android SDK**（API 33）
- **Gradle**（可由 Cordova / Android Gradle Plugin 自动拉取，也可指定 `GRADLE_USER_HOME`）

示例环境变量（按本机路径调整）：

```powershell
$env:JAVA_HOME = "E:\java\jdk-17"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:GRADLE_USER_HOME = "D:\gradle"
```

首次添加 Android 平台后，请确认 `platforms/android/local.properties` 中 `sdk.dir` 指向正确的 Android SDK 路径。

## 快速开始

### 1. 安装依赖

```powershell
cd MyNFCApp
npm install
```

### 2. 添加 Android 平台（若尚未添加）

```powershell
cordova platform add android
```

### 3. 构建 Debug APK

```powershell
cordova build android
```

构建产物路径：

```
platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

### 4. 安装到设备

```powershell
cordova run android
```

或手动将 `app-debug.apk` 安装到支持 NFC 的 Android 手机。

> **注意**：必须在 APK 内运行。直接用浏览器打开 `www/index.html` 无法加载 `cordova.js` 与 NFC 插件。

## 使用说明

1. 启动应用，等待顶部状态显示「已就绪」「已初始化」
2. 点击 **开锁** 或 **关锁**
3. 将手机贴近 NFC 锁卡，按提示操作：
   - 红色 **请贴卡**：读取锁 ID
   - 蓝色 **请保持贴卡**：在同一贴卡状态下读取密码
4. 充电阶段圆环随电量实时变化；流程结束后显示 **100%** 与总耗时

## 配置说明

编辑 `www/resources/appConfig.js`：

```javascript
AppConfig = {
  // 可选：远程权限密码接口
  lockPasswordApiUrl: '',
  lockPasswordApiMethod: 'GET',
  lockPasswordApiTimeoutMs: 10000,
  lockPasswordApiLockIdParam: 'lockId',

  grantAllLocks: false,
  useFallbackWhenNoMatch: false,

  nfc: {
    queryLockIdTimeoutMs: 3500,
    queryLockIdRetries: 3,
    queryLockPasswordTimeoutMs: 3500,
    queryLockPasswordRetries: 3,
    queryPowerTimeoutMs: 1200,
    queryPowerRetries: 2,
    motorTimeoutMs: 8000,
    motorRetries: 4,
    chargeMaxWaitMs: 90000
  }
};
```

- 开/关锁默认从 **NFC 卡** 读取密码，不依赖固定默认密码
- 若需改为 HTTP 接口取密码，可配置 `lockPasswordApiUrl`，并在调用时传入自定义 `readPasswordFn`（见 SDK 一节）

## JS SDK 简要 API

`nfcJrx.js` 暴露全局对象 `NfcJrxUtil`，可在任意 Cordova 页面引用：

```javascript
document.addEventListener('deviceready', function () {
  NfcJrxUtil.setHooks({
    log: console.log,
    onLockId: function (lockId) { /* 读到锁 ID */ },
    onPasswordReady: function (lockId, pwd) { /* 密码就绪 */ },
    onPower: function (percent, lockState, rate) { /* 电量采样 */ }
  });

  NfcJrxUtil.bindChargeUi({
    ring: 'chargeRingFg',
    percent: 'powerLevel',
    rate: 'chargeRate',
    status: 'unlockStatus'
  });

  NfcJrxUtil.ensureInitialized()
    .then(function () { return NfcJrxUtil.openLock(); })
    .then(function (r) {
      console.log('开锁成功，充电耗时(ms):', r.chargeMs);
    })
    .catch(function (err) {
      console.error(err);
    });
});
```

常用方法：

| 方法 | 说明 |
|------|------|
| `ensureInitialized()` | 初始化 NFC 插件 |
| `openLock()` / `closeLock()` | 执行开/关锁完整流程 |
| `runLockFlow('open' \| 'close', options)` | 底层流程，可传 `readPasswordFn` |
| `bindChargeUi(selectors)` | 绑定电量圆环 DOM |
| `abortFlow()` | 取消当前流程 |
| `prepareNextNfcRead()` | 重置 NFC 会话，准备下次读卡 |

## 常见问题

| 现象 | 可能原因 / 处理 |
|------|------------------|
| 页面提示「cordova.js 未加载」 | 未在 APK 内运行，请安装 Debug/Release 包 |
| 「NFCLockPlugin 未找到」 | 执行 `cordova prepare android` 后重新打包 |
| 读到锁 ID 后查密码超时 | 读 ID 后 **保持贴卡**；可适当增大 `queryLockPasswordTimeoutMs` / `Retries` |
| Gradle / SDK 构建失败 | 检查 `JAVA_HOME`、`ANDROID_HOME`、`local.properties` 中的 `sdk.dir` |
| 修改 `www` 后界面未更新 | 重新执行 `cordova build android`（或 `cordova prepare`） |

## 许可证

Apache License 2.0（与 Cordova 模板一致）
