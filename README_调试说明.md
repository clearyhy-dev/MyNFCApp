# NFC App 调试说明

## 项目结构

- `www/index.html` - 主应用页面，包含iframe和NFC桥接
- `www/js/nfc-bridge-client.js` - NFC桥接客户端脚本（供远程H5使用）
- `resources/` - H5页面代码（参考用）
- `config.xml` - Cordova配置文件

## 工作原理

1. **主页面（www/index.html）**：
   - 通过iframe加载远程H5页面 `https://49.232.195.86/`
   - 监听iframe的postMessage消息
   - 代理NFC插件调用
   - 将结果通过postMessage返回给iframe

2. **远程H5页面**：
   - 需要在H5页面中引入 `nfc-bridge-client.js`
   - 使用 `NFCBridge` API调用NFC功能

## 使用方法

### 1. 构建和运行应用

#### 方法一：使用调试脚本（推荐）
```bash
# Windows
debug-android.bat

# 或手动执行
cordova clean android
cordova prepare android
cordova build android --debug
cordova run android --device
```

#### 方法二：手动执行
```bash
# 清理
cordova clean android

# 准备
cordova prepare android

# 构建Debug版本
cordova build android --debug

# 安装到设备
cordova run android --device
```

#### 方法三：当前环境一键打包命令（PowerShell）

```powershell
Set-Location "e:\lmis0822\MyNFCApp"
$env:ANDROID_HOME="D:\Android\Sdk"
$env:ANDROID_SDK_ROOT="D:\Android\Sdk"
$env:JAVA_HOME="E:\java\jdk-17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
npx cordova build android
```

打包输出路径：

`e:\lmis0822\MyNFCApp\platforms\android\app\build\outputs\apk\debug\app-debug.apk`

### 2. 远程H5页面集成NFC桥接

在远程H5页面（`https://49.232.195.86/`）中添加：

```html
<!-- 引入桥接脚本 -->
<script src="https://你的域名/js/nfc-bridge-client.js"></script>
<!-- 或者如果桥接脚本部署在远程服务器上 -->
<script src="https://49.232.195.86/js/nfc-bridge-client.js"></script>

<script>
// 初始化桥接
NFCBridge.init(function(success, error) {
    if (success) {
        console.log('NFC桥接已就绪');
        
        // 初始化NFC插件
        NFCBridge.initPlugin(
            function(result) {
                console.log('NFC插件初始化成功:', result);
            },
            function(err) {
                console.error('NFC插件初始化失败:', err);
            }
        );
        
        // 注册回调（用于接收异步响应）
        NFCBridge.registerCallback(
            function(result) {
                console.log('收到NFC回调:', result);
                // 处理回调数据
            },
            function(err) {
                console.error('注册回调失败:', err);
            }
        );
    } else {
        console.error('NFC桥接初始化失败:', error);
    }
});

// 使用示例：查询锁ID
function queryLockId() {
    NFCBridge.queryLockId(
        function(result) {
            console.log('锁ID查询成功:', result);
        },
        function(error) {
            console.error('锁ID查询失败:', error);
        }
    );
}

// 使用示例：开锁
function openLock(lockId, password) {
    NFCBridge.motorForward(lockId, password,
        function(result) {
            console.log('开锁成功:', result);
        },
        function(error) {
            console.error('开锁失败:', error);
        }
    );
}
</script>
```

### 3. 调试方法

#### Chrome DevTools远程调试

1. **确保应用已安装并运行在手机上**

2. **打开Chrome浏览器**，访问：
   ```
   chrome://inspect
   ```

3. **找到你的设备**，点击 "inspect" 按钮

4. **在Console中查看日志**：
   - 主页面日志：包含 `[NFC Bridge]` 前缀
   - iframe日志：需要切换到iframe的context

5. **查看Network标签**：检查远程页面是否加载成功

6. **查看调试面板**：
   - 点击应用右下角的🐛按钮可以显示/隐藏调试日志
   - 调试日志会显示所有消息传递和NFC调用

#### ADB日志

```bash
# 查看应用日志
adb logcat | grep -i "cordova\|nfc\|webview"

# 查看所有日志
adb logcat
```

#### 检查设备连接

```bash
# 列出已连接的设备
adb devices

# 如果设备未显示，尝试：
adb kill-server
adb start-server
adb devices
```

## 常见问题

### 1. 远程页面无法加载

**检查项**：
- 网络连接是否正常
- `https://49.232.195.86/` 是否可以访问
- config.xml中是否允许了HTTPS访问

**解决方案**：
- 检查手机网络设置
- 确认远程服务器可访问
- 查看Chrome DevTools的Network标签

### 2. NFC功能无法调用

**检查项**：
- 是否调用了 `NFCBridge.init()`
- 是否等待了 `deviceready` 事件
- NFC插件是否正确安装

**调试步骤**：
1. 打开调试面板（点击🐛按钮）
2. 查看是否有 "Cordova已就绪" 和 "NFC插件可用" 的消息
3. 查看iframe发送的消息是否被正确接收
4. 查看NFC调用是否成功执行

### 3. postMessage通信失败

**可能原因**：
- 消息来源验证失败
- iframe和父窗口不在同一域名

**调试方法**：
- 查看调试面板中的消息日志
- 检查消息格式是否正确
- 确认iframe已正确加载

### 4. 应用崩溃或闪退

**检查项**：
- AndroidManifest.xml权限配置
- NFC插件是否正确安装
- 查看adb logcat中的错误信息

## NFC API说明

可用的NFC方法（通过NFCBridge.call调用）：

- `init` - 初始化插件
- `registerCallback` - 注册全局回调
- `queryLockId` - 查询锁ID
- `queryLockPassword` - 查询锁密码
- `motorForward` - 开锁（需要lockId和password）
- `motorReverse` - 关锁（需要lockId和password）
- `queryPowerLevel` - 查询电量
- `startReadNFC` - 开始读取NFC
- `stopReadNFC` - 停止读取NFC
- 更多方法请参考 `plugins/cordova-plugin-nfc-lock/www/NFCLockPlugin.js`

## 注意事项

1. **安全性**：postMessage通信已做来源验证，但生产环境建议加强安全措施
2. **性能**：频繁的postMessage调用可能影响性能，建议合理控制调用频率
3. **兼容性**：确保目标Android版本支持所需功能（最低API 22）
4. **网络**：远程H5页面需要稳定的网络连接

## 更新日志

- 2024-XX-XX：初始版本，支持iframe加载远程H5页面和NFC功能桥接

