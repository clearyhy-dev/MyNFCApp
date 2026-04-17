# NFC 锁 Cordova 插件

这是一个用于集成 NFC 锁功能的 Cordova 插件，支持 Android 平台。

## 功能特性

### 基本操作
- 🔒 电机正转（开锁）
- 🔒 电机反转（关锁）
- ⏹️ 停止电机

### 锁管理
- 🆔 查询锁 ID
- 🆔 设置锁 ID
- 📊 查询锁状态
- 🔑 查询锁密码
- 🔑 设置密码（方式一）
- 🔑 设置密码（方式二）
- 🗑️ 清除密码

### 系统功能
- 📱 检查 NFC 可用性
- 🔋 查询电量及开关状态
- 📖 查询固件版本
- ℹ️ 获取锁信息
- 🔄 异步回调支持
- ⚡ 智能电量管理（自动轮询充电）

### 高级特性
- 🔄 自动电量检测和充电管理
- 📡 原始数据读取和校验
- 🎯 精确的响应类型识别
- ⚠️ 完善的错误处理机制

## 安装

### 1. 将插件添加到 Cordova 项目

```bash
cordova plugin add /path/to/your/NFCLockPlugin
```

### 2. 确保 Android 平台已添加

```bash
cordova platform add android
```

### 3. 构建项目

```bash
cordova build android
```

## 使用方法

### 1. 初始化插件

```javascript
document.addEventListener('deviceready', function() {
    // 初始化插件
    cordova.plugins.NFCLockPlugin.init(
        function(success) {
            console.log('插件初始化成功:', success);
        },
        function(error) {
            console.error('插件初始化失败:', error);
        }
    );
    
    // 注册全局回调，用于接收 SDK 的异步响应
    cordova.plugins.NFCLockPlugin.registerCallback(
        function(result) {
            console.log('收到锁响应:', result);
            // result 包含:
            // - commandId: 命令ID
            // - lockId: 锁ID
            // - success: 操作是否成功
        },
        function(error) {
            console.error('回调错误:', error);
        }
    );
}, false);
```

### 2. 基本操作

#### 开锁（电机正转）

```javascript
cordova.plugins.NFCLockPlugin.motorForward(
    'LOCK001',    // 锁ID
    '123456',     // 密码
    function(success) {
        console.log('开锁指令已发送:', success);
    },
    function(error) {
        console.error('开锁指令发送失败:', error);
    }
);
```

#### 关锁（电机反转）

```javascript
cordova.plugins.NFCLockPlugin.motorReverse(
    'LOCK001',    // 锁ID
    '123456',     // 密码
    function(success) {
        console.log('关锁指令已发送:', success);
    },
    function(error) {
        console.error('关锁指令发送失败:', error);
    }
);
```

#### 停止电机

```javascript
cordova.plugins.NFCLockPlugin.motorStop(
    'LOCK001',    // 锁ID
    '123456',     // 密码
    function(success) {
        console.log('停止电机指令已发送:', success);
    },
    function(error) {
        console.error('停止电机指令发送失败:', error);
    }
);
```

### 3. 高级功能

#### 查询锁状态

```javascript
cordova.plugins.NFCLockPlugin.queryLockStatus(
    'LOCK001',    // 锁ID
    '123456',     // 密码
    function(success) {
        console.log('查询状态指令已发送:', success);
    },
    function(error) {
        console.error('查询状态指令发送失败:', error);
    }
);
```

#### 修改密码

```javascript
cordova.plugins.NFCLockPlugin.setPassword(
    'LOCK001',    // 锁ID
    '123456',     // 原密码
    '654321',     // 新密码
    function(success) {
        console.log('修改密码指令已发送:', success);
    },
    function(error) {
        console.error('修改密码指令发送失败:', error);
    }
);
```

#### 检查 NFC 可用性

```javascript
cordova.plugins.NFCLockPlugin.isNFCAvailable(
    function(available) {
        console.log('NFC 可用:', available);
    },
    function(error) {
        console.error('检查 NFC 失败:', error);
    }
);
```

#### 获取锁信息

```javascript
cordova.plugins.NFCLockPlugin.getCurrentLockInfo(
    function(info) {
        console.log('锁信息:', info);
        // info 包含锁的连接状态等信息
    },
    function(error) {
        console.error('获取锁信息失败:', error);
    }
);
```

## API 参考

### 方法列表

#### 基本操作
| 方法名 | 参数 | 描述 |
|--------|------|------|
| `init(success, error)` | 无 | 初始化 NFC 锁 SDK |
| `registerCallback(callback, error)` | 无 | 注册全局回调函数 |
| `motorForward(lockId, password, success, error)` | lockId, password | 电机正转（开锁） |
| `motorReverse(lockId, password, success, error)` | lockId, password | 电机反转（关锁） |
| `motorStop(lockId, password, success, error)` | lockId, password | 停止电机 |

#### 锁管理
| 方法名 | 参数 | 描述 |
|--------|------|------|
| `queryLockId(success, error)` | 无 | 查询锁 ID |
| `setLockId(lockId, success, error)` | lockId | 设置锁 ID |
| `queryLockStatus(lockId, password, success, error)` | lockId, password | 查询锁状态 |
| `queryPassword(success, error)` | 无 | 查询锁密码 |
| `setPasswordFirst(password, success, error)` | password | 设置密码（方式一） |
| `setPasswordSecond(lockId, oldPassword, newPassword, success, error)` | lockId, oldPassword, newPassword | 设置密码（方式二） |
| `clearPassword(success, error)` | 无 | 清除密码 |

#### 系统功能
| 方法名 | 参数 | 描述 |
|--------|------|------|
| `isNFCAvailable(success, error)` | 无 | 检查 NFC 是否可用 |
| `queryPowerLevel(success, error)` | 无 | 查询电量及开关状态 |
| `queryVersion(success, error)` | 无 | 查询固件版本 |
| `getCurrentLockInfo(success, error)` | 无 | 获取当前锁信息 |
| `startReadNFC(success, error)` | 无 | 开始读取 NFC |
| `stopReadNFC(success, error)` | 无 | 停止读取 NFC |

### 回调数据格式

全局回调函数会接收到以下格式的数据：

#### 基本响应格式
```javascript
{
    "commandId": "命令ID",
    "lockId": "锁ID", 
    "success": true/false,
    "respCmdType": 0x102, // 响应命令类型
    // 其他字段根据响应类型而定
}
```

#### 电量查询响应
```javascript
{
    "respCmdType": 0x11d,
    "powerLevel": "100", // 电量百分比
    "lockState": "开锁状态",
    "success": true
}
```

#### 锁ID查询响应
```javascript
{
    "respCmdType": 0x102,
    "lockId": "00000001",
    "success": true
}
```

#### 版本查询响应
```javascript
{
    "respCmdType": 0x115,
    "versionName": "V1.0.0",
    "success": true
}
```

#### 密码查询响应
```javascript
{
    "respCmdType": 0x111,
    "lockPassword": "123456",
    "success": true
}
```

#### 电机操作响应
```javascript
{
    "respCmdType": 0x119, // 0x119=正转, 0x11b=反转
    "success": true,
    "lockId": "00000001"
}
```

#### 原始数据响应
```javascript
{
    "type": "rawData",
    "data": "base64编码的原始数据"
}
```

#### 数据校验响应
```javascript
{
    "type": "dataVerify",
    "valid": true/false,
    "error": "错误信息（如果校验失败）"
}
```

#### 错误响应
```javascript
{
    "error": true,
    "message": "错误信息",
    "title": "错误标题"
}
```

## 权限要求

插件会自动申请以下 Android 权限：

- `android.permission.NFC` - NFC 权限
- `android.hardware.nfc` - NFC 硬件特性

## 注意事项

1. **异步操作**: 所有锁操作都是异步的，实际结果通过全局回调函数返回
2. **NFC 支持**: 确保设备支持 NFC 功能
3. **锁连接**: 操作前请确保 NFC 锁已正确连接
4. **错误处理**: 建议为所有操作添加错误处理逻辑
5. **资源清理**: 插件会在应用销毁时自动清理资源

## 演示

查看 `demo.html` 文件获取完整的使用演示。

## 故障排除

### 常见问题

1. **插件未加载**
   - 确保已正确安装插件
   - 检查 `cordova.js` 是否正确加载

2. **NFC 不可用**
   - 检查设备是否支持 NFC
   - 确保 NFC 功能已启用

3. **操作失败**
   - 检查锁ID和密码是否正确
   - 确保 NFC 锁已正确连接
   - 查看控制台日志获取详细错误信息

### 调试

启用详细日志：

```javascript
// 在全局回调中打印详细信息
cordova.plugins.NFCLockPlugin.registerCallback(
    function(result) {
        console.log('详细响应:', JSON.stringify(result, null, 2));
    },
    function(error) {
        console.error('详细错误:', error);
    }
);
```

## 技术支持

如有问题，请检查：

1. Cordova 版本兼容性
2. Android 平台版本
3. NFC 锁 SDK 版本
4. 设备 NFC 功能状态

## 版本历史

- v1.0.0 - 初始版本，支持基本 NFC 锁功能


