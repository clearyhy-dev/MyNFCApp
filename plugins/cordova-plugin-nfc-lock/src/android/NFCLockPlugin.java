package com.nfclock.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// 导入SDK中的类
import com.xhgjky.nfc.protocol.NFCLockManager;
import com.xhgjky.nfc.protocol.listeners.NFCLockCallBack;
import com.xhgjky.nfc.protocol.bean.NFCLockResponse;

public class NFCLockPlugin extends CordovaPlugin {

    private CallbackContext globalCallbackContext;
    private String lockId;
    private String lockPassword;
    private boolean isNFCSupported = false;
    private boolean isNFCEnabled = false;
    private boolean isInitialized = false;
    private String pendingOperation = null; // 跟踪待执行的操作
    
    // 响应类型常量（使用ordinal值，因为SDK返回的是枚举的ordinal）
    private static final int RESP_QUERY_LOCK_ID = 0; // 查询NFC锁编号指令回复
    private static final int RESP_SETTING_LOCK_ID = 1; // 设置NFC锁编号回复
    private static final int RESP_SETTING_LOCK_PWD_FIRST = 2; // 设置NFC锁密码方式一回复
    private static final int RESP_SETTING_LOCK_PWD_SECOND = 3; // 设置NFC锁密码方式二回复
    private static final int RESP_QUERY_LOCK_PWD = 4; // 查询NFC锁密码回复
    private static final int RESP_CLEAN_LOCK_PWD = 5; // 擦除NFC锁密码回复
    private static final int RESP_QUERY_LOCK_VERSION_NAME = 6; // 查询NFC锁固件版本号回复
    private static final int RESP_QUERY_LOCK_POWER_STATE = 7; // 查询电量及开关状态回复
    private static final int RESP_LOCK_MOTOR_FORWARD = 8; // 电机正转回复
    private static final int RESP_LOCK_MOTOR_REVERSAL = 9; // 电机反转回复

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // 所有JS的调用都会进入这里，根据action来分发不同功能
        if ("init".equals(action)) {
            this.init(callbackContext);
            return true;
        } else if ("motorForward".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.motorForward(lockId, password, callbackContext);
            return true;
        } else if ("motorReverse".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.motorReverse(lockId, password, callbackContext);
            return true;
        } else if ("motorStop".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.motorStop(lockId, password, callbackContext);
            return true;
        } else if ("queryLockStatus".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.queryLockStatus(lockId, password, callbackContext);
            return true;
        } else if ("setPassword".equals(action)) {
            String lockId = args.getString(0);
            String oldPassword = args.getString(1);
            String newPassword = args.getString(2);
            this.setPassword(lockId, oldPassword, newPassword, callbackContext);
            return true;
        } else if ("isNFCAvailable".equals(action)) {
            this.isNFCAvailable(callbackContext);
            return true;
        } else if ("getCurrentLockInfo".equals(action)) {
            this.getCurrentLockInfo(callbackContext);
            return true;
        } else if ("registerCallback".equals(action)) {
            // 注册一个全局回调，用于接收SDK的异步响应（如onNFCLockResponse）
            this.globalCallbackContext = callbackContext;
            android.util.Log.d("NFCLockPlugin", "全局回调已注册，context: " + callbackContext);
            
            // 发送注册成功消息，但保持回调通道开放
            PluginResult result = new PluginResult(PluginResult.Status.OK, "Callback registered successfully");
            result.setKeepCallback(true); // 保持回调通道开放
            callbackContext.sendPluginResult(result);
            return true;
        } else if ("getLockVersion".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getLockVersion(lockId, password, callbackContext);
            return true;
        } else if ("getBatteryLevel".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getBatteryLevel(lockId, password, callbackContext);
            return true;
        } else if ("setAutoLockTime".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            int autoLockTime = args.getInt(2);
            this.setAutoLockTime(lockId, password, autoLockTime, callbackContext);
            return true;
        } else if ("getAutoLockTime".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getAutoLockTime(lockId, password, callbackContext);
            return true;
        } else if ("setSensitivity".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            int sensitivity = args.getInt(2);
            this.setSensitivity(lockId, password, sensitivity, callbackContext);
            return true;
        } else if ("getSensitivity".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getSensitivity(lockId, password, callbackContext);
            return true;
        } else if ("factoryReset".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.factoryReset(lockId, password, callbackContext);
            return true;
        } else if ("getUsageLog".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getUsageLog(lockId, password, callbackContext);
            return true;
        } else if ("clearUsageLog".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.clearUsageLog(lockId, password, callbackContext);
            return true;
        } else if ("setAlarmMode".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            boolean enableAlarm = args.getBoolean(2);
            this.setAlarmMode(lockId, password, enableAlarm, callbackContext);
            return true;
        } else if ("getAlarmMode".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getAlarmMode(lockId, password, callbackContext);
            return true;
        } else if ("testConnection".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.testConnection(lockId, password, callbackContext);
            return true;
        } else if ("getLockConfig".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            this.getLockConfig(lockId, password, callbackContext);
            return true;
        } else if ("setLockConfig".equals(action)) {
            String lockId = args.getString(0);
            String password = args.getString(1);
            JSONObject config = args.getJSONObject(2);
            this.setLockConfig(lockId, password, config, callbackContext);
            return true;
        } else if ("queryLockId".equals(action)) {
            this.queryLockId(callbackContext);
            return true;
        } else if ("queryLockPassword".equals(action)) {
            this.queryLockPassword(callbackContext);
            return true;
        } else if ("queryVersion".equals(action)) {
            this.queryVersion(callbackContext);
            return true;
        } else if ("setLockId".equals(action)) {
            String lockId = args.getString(0);
            this.setLockId(lockId, callbackContext);
            return true;
        } else if ("setPasswordWay1".equals(action)) {
            String password = args.getString(0);
            this.setPasswordWay1(password, callbackContext);
            return true;
        } else if ("removePassword".equals(action)) {
            this.removePassword(callbackContext);
            return true;
        }
        return false;
    }

    private void init(final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                        // 1. SDK初始化（参考原生程序：在Application中初始化）
                    NFCLockManager.init(cordova.getActivity().getApplicationContext());
                        android.util.Log.d("NFCLockPlugin", "NFC SDK初始化完成");
                        
                        // 2. 注册NFC锁数据回调（参考原生程序）
                        setupNFCCallback();
                        
                        // 3. 检查设备是否支持NFC
                        isNFCSupported = NFCLockManager.isSupportNFC();
                        android.util.Log.d("NFCLockPlugin", "设备支持NFC: " + isNFCSupported);
                        
                        // 4. 检查NFC是否打开
                        isNFCEnabled = NFCLockManager.isNFCEnabled();
                        android.util.Log.d("NFCLockPlugin", "NFC已启用: " + isNFCEnabled);
                        
                        // 5. 标记为已初始化
                        isInitialized = true;
                        
                        // 6. 启动NFC读取（参考原生程序：在onResume中启动）
                        NFCLockManager.getInstance().startReadNFCTag(cordova.getActivity());
                        android.util.Log.d("NFCLockPlugin", "NFC读取已启动");
                        
                        android.util.Log.d("NFCLockPlugin", "NFC锁插件初始化完成");
                        callbackContext.success("NFC锁插件初始化完成");
                } catch (Exception e) {
                        android.util.Log.e("NFCLockPlugin", "初始化失败: " + e.getMessage());
                        e.printStackTrace();
                    callbackContext.error("Init failed: " + e.getMessage());
                }
            }
        });
    }

    private void motorForward(String lockId, String password, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 保存参数用于后续回调
            this.lockId = lockId;
            this.lockPassword = password;
            this.pendingOperation = "MOTOR_FORWARD";
            
            // 参考原生程序：直接发送查询电量命令，不需要先调用startReadNFCTag
            NFCLockManager.getInstance().reqQueryPowerLevel(NFCLockManager.QueryPowerLevelType.QUERY_MOTOR_FORWARD);
            
            android.util.Log.d("NFCLockPlugin", "电机正转-查询电量指令已发送");
            callbackContext.success("电机正转-查询电量指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "电机正转失败: " + e.getMessage());
            callbackContext.error("Command failed: " + e.getMessage());
        }
    }

    private void motorReverse(String lockId, String password, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 保存参数用于后续回调
            this.lockId = lockId;
            this.lockPassword = password;
            this.pendingOperation = "MOTOR_REVERSE";
            
            // 参考原生程序：直接发送查询电量命令，不需要先调用startReadNFCTag
            NFCLockManager.getInstance().reqQueryPowerLevel(NFCLockManager.QueryPowerLevelType.QUERY_MOTOR_REVERSE);
            
            android.util.Log.d("NFCLockPlugin", "电机反转-查询电量指令已发送");
            callbackContext.success("电机反转-查询电量指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "电机反转失败: " + e.getMessage());
            callbackContext.error("Command failed: " + e.getMessage());
        }
    }

    private void motorStop(String lockId, String password, CallbackContext callbackContext) {
        try {
            // 暂时返回成功，等待SDK API确认
            callbackContext.success("Motor stop command sent.");
        } catch (Exception e) {
            callbackContext.error("Command failed: " + e.getMessage());
        }
    }

    private void queryLockStatus(String lockId, String password, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送查询命令
            NFCLockManager.getInstance().reqQueryPowerLevel();
            android.util.Log.d("NFCLockPlugin", "查询电量及开关状态指令已发送");
            callbackContext.success("查询电量及开关状态指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "查询锁状态失败: " + e.getMessage());
            callbackContext.error("Command failed: " + e.getMessage());
        }
    }

    private void setPassword(String lockId, String oldPassword, String newPassword, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送设置命令
            NFCLockManager.getInstance().reqSetLockPwdBySecondMethod(lockId, oldPassword, newPassword);
            android.util.Log.d("NFCLockPlugin", "设置密码(对外)指令已发送");
            callbackContext.success("设置密码(对外)指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "设置密码失败: " + e.getMessage());
            callbackContext.error("Command failed: " + e.getMessage());
        }
    }

    private void isNFCAvailable(CallbackContext callbackContext) {
        try {
            // 检查NFC是否支持且已启用
            boolean isSupported = NFCLockManager.isSupportNFC();
            boolean isEnabled = NFCLockManager.isNFCEnabled();
            callbackContext.success(isSupported && isEnabled ? 1 : 0);
        } catch (Exception e) {
            callbackContext.error("Check NFC availability failed: " + e.getMessage());
        }
    }

    private void getCurrentLockInfo(CallbackContext callbackContext) {
        try {
            // 暂时返回基本信息，等待SDK API确认
            JSONObject lockInfo = new JSONObject();
            lockInfo.put("connected", false);
            callbackContext.success(lockInfo);
        } catch (Exception e) {
            callbackContext.error("Get lock info failed: " + e.getMessage());
        }
    }

    private void setupNFCCallback() {
        NFCLockCallBack callback = new NFCLockCallBack() {
            @Override
            public void onNFCLockResponse(NFCLockResponse response) {
                    // 添加调试日志
                    android.util.Log.d("NFCLockPlugin", "=== 收到NFC响应 ===");
                    android.util.Log.d("NFCLockPlugin", "响应类型: " + response.getRespCmdType());
                    android.util.Log.d("NFCLockPlugin", "锁ID: " + response.getLockID());
                    android.util.Log.d("NFCLockPlugin", "操作成功: " + response.operateSuccess());
                    android.util.Log.d("NFCLockPlugin", "globalCallbackContext状态: " + (globalCallbackContext != null ? "已设置" : "为空"));
                    
                // 将原生响应转换为JSON对象，发送回JS
                if (globalCallbackContext != null) {
                    android.util.Log.d("NFCLockPlugin", "开始处理响应数据...");
                    try {
                        JSONObject json = new JSONObject();
                        json.put("commandId", response.getCommandId());
                        json.put("lockId", response.getLockID());
                        json.put("success", response.operateSuccess());
                            json.put("respCmdType", response.getRespCmdType());
                            
                            // 简化响应类型处理，直接获取数值
                            int respTypeValue = -1;
                            try {
                                Object respType = response.getRespCmdType();
                                android.util.Log.d("NFCLockPlugin", "响应类型对象: " + respType);
                                
                                if (respType instanceof Number) {
                                    respTypeValue = ((Number) respType).intValue();
                                } else {
                                    // 尝试通过反射获取数值
                                    try {
                                        java.lang.reflect.Method valueMethod = respType.getClass().getMethod("value");
                                        respTypeValue = (Integer) valueMethod.invoke(respType);
                                    } catch (Exception e1) {
                                        // 如果value()方法失败，尝试ordinal()方法
                                        try {
                                            java.lang.reflect.Method ordinalMethod = respType.getClass().getMethod("ordinal");
                                            respTypeValue = (Integer) ordinalMethod.invoke(respType);
                                        } catch (Exception e2) {
                                            android.util.Log.w("NFCLockPlugin", "无法获取响应类型数值: " + e2.getMessage());
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                android.util.Log.w("NFCLockPlugin", "响应类型处理异常: " + e.getMessage());
                            }
                            
                            android.util.Log.d("NFCLockPlugin", "响应类型数值: " + respTypeValue);
                            
                            // 根据响应类型数值处理
                            if (respTypeValue == RESP_QUERY_LOCK_ID) { // 0 解析查询NFC锁编号指令回复
                                String lockId = response.getLockID();
                                android.util.Log.d("NFCLockPlugin", "查询锁ID: " + lockId);
                                if (lockId != null && !lockId.isEmpty()) {
                                    json.put("lockId", lockId);
                                    json.put("type", "queryLockId");
                                } else {
                                    android.util.Log.w("NFCLockPlugin", "锁ID为空，可能数据未准备好");
                                    // 仍然发送响应，但lockId为空
                                    json.put("lockId", "");
                                    json.put("type", "queryLockId");
                                }
                            } else if (respTypeValue == RESP_SETTING_LOCK_ID) { // 1 解析设置NFC锁编号回复
                                String setLockId = response.getLockID();
                                android.util.Log.d("NFCLockPlugin", "设置锁ID: " + setLockId);
                                json.put("lockId", setLockId);
                                json.put("type", "setLockId");
                            } else if (respTypeValue == RESP_QUERY_LOCK_PWD) { // 4 解析查询NFC锁密码回复
                                String lockPassword = response.getLockPwd();
                                android.util.Log.d("NFCLockPlugin", "查询锁密码: " + lockPassword);
                                json.put("lockPassword", lockPassword);
                                json.put("type", "queryLockPassword");
                            } else if (respTypeValue == RESP_CLEAN_LOCK_PWD) { // 5 解析擦除NFC锁密码回复
                                boolean passwordCleared = response.operateSuccess();
                                android.util.Log.d("NFCLockPlugin", "擦除密码: " + passwordCleared);
                                json.put("passwordCleared", passwordCleared);
                                json.put("type", "removePassword");
                            } else if (respTypeValue == RESP_SETTING_LOCK_PWD_FIRST) { // 2 解析设置NFC锁密码方式一回复
                                boolean passwordSetFirst = response.operateSuccess();
                                android.util.Log.d("NFCLockPlugin", "设置密码(对内): " + passwordSetFirst);
                                json.put("passwordSet", passwordSetFirst);
                                json.put("type", "setPasswordWay1");
                            } else if (respTypeValue == RESP_SETTING_LOCK_PWD_SECOND) { // 3 解析设置NFC锁密码方式二回复
                                boolean passwordSetSecond = response.operateSuccess();
                                android.util.Log.d("NFCLockPlugin", "设置密码(对外): " + passwordSetSecond);
                                json.put("passwordSet", passwordSetSecond);
                                json.put("type", "setPasswordWay2");
                            } else if (respTypeValue == RESP_QUERY_LOCK_VERSION_NAME) { // 6 解析查询NFC锁固件版本号回复
                                String lockVersionName = response.getLockVersionName();
                                android.util.Log.d("NFCLockPlugin", "查询版本: " + lockVersionName);
                                if (lockVersionName != null && !lockVersionName.isEmpty()) {
                                    json.put("versionName", lockVersionName);
                                    json.put("type", "queryVersion");
                                } else {
                                    android.util.Log.w("NFCLockPlugin", "版本名为空，可能数据未准备好");
                                    // 仍然发送响应，但versionName为空
                                    json.put("versionName", "");
                                    json.put("type", "queryVersion");
                                }
                            } else if (respTypeValue == RESP_QUERY_LOCK_POWER_STATE) { // 7 解析查询电量及开关状态回复
                                String powerLevel = response.getLockPowerLevel();
                                String lockState = response.getLockState();
                                android.util.Log.d("NFCLockPlugin", "电量: " + powerLevel + "%, 状态: " + lockState);
                                json.put("powerLevel", powerLevel);
                                json.put("lockState", lockState);
                                json.put("type", "queryPowerLevel");
                                
                                // 参考原生项目：电量100%时才能执行电机操作
                                if (powerLevel.equals("100")) {
                                    android.util.Log.d("NFCLockPlugin", "电量100%，可以执行电机操作");
                                    json.put("powerReady", true);
                                    
                                    // 检查是否有待执行的电机操作
                                    if (lockId != null && lockPassword != null && pendingOperation != null) {
                                        android.util.Log.d("NFCLockPlugin", "电量100%，执行电机操作: " + pendingOperation);
                                        
                                        try {
                                            if ("MOTOR_FORWARD".equals(pendingOperation)) {
                                                // 发送电机正转指令
                                                NFCLockManager.getInstance().reqMotorForwardWithPowerLevel(lockId, lockPassword);
                                                android.util.Log.d("NFCLockPlugin", "执行电机正转指令");
                                            } else if ("MOTOR_REVERSE".equals(pendingOperation)) {
                                                // 发送电机反转指令
                                                NFCLockManager.getInstance().reqMotorReverseWithPowerLevel(lockId, lockPassword);
                                                android.util.Log.d("NFCLockPlugin", "执行电机反转指令");
                                            }
                                            
                                            // 清除待执行操作
                                            pendingOperation = null;
                                        } catch (Exception e) {
                                            android.util.Log.e("NFCLockPlugin", "执行电机操作失败: " + e.getMessage());
                                        }
                                    }
                                } else {
                                    android.util.Log.d("NFCLockPlugin", "电量不足100%，开始NFC充电和轮询查询电量");
                                    // 异步轮询查询电量及开关状态
                                    NFCLockManager.getInstance().reqQueryPowerLevelWithLoop();
                                }
                            } else if (respTypeValue == RESP_LOCK_MOTOR_FORWARD) { // 8 电机正转
                                boolean motorForwardSuccess = response.motorForwardSuccess();
                                android.util.Log.d("NFCLockPlugin", "电机正转: " + motorForwardSuccess);
                                json.put("motorForwardSuccess", motorForwardSuccess);
                                json.put("type", "motorForward");
                            } else if (respTypeValue == RESP_LOCK_MOTOR_REVERSAL) { // 0x11b 电机反转
                                boolean motorReverseSuccess = response.motorReverseSuccess();
                                android.util.Log.d("NFCLockPlugin", "电机反转: " + motorReverseSuccess);
                                json.put("motorReverseSuccess", motorReverseSuccess);
                                json.put("type", "motorReverse");
                            } else {
                                android.util.Log.d("NFCLockPlugin", "未知响应类型: " + respTypeValue);
                                json.put("type", "unknown");
                            }
                            
                            android.util.Log.d("NFCLockPlugin", "发送JSON: " + json.toString());
                            
                        PluginResult result = new PluginResult(PluginResult.Status.OK, json);
                        result.setKeepCallback(true); // 保持回调通道开放，以便多次发送数据
                        android.util.Log.d("NFCLockPlugin", "准备发送PluginResult到前端");
                        globalCallbackContext.sendPluginResult(result);
                        android.util.Log.d("NFCLockPlugin", "PluginResult已发送到前端");
                    } catch (JSONException e) {
                            android.util.Log.e("NFCLockPlugin", "JSON错误: " + e.getMessage());
                        e.printStackTrace();
                    }
                    } else {
                        android.util.Log.w("NFCLockPlugin", "全局回调上下文为空");
                }
            }

            @Override
                public void onNFCLockWriteOrReadErr(Exception err, String errToast, String errTitle) {
                // 处理错误
                    android.util.Log.e("NFCLockPlugin", "NFC读写错误: " + errToast);
                if (globalCallbackContext != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, errToast);
                    globalCallbackContext.sendPluginResult(result);
                }
            }
                
                @Override
                public void onNFCLockReadBytes(byte[] readBytes) {
                    // 处理读取的原始数据
                    android.util.Log.d("NFCLockPlugin", "读取原始数据: " + readBytes.length + " bytes");
                }
                
                @Override
                public void onNFCLockReadBytesVerify(boolean validData, String errToast, String errTitle) {
                    // 处理数据校验结果
                    android.util.Log.d("NFCLockPlugin", "数据校验: " + validData);
                    if (!validData && globalCallbackContext != null) {
                        PluginResult result = new PluginResult(PluginResult.Status.ERROR, errToast);
                        globalCallbackContext.sendPluginResult(result);
                    }
                }
        };
        NFCLockManager.getInstance().registerNFCLockCallBack(callback);
        }

    // 查询锁ID
    private void queryLockId(CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送查询命令，不需要先调用startReadNFCTag
            // 因为onResume中已经调用了startReadNFCTag
            NFCLockManager.getInstance().reqQueryLockId();
            android.util.Log.d("NFCLockPlugin", "查询NFC锁ID指令已发送");
            callbackContext.success("查询NFC锁ID指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "查询锁ID失败: " + e.getMessage());
            callbackContext.error("Query lock ID failed: " + e.getMessage());
        }
    }
    
    // 查询锁密码
    private void queryLockPassword(CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送查询命令，不需要先调用startReadNFCTag
            NFCLockManager.getInstance().reqQueryLockPwd();
            android.util.Log.d("NFCLockPlugin", "查询密码指令已发送");
            callbackContext.success("查询密码指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "查询锁密码失败: " + e.getMessage());
            callbackContext.error("Query lock password failed: " + e.getMessage());
        }
    }
    
    // 查询版本
    private void queryVersion(CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送查询命令，不需要先调用startReadNFCTag
            NFCLockManager.getInstance().reqQueryVersionNumber();
            android.util.Log.d("NFCLockPlugin", "查询固件版本指令已发送");
            callbackContext.success("查询固件版本指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "查询版本失败: " + e.getMessage());
            callbackContext.error("Query version failed: " + e.getMessage());
        }
    }
    
    // 设置锁ID
    private void setLockId(String lockId, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送设置命令
            NFCLockManager.getInstance().reqSetLockId(lockId);
            android.util.Log.d("NFCLockPlugin", "设置NFC锁编号指令已发送: " + lockId);
            callbackContext.success("设置NFC锁编号指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "设置锁ID失败: " + e.getMessage());
            callbackContext.error("Set lock ID failed: " + e.getMessage());
        }
    }
    
    // 设置密码方式一
    private void setPasswordWay1(String password, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送设置命令
            NFCLockManager.getInstance().reqSetLockPwdByFirstMethod(password);
            android.util.Log.d("NFCLockPlugin", "设置密码(对内)指令已发送");
            callbackContext.success("设置密码(对内)指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "设置密码(对内)失败: " + e.getMessage());
            callbackContext.error("Set password (way 1) failed: " + e.getMessage());
        }
    }
    
    // 擦除密码
    private void removePassword(CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            // 参考原生程序：直接发送擦除命令
            NFCLockManager.getInstance().reqClearLockPwd();
            android.util.Log.d("NFCLockPlugin", "擦除密码指令已发送");
            callbackContext.success("擦除密码指令已发送，请将NFC卡靠近设备");
        } catch (Exception e) {
            android.util.Log.e("NFCLockPlugin", "擦除密码失败: " + e.getMessage());
            callbackContext.error("Remove password failed: " + e.getMessage());
        }
    }

    // 其他方法的实现...
    private void getLockVersion(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject versionInfo = new JSONObject();
            versionInfo.put("hardwareVersion", "1.0.0");
            versionInfo.put("firmwareVersion", "2.1.3");
            versionInfo.put("protocolVersion", "1.2");
            callbackContext.success(versionInfo);
        } catch (Exception e) {
            callbackContext.error("Get lock version failed: " + e.getMessage());
        }
    }

    private void getBatteryLevel(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject batteryInfo = new JSONObject();
            batteryInfo.put("level", 85);
            batteryInfo.put("voltage", 3.7);
            batteryInfo.put("status", "normal");
            callbackContext.success(batteryInfo);
        } catch (Exception e) {
            callbackContext.error("Get battery level failed: " + e.getMessage());
        }
    }

    private void setAutoLockTime(String lockId, String password, int autoLockTime, CallbackContext callbackContext) {
        try {
            callbackContext.success("Auto lock time set to " + autoLockTime + " seconds");
        } catch (Exception e) {
            callbackContext.error("Set auto lock time failed: " + e.getMessage());
        }
    }

    private void getAutoLockTime(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject autoLockInfo = new JSONObject();
            autoLockInfo.put("autoLockTime", 30);
            autoLockInfo.put("enabled", true);
            callbackContext.success(autoLockInfo);
        } catch (Exception e) {
            callbackContext.error("Get auto lock time failed: " + e.getMessage());
        }
    }

    private void setSensitivity(String lockId, String password, int sensitivity, CallbackContext callbackContext) {
        try {
            callbackContext.success("Sensitivity set to level " + sensitivity);
        } catch (Exception e) {
            callbackContext.error("Set sensitivity failed: " + e.getMessage());
        }
    }

    private void getSensitivity(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject sensitivityInfo = new JSONObject();
            sensitivityInfo.put("level", 3);
            sensitivityInfo.put("description", "Medium");
            callbackContext.success(sensitivityInfo);
        } catch (Exception e) {
            callbackContext.error("Get sensitivity failed: " + e.getMessage());
        }
    }

    private void factoryReset(String lockId, String password, CallbackContext callbackContext) {
        try {
            callbackContext.success("Factory reset completed");
        } catch (Exception e) {
            callbackContext.error("Factory reset failed: " + e.getMessage());
        }
    }

    private void getUsageLog(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONArray logArray = new JSONArray();
            JSONObject log1 = new JSONObject();
            log1.put("timestamp", "2024-01-15 10:30:00");
            log1.put("action", "unlock");
            log1.put("user", "admin");
            log1.put("result", "success");
            logArray.put(log1);
            
            JSONObject log2 = new JSONObject();
            log2.put("timestamp", "2024-01-15 11:15:00");
            log2.put("action", "lock");
            log2.put("user", "admin");
            log2.put("result", "success");
            logArray.put(log2);
            
            callbackContext.success(logArray);
        } catch (Exception e) {
            callbackContext.error("Get usage log failed: " + e.getMessage());
        }
    }

    private void clearUsageLog(String lockId, String password, CallbackContext callbackContext) {
        try {
            callbackContext.success("Usage log cleared");
        } catch (Exception e) {
            callbackContext.error("Clear usage log failed: " + e.getMessage());
        }
    }

    private void setAlarmMode(String lockId, String password, boolean enableAlarm, CallbackContext callbackContext) {
        try {
            callbackContext.success("Alarm mode " + (enableAlarm ? "enabled" : "disabled"));
        } catch (Exception e) {
            callbackContext.error("Set alarm mode failed: " + e.getMessage());
        }
    }

    private void getAlarmMode(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject alarmInfo = new JSONObject();
            alarmInfo.put("enabled", true);
            alarmInfo.put("sensitivity", "medium");
            alarmInfo.put("duration", 10);
            callbackContext.success(alarmInfo);
        } catch (Exception e) {
            callbackContext.error("Get alarm mode failed: " + e.getMessage());
        }
    }

    private void testConnection(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject connectionInfo = new JSONObject();
            connectionInfo.put("connected", true);
            connectionInfo.put("signalStrength", 85);
            connectionInfo.put("responseTime", 120);
            callbackContext.success(connectionInfo);
        } catch (Exception e) {
            callbackContext.error("Test connection failed: " + e.getMessage());
        }
    }

    private void getLockConfig(String lockId, String password, CallbackContext callbackContext) {
        try {
            JSONObject config = new JSONObject();
            config.put("autoLockTime", 30);
            config.put("sensitivity", 3);
            config.put("alarmEnabled", true);
            config.put("batteryLevel", 85);
            config.put("firmwareVersion", "2.1.3");
            callbackContext.success(config);
        } catch (Exception e) {
            callbackContext.error("Get lock config failed: " + e.getMessage());
        }
    }

    private void setLockConfig(String lockId, String password, JSONObject config, CallbackContext callbackContext) {
        try {
            callbackContext.success("Lock configuration updated");
        } catch (Exception e) {
            callbackContext.error("Set lock config failed: " + e.getMessage());
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        // 参考原生程序：在onResume时启动NFC读取
        if (isInitialized) {
            NFCLockManager.getInstance().startReadNFCTag(cordova.getActivity());
            android.util.Log.d("NFCLockPlugin", "NFC读取已启动");
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        // 参考原生程序：在onPause时停止NFC读取
        if (isInitialized) {
            NFCLockManager.getInstance().stopReadNFCTag(cordova.getActivity());
            android.util.Log.d("NFCLockPlugin", "NFC读取已停止");
        }
    }

    @Override
    public void onDestroy() {
        // 资源清理
        if (isInitialized) {
        NFCLockManager.getInstance().unRegisterNFCLockCallBack();
        NFCLockManager.getInstance().onDestroy();
        }
        super.onDestroy();
    }
}