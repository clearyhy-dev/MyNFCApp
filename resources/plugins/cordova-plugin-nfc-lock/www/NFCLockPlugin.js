cordova.define("cordova-plugin-nfc-lock.NFCLockPlugin", function(require, exports, module) {
var exec = require('cordova/exec');

/**
 * NFC 锁插件 JavaScript 接口
 * 提供与原生 NFC 锁 SDK 的交互功能
 */
var NFCLockPlugin = {
    
    // 全局回调函数，用于接收 SDK 的异步响应
    _globalCallback: null,
    
    /**
     * 初始化 NFC 锁 SDK
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    init: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'init', []);
    },
    
    /**
     * 注册全局回调，用于接收 SDK 的异步响应
     * @param {Function} callback - 回调函数，接收响应数据
     * @param {Function} error - 错误回调函数
     */
    registerCallback: function(callback, error) {
        this._globalCallback = callback;
        exec(
            function(result) {
                // 这里会多次被调用，接收来自 SDK 的响应
                if (callback) {
                    callback(result);
                }
            },
            error,
            'NFCLockPlugin',
            'registerCallback',
            []
        );
    },
    
    /**
     * 电机正转（开锁）
     * @param {String} lockId - 锁的 ID
     * @param {String} password - 锁的密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    motorForward: function(lockId, password, success, error) {
        exec(success, error, 'NFCLockPlugin', 'motorForward', [lockId, password]);
    },
    
    /**
     * 电机反转（关锁）
     * @param {String} lockId - 锁的 ID
     * @param {String} password - 锁的密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    motorReverse: function(lockId, password, success, error) {
        exec(success, error, 'NFCLockPlugin', 'motorReverse', [lockId, password]);
    },
    
    /**
     * 停止电机
     * @param {String} lockId - 锁的 ID
     * @param {String} password - 锁的密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    motorStop: function(lockId, password, success, error) {
        exec(success, error, 'NFCLockPlugin', 'motorStop', [lockId, password]);
    },
    
    /**
     * 查询锁状态
     * @param {String} lockId - 锁的 ID
     * @param {String} password - 锁的密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    queryLockStatus: function(lockId, password, success, error) {
        exec(success, error, 'NFCLockPlugin', 'queryLockStatus', [lockId, password]);
    },
    
    /**
     * 设置锁密码
     * @param {String} lockId - 锁的 ID
     * @param {String} oldPassword - 旧密码
     * @param {String} newPassword - 新密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    setPassword: function(lockId, oldPassword, newPassword, success, error) {
        exec(success, error, 'NFCLockPlugin', 'setPassword', [lockId, oldPassword, newPassword]);
    },
    
    /**
     * 检查 NFC 是否可用
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    isNFCAvailable: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'isNFCAvailable', []);
    },
    
    /**
     * 获取当前连接的锁信息
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    getCurrentLockInfo: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'getCurrentLockInfo', []);
    },
    
    /**
     * 查询锁 ID
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    queryLockId: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'queryLockId', []);
    },
    
    /**
     * 查询固件版本
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    queryVersion: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'queryVersion', []);
    },
    
    /**
     * 设置锁 ID
     * @param {String} lockId - 锁的 ID
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    setLockId: function(lockId, success, error) {
        exec(success, error, 'NFCLockPlugin', 'setLockId', [lockId]);
    },
    
    /**
     * 查询锁密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    queryLockPassword: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'queryLockPassword', []);
    },
    
    /**
     * 设置密码（方式一）
     * @param {String} password - 新密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    setPasswordFirst: function(password, success, error) {
        exec(success, error, 'NFCLockPlugin', 'setPasswordFirst', [password]);
    },
    
    /**
     * 设置密码（方式二）
     * @param {String} lockId - 锁的 ID
     * @param {String} oldPassword - 旧密码
     * @param {String} newPassword - 新密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    setPasswordSecond: function(lockId, oldPassword, newPassword, success, error) {
        exec(success, error, 'NFCLockPlugin', 'setPasswordSecond', [lockId, oldPassword, newPassword]);
    },
    
    /**
     * 清除密码
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    clearPassword: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'clearPassword', []);
    },
    
    /**
     * 查询电量及开关状态
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    queryPowerLevel: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'queryPowerLevel', []);
    },
    
    /**
     * 开始读取 NFC
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    startReadNFC: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'startReadNFC', []);
    },
    
    /**
     * 停止读取 NFC
     * @param {Function} success - 成功回调函数
     * @param {Function} error - 错误回调函数
     */
    stopReadNFC: function(success, error) {
        exec(success, error, 'NFCLockPlugin', 'stopReadNFC', []);
    }
};

// 导出插件
module.exports = NFCLockPlugin;

});
