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

import android.content.Context;
import android.content.SharedPreferences;

import android.nfc.Tag;

public class NFCLockPlugin extends CordovaPlugin {

    private CallbackContext globalCallbackContext;
    private String lockId;
    private String lockPassword;
    private boolean isNFCSupported = false;
    private boolean isNFCEnabled = false;
    private boolean isInitialized = false;
    private String pendingOperation = null; // 跟踪待执行的操作
    private boolean autoFlowRunning = false;
    private String autoFlowMotorAction = null;
    private boolean manualFlowRunning = false;
    private String manualFlowMotorAction = null;
    private String cachedLockPassword = null;
    private String cachedLockId = null;
    private static final String DEFAULT_LOCK_PASSWORD = "D7UOebAQ";
    private static final String PREFS_NAME = "NFCLockPluginPrefs";
    private static final String PREF_CACHED_LOCK_PASSWORD = "cached_lock_password";
    private static final String PREF_CACHED_LOCK_ID = "cached_lock_id";
    private static final String PREF_AUTO_TOGGLE_COUNT = "auto_toggle_count";
    private static final int AUTO_FLOW_TIMEOUT_MS = 60000;
    private static final int MANUAL_FLOW_TIMEOUT_MS = 30000;
    private static final int AUTO_FLOW_CHARGE_TIMEOUT_MS = 12000;
    private static final int MOTOR_RESPONSE_TIMEOUT_MS = 8000;
    private static final int READ_SESSION_DELAY_MS = 0;
    private static final int AUTO_FLOW_STEP_DELAY_MS = 0;
    private static final int CHARGE_SESSION_WARMUP_MS = 80;
    private static final int MAX_FLOW_CHARGE_ERROR_RETRIES = 3;
    private static final int MAX_FLOW_LOCK_ID_RETRIES = 1;
    private static final int FLOW_ARM_NONE = 0;
    private static final int FLOW_ARM_QUERY_LOCK_ID = 1;
    private static final int FLOW_ARM_QUERY_POWER = 2;
    private static final int FLOW_ARM_QUERY_PWD = 3;
    private final android.os.Handler autoFlowHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoFlowTimeoutRunnable = null;
    private Runnable autoFlowChargeTimeoutRunnable = null;
    private Runnable manualFlowTimeoutRunnable = null;
    private Runnable motorResponseTimeoutRunnable = null;
    private boolean awaitingMotorResponse = false;
    private boolean awaitingMotorStateVerify = false;
    private boolean motorSucceededPendingVerify = false;
    private String lockStateBeforeMotor = null;
    private String lastKnownLockState = null;
    private long autoFlowStartMs = 0L;
    private long manualFlowStartMs = 0L;
    private long autoFlowChargeStartMs = 0L;
    private long flowChargeStartMs = 0L;
    private long flowChargeDurationMs = -1L;
    private int autoFlowChargePollCount = 0;
    private int lastNotifiedChargePercent = -1;
    private long lastFlowMotorCompleteMs = 0L;
    private int flowChargeErrorRetries = 0;
    private int flowArmedCommand = FLOW_ARM_NONE;
    private boolean tagConnectedInFlow = false;
    private boolean chargePhaseNotified = false;
    private long autoFlowLastStepMs = 0L;
    private long manualFlowLastStepMs = 0L;
    private org.json.JSONArray autoFlowStepTimings = new org.json.JSONArray();
    private org.json.JSONArray manualFlowStepTimings = new org.json.JSONArray();
    private boolean autoFlowAwaitingPassword = false;
    private int manualFlowLockIdRetries = 0;
    private boolean manualFlowRereadLockId = false;
    private int autoFlowLockIdRetries = 0;
    private boolean autoFlowRereadLockId = false;
    private final NFCLockSoundHelper nfcSoundHelper = new NFCLockSoundHelper();
    private final NFCLockChargeFeedback chargeFeedback = new NFCLockChargeFeedback();
    
    // 响应类型常量（使用实际SDK返回的数值）
    private static final int RESP_QUERY_LOCK_ID = 0; // 查询NFC锁编号指令回复
    private static final int RESP_SETTING_LOCK_ID = 1; // 设置NFC锁编号回复
    private static final int RESP_QUERY_LOCK_PWD = 2; // 查询NFC锁密码回复
    private static final int RESP_SETTING_LOCK_PWD_FIRST = 3; // 设置NFC锁密码方式一回复
    private static final int RESP_SETTING_LOCK_PWD_SECOND = 4; // 设置NFC锁密码方式二回复
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
        } else if ("autoToggleLock".equals(action)) {
            this.autoToggleLock(callbackContext);
            return true;
        } else if ("manualOpenLock".equals(action)) {
            this.manualOpenLock(callbackContext);
            return true;
        } else if ("manualCloseLock".equals(action)) {
            this.manualCloseLock(callbackContext);
            return true;
        }
        return false;
    }

    private void init(final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isInitialized) {
                        performInitCore();
                    }
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

    private void performInitCore() throws Exception {
        NFCLockManager.init(cordova.getActivity().getApplicationContext());
        android.util.Log.d("NFCLockPlugin", "NFC SDK初始化完成");

        loadCachedLockPassword();
        loadCachedLockId();

        setupNFCCallback();

        isNFCSupported = NFCLockManager.isSupportNFC();
        android.util.Log.d("NFCLockPlugin", "设备支持NFC: " + isNFCSupported);

        isNFCEnabled = NFCLockManager.isNFCEnabled();
        android.util.Log.d("NFCLockPlugin", "NFC已启用: " + isNFCEnabled);

        isInitialized = true;

        nfcSoundHelper.init(cordova.getActivity().getApplicationContext());

        NFCLockManager.getInstance().startReadNFCTag(cordova.getActivity());
        android.util.Log.d("NFCLockPlugin", "NFC读取已启动");
    }

    private void ensureInitializedThen(final CallbackContext callbackContext, final Runnable action) {
        if (isInitialized) {
            action.run();
            return;
        }
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isInitialized) {
                        performInitCore();
                        android.util.Log.d("NFCLockPlugin", "操作前自动初始化完成");
                    }
                    action.run();
                } catch (Exception e) {
                    android.util.Log.e("NFCLockPlugin", "自动初始化失败: " + e.getMessage());
                    callbackContext.error("插件自动初始化失败: " + e.getMessage());
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
            
            restartReadSessionAndExecute(new Runnable() {
                @Override
                public void run() {
                    NFCLockManager.getInstance().reqQueryPowerLevel(NFCLockManager.QueryPowerLevelType.QUERY_MOTOR_FORWARD);
                    android.util.Log.d("NFCLockPlugin", "开锁流程已触发（重置读卡会话后）");
                }
            });
            
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
            
            restartReadSessionAndExecute(new Runnable() {
                @Override
                public void run() {
                    NFCLockManager.getInstance().reqQueryPowerLevel(NFCLockManager.QueryPowerLevelType.QUERY_MOTOR_REVERSE);
                    android.util.Log.d("NFCLockPlugin", "关锁流程已触发（重置读卡会话后）");
                }
            });
            
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
            restartReadSessionAndExecute(new Runnable() {
                @Override
                public void run() {
                    NFCLockManager.getInstance().reqQueryPowerLevel();
                    android.util.Log.d("NFCLockPlugin", "查询状态已触发（重置读卡会话后）");
                }
            });
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

    private int resolveRespTypeValue(NFCLockResponse response) {
        try {
            Object respType = response.getRespCmdType();
            if (respType instanceof Number) {
                return ((Number) respType).intValue();
            }
            try {
                java.lang.reflect.Method valueMethod = respType.getClass().getMethod("value");
                return (Integer) valueMethod.invoke(respType);
            } catch (Exception e1) {
                java.lang.reflect.Method ordinalMethod = respType.getClass().getMethod("ordinal");
                return (Integer) ordinalMethod.invoke(respType);
            }
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "无法解析响应类型: " + e.getMessage());
            return -1;
        }
    }

    private boolean isMotorRespType(int respTypeValue) {
        return respTypeValue == RESP_LOCK_MOTOR_FORWARD
                || respTypeValue == RESP_LOCK_MOTOR_REVERSAL;
    }

    private JSONObject buildGenericResponseJson(int respTypeValue, NFCLockResponse response) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("commandId", response.getCommandId());
        json.put("lockId", response.getLockID());
        json.put("success", response.operateSuccess());
        json.put("respCmdType", response.getRespCmdType());

        switch (respTypeValue) {
            case RESP_QUERY_LOCK_ID: {
                String id = response.getLockID();
                json.put("lockId", id != null ? id : "");
                json.put("type", "queryLockId");
                break;
            }
            case RESP_SETTING_LOCK_ID: {
                json.put("lockId", response.getLockID());
                json.put("type", "setLockId");
                break;
            }
            case RESP_QUERY_LOCK_PWD: {
                json.put("lockPassword", response.getLockPwd());
                json.put("type", "queryLockPassword");
                json.put("respCmdType", "RESP_QUERY_LOCK_PWD");
                break;
            }
            case RESP_SETTING_LOCK_PWD_FIRST: {
                json.put("passwordSet", response.operateSuccess());
                json.put("type", "setPasswordWay1");
                break;
            }
            case RESP_SETTING_LOCK_PWD_SECOND: {
                json.put("passwordSet", response.operateSuccess());
                json.put("type", "setPasswordWay2");
                break;
            }
            case RESP_CLEAN_LOCK_PWD: {
                json.put("passwordCleared", response.operateSuccess());
                json.put("type", "removePassword");
                break;
            }
            case RESP_QUERY_LOCK_VERSION_NAME: {
                String lockVersionName = response.getLockVersionName();
                json.put("versionName", lockVersionName != null ? lockVersionName : "");
                json.put("type", "queryVersion");
                break;
            }
            case RESP_QUERY_LOCK_POWER_STATE: {
                String powerLevel = response.getLockPowerLevel();
                String lockState = response.getLockState();
                json.put("powerLevel", powerLevel);
                json.put("lockState", lockState);
                json.put("type", "queryPowerLevel");
                if (lockState != null && !lockState.isEmpty()) {
                    lastKnownLockState = lockState;
                }
                break;
            }
            case RESP_LOCK_MOTOR_FORWARD: {
                json.put("motorForwardSuccess", response.motorForwardSuccess());
                json.put("type", "motorForward");
                break;
            }
            case RESP_LOCK_MOTOR_REVERSAL: {
                json.put("motorReverseSuccess", response.motorReverseSuccess());
                json.put("type", "motorReverse");
                break;
            }
            default:
                json.put("type", "unknown");
                break;
        }
        return json;
    }

    private void setupNFCCallback() {
        NFCLockCallBack callback = new NFCLockCallBack() {
            @Override
            public void onNFCLockResponse(NFCLockResponse response) {
                if (globalCallbackContext == null) {
                    android.util.Log.w("NFCLockPlugin", "全局回调上下文为空");
                    return;
                }
                try {
                    int respTypeValue = resolveRespTypeValue(response);
                    final boolean suppressMotorGenericCallback = isMotorRespType(respTypeValue)
                            && (autoFlowRunning || manualFlowRunning || awaitingMotorResponse);

                    handleAutoFlowStep(respTypeValue, response);
                    handleManualFlowStep(respTypeValue, response);
                    cacheLockPasswordFromResponse(respTypeValue, response);

                    if (shouldSuppressGenericCallback(respTypeValue, suppressMotorGenericCallback)) {
                        return;
                    }

                    JSONObject json = buildGenericResponseJson(respTypeValue, response);
                    if (json == null) {
                        return;
                    }
                    PluginResult result = new PluginResult(PluginResult.Status.OK, json);
                    result.setKeepCallback(true);
                    globalCallbackContext.sendPluginResult(result);
                } catch (JSONException e) {
                    android.util.Log.e("NFCLockPlugin", "JSON错误: " + e.getMessage());
                }
            }
            @Override
                public void onNFCLockWriteOrReadErr(Exception err, String errToast, String errTitle) {
                // 处理错误
                    android.util.Log.e("NFCLockPlugin", "NFC读写错误: " + errToast);
                    android.util.Log.e("NFCLockPlugin", "错误标题: " + errTitle);
                    if (err != null) {
                        android.util.Log.e("NFCLockPlugin", "异常详情: " + err.getMessage());
                        err.printStackTrace();
                    }
                if (globalCallbackContext != null) {
                    if (awaitingMotorResponse) {
                        android.util.Log.w("NFCLockPlugin", "电机响应等待中，忽略NFC错误: " + errToast);
                        return;
                    }
                    if (awaitingMotorStateVerify && motorSucceededPendingVerify) {
                        android.util.Log.w("NFCLockPlugin", "电机后验查询失败，电机已成功，忽略: " + errToast);
                        completeMotorFlowAfterSuccess(autoFlowRunning);
                        return;
                    }
                    if (shouldRetryChargePoll(errToast)) {
                        return;
                    }
                    if (autoFlowRunning) {
                        sendAutoFlowFail(errToast != null ? errToast : "NFC读写错误");
                    } else if (manualFlowRunning) {
                        sendManualFlowFail(errToast != null ? errToast : "NFC读写错误");
                    } else {
                        android.util.Log.w("NFCLockPlugin", "流程已结束，忽略NFC错误: " + errToast);
                        return;
                    }
                    try {
                        JSONObject errorJson = new JSONObject();
                        errorJson.put("success", false);
                        errorJson.put("message", errToast);
                        errorJson.put("title", errTitle != null ? errTitle : "NFC错误");
                        errorJson.put("type", "error");
                        
                        PluginResult result = new PluginResult(PluginResult.Status.ERROR, errorJson);
                        result.setKeepCallback(true);
                        globalCallbackContext.sendPluginResult(result);
                    } catch (JSONException e) {
                        PluginResult result = new PluginResult(PluginResult.Status.ERROR, errToast);
                        globalCallbackContext.sendPluginResult(result);
                    }
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
                    android.util.Log.d("NFCLockPlugin", "数据校验: " + validData + ", 错误: " + errToast);
                    if (!validData && globalCallbackContext != null) {
                        try {
                            JSONObject errorJson = new JSONObject();
                            errorJson.put("success", false);
                            errorJson.put("message", errToast);
                            errorJson.put("title", errTitle != null ? errTitle : "数据校验失败");
                            errorJson.put("type", "verify_error");
                            
                            android.util.Log.e("NFCLockPlugin", "数据校验失败，发送错误: " + errorJson.toString());
                            PluginResult result = new PluginResult(PluginResult.Status.ERROR, errorJson);
                            result.setKeepCallback(true);
                            globalCallbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            PluginResult result = new PluginResult(PluginResult.Status.ERROR, errToast);
                            result.setKeepCallback(true);
                            globalCallbackContext.sendPluginResult(result);
                        }
                    }
                }

                @Override
                public void onTagDiscovered(Tag tag) {
                    android.util.Log.d("NFCLockPlugin", "NFC标签已发现: " + (tag != null ? tag.toString() : "null"));
                    nfcSoundHelper.play(NFCLockSoundHelper.SOUND_NFC);
                    if ((manualFlowRunning || autoFlowRunning) && flowArmedCommand != FLOW_ARM_NONE) {
                        android.util.Log.d("NFCLockPlugin", "流程已贴卡，等待SDK读写 arm=" + flowArmedCommand);
                    }
                }
        };
        NFCLockManager.getInstance().registerNFCLockCallBack(callback);
        }

    // 重新激活读卡会话，避免连续指令时后续指令无回包
    private void restartReadSessionAndExecute(final Runnable command) {
        try {
            NFCLockManager.getInstance().stopReadNFCTag(cordova.getActivity());
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "stopReadNFCTag忽略异常: " + e.getMessage());
        }

        try {
            NFCLockManager.getInstance().startReadNFCTag(cordova.getActivity());
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "startReadNFCTag忽略异常: " + e.getMessage());
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                command.run();
                try {
                    NFCLockManager.getNfcOperateUtils().writeAndReadDataByTag();
                } catch (Exception e) {
                    android.util.Log.e("NFCLockPlugin", "NFC写入失败: " + e.getMessage());
                }
            }
        }, READ_SESSION_DELAY_MS);
    }

    private void runAutoFlowCommand(final Runnable command) {
        autoFlowHandler.postAtFrontOfQueue(command);
    }

    /** 参考 xhky control：链式指令在 req* 后必须 writeAndReadDataByTag，否则贴卡期间后续指令不会下发 */
    private void runNfcWriteCommand(final Runnable command) {
        autoFlowHandler.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                command.run();
                try {
                    NFCLockManager.getNfcOperateUtils().writeAndReadDataByTag();
                } catch (Exception e) {
                    android.util.Log.e("NFCLockPlugin", "NFC写入失败: " + e.getMessage());
                }
            }
        });
    }

    private void startAutoFlowSession(final Runnable command) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    NFCLockManager.getInstance().startReadNFCTag(cordova.getActivity());
                } catch (Exception e) {
                    android.util.Log.w("NFCLockPlugin", "startReadNFCTag忽略异常: " + e.getMessage());
                }
                command.run();
            }
        });
    }

    /** 结束上一轮 NFC 异步读写，避免连续流程时 reader 未就绪 */
    private void resetNfcSession() {
        try {
            NFCLockManager.getInstance().resetNfcStatus();
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "resetNfcStatus忽略异常: " + e.getMessage());
        }
    }

    /** 标记开始充电时刻（开始充电 → 充电完成） */
    private void markFlowChargeStarted() {
        if (flowChargeStartMs > 0L) {
            return;
        }
        flowChargeStartMs = System.currentTimeMillis();
        autoFlowChargeStartMs = flowChargeStartMs;
        flowChargeDurationMs = -1L;
        android.util.Log.d("NFCLockPlugin", "开始充电计时");
    }

    private void finalizeFlowChargeDuration() {
        if (flowChargeDurationMs >= 0L) {
            return;
        }
        if (flowChargeStartMs > 0L) {
            flowChargeDurationMs = Math.max(0L, System.currentTimeMillis() - flowChargeStartMs);
        } else {
            flowChargeDurationMs = 0L;
        }
        android.util.Log.d("NFCLockPlugin", "充电总耗时: " + flowChargeDurationMs + "ms");
    }

    private void resetFlowChargeTiming() {
        flowChargeStartMs = 0L;
        flowChargeDurationMs = -1L;
        autoFlowChargeStartMs = 0L;
    }

    private void resetNfcAfterFlow() {
        resetNfcSession();
        tagConnectedInFlow = false;
        flowArmedCommand = FLOW_ARM_NONE;
        ensureReaderModeActive();
    }

    private void ensureReaderModeActive() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    NFCLockManager.getInstance().startReadNFCTag(cordova.getActivity());
                } catch (Exception e) {
                    android.util.Log.w("NFCLockPlugin", "startReadNFCTag忽略异常: " + e.getMessage());
                }
            }
        });
    }

    private void markTagConnectedInFlow() {
        tagConnectedInFlow = true;
        flowArmedCommand = FLOW_ARM_NONE;
    }

    private boolean isTagReaderReady() {
        try {
            return NFCLockManager.getNfcOperateUtils().isReady();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryImmediateQueryLockId() {
        if (!isTagReaderReady()) {
            return false;
        }
        markTagConnectedInFlow();
        runNfcWriteCommand(new Runnable() {
            @Override
            public void run() {
                NFCLockManager.getInstance().reqQueryLockId();
                android.util.Log.d("NFCLockPlugin", "换锁重读：会话内立即查询锁ID");
            }
        });
        return true;
    }

    private void dispatchRereadLockIdOrWaitTag() {
        if (tryImmediateQueryLockId()) {
            return;
        }
        autoFlowHandler.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                if (!manualFlowRunning && !autoFlowRunning) {
                    return;
                }
                if (tryImmediateQueryLockId()) {
                    return;
                }
                tagConnectedInFlow = false;
                waitForTagAndArm(FLOW_ARM_QUERY_LOCK_ID);
                android.util.Log.d("NFCLockPlugin", "换锁重读：reader未就绪，等待贴卡");
            }
        });
    }

    /**
     * 参考 xhky：用户点击后先武装指令(req*)，等贴卡时 SDK 在 onTagDiscovered 里 writeAndRead。
     * 每次操作后手机会离开锁，不能立即 WithLoop。
     */
    private void waitForTagAndArm(final int armType) {
        resetNfcSession();
        flowArmedCommand = armType;
        tagConnectedInFlow = false;
        ensureReaderModeActive();
        autoFlowHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!manualFlowRunning && !autoFlowRunning) {
                    return;
                }
                switch (armType) {
                    case FLOW_ARM_QUERY_LOCK_ID:
                        NFCLockManager.getInstance().reqQueryLockId();
                        android.util.Log.d("NFCLockPlugin", "已武装查询锁ID，等待贴卡");
                        break;
                    case FLOW_ARM_QUERY_PWD:
                        NFCLockManager.getInstance().reqQueryLockPwd();
                        android.util.Log.d("NFCLockPlugin", "已武装查询密码，等待贴卡");
                        break;
                    case FLOW_ARM_QUERY_POWER:
                    default:
                        NFCLockManager.getInstance().reqQueryPowerLevel();
                        android.util.Log.d("NFCLockPlugin", "已武装查询电量，等待贴卡");
                        break;
                }
            }
        });
    }

    private boolean isPowerQueryError(String errToast) {
        return errToast != null
                && (errToast.contains("查询电量") || errToast.contains("电量及开关状态"));
    }

    private boolean shouldRetryChargePoll(String errToast) {
        if (!isPowerQueryError(errToast)) {
            return false;
        }
        if ((!autoFlowRunning && !manualFlowRunning) || pendingOperation == null || awaitingMotorResponse) {
            return false;
        }
        if (flowChargeErrorRetries >= MAX_FLOW_CHARGE_ERROR_RETRIES) {
            return false;
        }
        flowChargeErrorRetries++;
        android.util.Log.w("NFCLockPlugin", "电量查询失败，等待重新贴卡重试 " + flowChargeErrorRetries
                + "/" + MAX_FLOW_CHARGE_ERROR_RETRIES);
        tagConnectedInFlow = false;
        waitForTagAndArm(FLOW_ARM_QUERY_POWER);
        return true;
    }

    // 查询锁ID
    private void queryLockId(CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("插件未初始化");
            return;
        }
        
        try {
            restartReadSessionAndExecute(new Runnable() {
                @Override
                public void run() {
                    NFCLockManager.getInstance().reqQueryLockId();
                    android.util.Log.d("NFCLockPlugin", "查询锁ID已触发（重置读卡会话后）");
                }
            });
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
            restartReadSessionAndExecute(new Runnable() {
                @Override
                public void run() {
                    NFCLockManager.getInstance().reqQueryLockPwd();
                    android.util.Log.d("NFCLockPlugin", "查询锁密码已触发（重置读卡会话后）");
                }
            });
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

    private void autoToggleLock(final CallbackContext callbackContext) {
        ensureInitializedThen(callbackContext, new Runnable() {
            @Override
            public void run() {
                startAutoToggleLockFlow(callbackContext);
            }
        });
    }

    private void startAutoToggleLockFlow(final CallbackContext callbackContext) {
        if (autoFlowRunning) {
            callbackContext.error("自动流程执行中");
            return;
        }

        autoFlowRunning = true;
        autoFlowMotorAction = null;
        pendingOperation = null;
        awaitingMotorResponse = false;
        awaitingMotorStateVerify = false;
        motorSucceededPendingVerify = false;
        lockStateBeforeMotor = null;
        cancelMotorResponseTimeout();
        autoFlowChargeStartMs = 0L;
        autoFlowChargePollCount = 0;
        lastNotifiedChargePercent = -1;
        flowChargeErrorRetries = 0;
        tagConnectedInFlow = false;
        flowArmedCommand = FLOW_ARM_NONE;
        chargePhaseNotified = false;
        cancelAutoFlowChargeTimeout();
        chargeFeedback.reset();
        autoFlowStartMs = 0L;
        resetFlowChargeTiming();
        resetAutoFlowStepTimings();
        autoFlowLockIdRetries = 0;
        autoFlowRereadLockId = false;

        String knownLockId = getCachedLockId();
        String knownPassword = getEffectiveLockPassword();
        if (knownLockId != null && !knownLockId.isEmpty()
                && knownPassword != null && !knownPassword.isEmpty()) {
            lockId = knownLockId;
            lockPassword = knownPassword;
            autoFlowAwaitingPassword = false;
            sendAutoFlowProgress(1, "请贴卡，自动流程开始");
            scheduleAutoFlowTimeout("贴卡超时");
            waitForTagAndArm(FLOW_ARM_QUERY_POWER);
            callbackContext.success("自动流程已启动");
            return;
        }

        lockId = null;
        lockPassword = null;
        autoFlowAwaitingPassword = true;
        sendAutoFlowProgress(1, "请贴卡，正在查询锁ID");
        scheduleAutoFlowTimeout("贴卡超时");
        waitForTagAndArm(FLOW_ARM_QUERY_LOCK_ID);
        callbackContext.success("自动流程已启动");
    }

    private void loadCachedLockPassword() {
        try {
            SharedPreferences prefs = getPrefs();
            String saved = prefs.getString(PREF_CACHED_LOCK_PASSWORD, null);
            if (saved != null && !saved.isEmpty()) {
                cachedLockPassword = saved;
                android.util.Log.d("NFCLockPlugin", "已加载持久化锁密码");
            }
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "加载锁密码失败: " + e.getMessage());
        }
    }

    private void loadCachedLockId() {
        try {
            SharedPreferences prefs = getPrefs();
            String saved = prefs.getString(PREF_CACHED_LOCK_ID, null);
            if (saved != null && !saved.isEmpty()) {
                cachedLockId = saved;
                android.util.Log.d("NFCLockPlugin", "已加载持久化锁ID");
            }
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "加载锁ID失败: " + e.getMessage());
        }
    }

    private String getCachedLockId() {
        if (cachedLockId != null && !cachedLockId.isEmpty()) {
            return cachedLockId;
        }
        if (lockId != null && !lockId.isEmpty()) {
            return lockId;
        }
        return null;
    }

    private void cacheLockId(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        cachedLockId = id;
        lockId = id;
        try {
            getPrefs().edit().putString(PREF_CACHED_LOCK_ID, id).apply();
            android.util.Log.d("NFCLockPlugin", "锁ID已缓存并持久化");
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "持久化锁ID失败: " + e.getMessage());
        }
    }

    private void clearCachedLockId() {
        cachedLockId = null;
        try {
            getPrefs().edit().remove(PREF_CACHED_LOCK_ID).apply();
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "清除锁ID缓存失败: " + e.getMessage());
        }
    }

    private SharedPreferences getPrefs() {
        return cordova.getActivity().getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 手动流程：优先用自动流程缓存密码，否则默认密码 */
    private String getEffectiveLockPassword() {
        if (cachedLockPassword != null && !cachedLockPassword.isEmpty()) {
            return cachedLockPassword;
        }
        return DEFAULT_LOCK_PASSWORD;
    }

    private void cacheLockPassword(String password) {
        if (password == null || password.isEmpty()) {
            return;
        }
        cachedLockPassword = password;
        try {
            getPrefs().edit().putString(PREF_CACHED_LOCK_PASSWORD, password).apply();
            android.util.Log.d("NFCLockPlugin", "锁密码已缓存并持久化");
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "持久化锁密码失败: " + e.getMessage());
        }
    }

    private void cacheLockPasswordFromResponse(int respTypeValue, NFCLockResponse response) {
        if (respTypeValue == RESP_QUERY_LOCK_PWD) {
            cacheLockPassword(response.getLockPwd());
        }
    }

    private void manualOpenLock(final CallbackContext callbackContext) {
        ensureInitializedThen(callbackContext, new Runnable() {
            @Override
            public void run() {
                startManualLockFlow(callbackContext, "open", "MOTOR_FORWARD");
            }
        });
    }

    private void manualCloseLock(final CallbackContext callbackContext) {
        ensureInitializedThen(callbackContext, new Runnable() {
            @Override
            public void run() {
                startManualLockFlow(callbackContext, "close", "MOTOR_REVERSE");
            }
        });
    }

    private void startManualLockFlow(final CallbackContext callbackContext, final String motorAction, final String pendingOp) {
        if (autoFlowRunning) {
            callbackContext.error("自动流程执行中");
            return;
        }
        if (manualFlowRunning) {
            callbackContext.error("手动流程执行中");
            return;
        }

        manualFlowRunning = true;
        manualFlowMotorAction = motorAction;
        pendingOperation = pendingOp;
        awaitingMotorResponse = false;
        awaitingMotorStateVerify = false;
        motorSucceededPendingVerify = false;
        lockStateBeforeMotor = null;
        cancelMotorResponseTimeout();
        lockPassword = getEffectiveLockPassword();
        android.util.Log.d("NFCLockPlugin", "手动流程使用密码: "
                + (cachedLockPassword != null && !cachedLockPassword.isEmpty() ? "缓存" : "默认"));
        autoFlowChargeStartMs = 0L;
        autoFlowChargePollCount = 0;
        lastNotifiedChargePercent = -1;
        flowChargeErrorRetries = 0;
        tagConnectedInFlow = false;
        flowArmedCommand = FLOW_ARM_NONE;
        chargePhaseNotified = false;
        cancelAutoFlowChargeTimeout();
        chargeFeedback.reset();
        manualFlowStartMs = 0L;
        resetFlowChargeTiming();
        resetManualFlowStepTimings();
        manualFlowLockIdRetries = 0;
        manualFlowRereadLockId = false;

        String knownLockId = getCachedLockId();
        if (knownLockId != null && !knownLockId.isEmpty()) {
            lockId = knownLockId;
            sendManualFlowProgress(1, "open".equals(motorAction)
                    ? "请贴卡开锁，贴卡后请勿移开直至完成" : "请贴卡关锁，贴卡后请勿移开直至完成");
            scheduleManualFlowTimeout("贴卡超时");
            startPowerLevelPollingForMotor();
            callbackContext.success("手动流程已启动");
            return;
        }

        lockId = null;
        sendManualFlowProgress(1, "open".equals(motorAction)
                ? "请贴卡查询锁ID并开锁" : "请贴卡查询锁ID并关锁");
        scheduleManualFlowTimeout("贴卡超时");
        waitForTagAndArm(FLOW_ARM_QUERY_LOCK_ID);
        callbackContext.success("手动流程已启动");
    }

    private void handleManualFlowStep(int respTypeValue, NFCLockResponse response) {
        if (!manualFlowRunning) {
            return;
        }
        switch (respTypeValue) {
            case RESP_QUERY_LOCK_ID: {
                if (lockId != null && !lockId.isEmpty() && !manualFlowRereadLockId) {
                    return;
                }
                manualFlowRereadLockId = false;
                String id = response.getLockID();
                if (id == null || id.isEmpty()) {
                    sendManualFlowFail("未读取到锁ID，请重新贴卡");
                    return;
                }
                lockId = id;
                cacheLockId(id);
                markTagConnectedInFlow();
                lockPassword = getEffectiveLockPassword();
                cancelManualFlowTimeout();
                sendManualFlowProgress(2, "query_lock_id", "查询锁ID",
                        "锁ID已获取，请保持贴卡"
                        + ("open".equals(manualFlowMotorAction) ? "开锁" : "关锁"));
                scheduleManualFlowTimeout("电机操作超时");
                startPowerLevelPollingForMotor();
                break;
            }
            case RESP_LOCK_MOTOR_FORWARD:
            case RESP_LOCK_MOTOR_REVERSAL: {
                handleMotorCommandResponse(respTypeValue, response, false);
                break;
            }
            case RESP_QUERY_LOCK_POWER_STATE:
                if (awaitingMotorStateVerify) {
                    verifyMotorStateAfterCommand(response, false);
                    break;
                }
                handleFlowPowerLevelResponse(response, false);
                break;
            default:
                break;
        }
    }

    private void scheduleManualFlowTimeout(final String reason) {
        cancelManualFlowTimeout();
        manualFlowTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (manualFlowRunning) {
                    if (awaitingMotorStateVerify && motorSucceededPendingVerify) {
                        completeMotorFlowAfterSuccess(false);
                    } else {
                        sendManualFlowFail(reason + "，请重新贴卡");
                    }
                }
            }
        };
        autoFlowHandler.postDelayed(manualFlowTimeoutRunnable, MANUAL_FLOW_TIMEOUT_MS);
    }

    private void cancelManualFlowTimeout() {
        if (manualFlowTimeoutRunnable != null) {
            autoFlowHandler.removeCallbacks(manualFlowTimeoutRunnable);
            manualFlowTimeoutRunnable = null;
        }
    }

    private void sendManualFlowProgress(int step, String message) {
        sendManualFlowProgress(step, null, null, message);
    }

    private void sendManualFlowProgress(int step, String stepKey, String stepLabel, String message) {
        sendManualFlowEvent("manualFlowProgress", step, message, null);
    }

    private void sendManualFlowComplete(boolean success, String message, String motorAction) {
        manualFlowRunning = false;
        awaitingMotorResponse = false;
        awaitingMotorStateVerify = false;
        motorSucceededPendingVerify = false;
        pendingOperation = null;
        flowChargeErrorRetries = 0;
        cancelManualFlowTimeout();
        cancelAutoFlowChargeTimeout();
        chargeFeedback.stop();
        cancelMotorResponseTimeout();
        awaitingMotorResponse = false;
        if (success) {
            nfcSoundHelper.play(NFCLockSoundHelper.SOUND_SUCCESS);
        } else {
            nfcSoundHelper.play(NFCLockSoundHelper.SOUND_ERROR);
        }
        sendManualFlowEvent("manualFlowComplete", 3, message, success, motorAction);
        resetNfcAfterFlow();
    }

    private void sendManualFlowFail(String message) {
        sendManualFlowComplete(false, message, null);
    }

    private String resolveManualPendingOperation() {
        if ("open".equals(manualFlowMotorAction)) {
            return "MOTOR_FORWARD";
        }
        if ("close".equals(manualFlowMotorAction)) {
            return "MOTOR_REVERSE";
        }
        return null;
    }

    /** 换锁或缓存锁ID失效：清除缓存并重新贴卡读ID后继续原开/关锁意图 */
    private void retryManualFlowWithFreshLockId(String reason) {
        manualFlowLockIdRetries++;
        clearCachedLockId();
        lockId = null;
        manualFlowRereadLockId = true;
        awaitingMotorResponse = false;
        awaitingMotorStateVerify = false;
        motorSucceededPendingVerify = false;
        lockStateBeforeMotor = null;
        cancelMotorResponseTimeout();
        cancelAutoFlowChargeTimeout();
        chargeFeedback.stop();
        chargePhaseNotified = false;
        resetFlowChargeTiming();
        flowChargeErrorRetries = 0;
        flowArmedCommand = FLOW_ARM_NONE;
        pendingOperation = resolveManualPendingOperation();
        lockPassword = getEffectiveLockPassword();
        android.util.Log.w("NFCLockPlugin", "手动流程失败(" + reason + ")，重新读取锁ID，重试 "
                + manualFlowLockIdRetries + "/" + MAX_FLOW_LOCK_ID_RETRIES);
        String actionText = "open".equals(manualFlowMotorAction) ? "开锁" : "关锁";
        cancelManualFlowTimeout();
        if (isTagReaderReady()) {
            sendManualFlowProgress(1, "正在重新读取锁ID并" + actionText);
        } else {
            sendManualFlowProgress(1, "锁ID可能已变更，请重新贴卡读取锁ID并" + actionText);
        }
        scheduleManualFlowTimeout("贴卡超时");
        dispatchRereadLockIdOrWaitTag();
    }

    private void failManualFlowOrRetryLockId(String reason) {
        if (manualFlowRunning && manualFlowLockIdRetries < MAX_FLOW_LOCK_ID_RETRIES) {
            retryManualFlowWithFreshLockId(reason);
            return;
        }
        sendManualFlowFail(reason);
    }

    private String resolveAutoPendingOperation() {
        if ("open".equals(autoFlowMotorAction)) {
            return "MOTOR_FORWARD";
        }
        if ("close".equals(autoFlowMotorAction)) {
            return "MOTOR_REVERSE";
        }
        return null;
    }

    /** 换锁或缓存锁ID失效：自动流程重新贴卡读ID/密码后继续 */
    private void retryAutoFlowWithFreshLockId(String reason) {
        autoFlowLockIdRetries++;
        clearCachedLockId();
        lockId = null;
        lockPassword = null;
        autoFlowRereadLockId = true;
        autoFlowAwaitingPassword = true;
        awaitingMotorResponse = false;
        awaitingMotorStateVerify = false;
        motorSucceededPendingVerify = false;
        lockStateBeforeMotor = null;
        cancelMotorResponseTimeout();
        cancelAutoFlowChargeTimeout();
        chargeFeedback.stop();
        chargePhaseNotified = false;
        resetFlowChargeTiming();
        flowChargeErrorRetries = 0;
        flowArmedCommand = FLOW_ARM_NONE;
        pendingOperation = resolveAutoPendingOperation();
        android.util.Log.w("NFCLockPlugin", "自动流程失败(" + reason + ")，重新读取锁ID，重试 "
                + autoFlowLockIdRetries + "/" + MAX_FLOW_LOCK_ID_RETRIES);
        cancelAutoFlowTimeout();
        if (isTagReaderReady()) {
            sendAutoFlowProgress(1, "正在重新读取锁ID");
        } else {
            sendAutoFlowProgress(1, "锁ID可能已变更，请重新贴卡读取锁ID");
        }
        scheduleAutoFlowTimeout("贴卡超时");
        dispatchRereadLockIdOrWaitTag();
    }

    private void failAutoFlowOrRetryLockId(String reason) {
        if (autoFlowRunning && autoFlowLockIdRetries < MAX_FLOW_LOCK_ID_RETRIES) {
            retryAutoFlowWithFreshLockId(reason);
            return;
        }
        sendAutoFlowFail(reason);
    }

    private void sendManualFlowEvent(String type, int step, String message, Boolean success) {
        sendManualFlowEvent(type, step, message, success, null);
    }

    private void sendManualFlowEvent(String type, int step, String message, Boolean success, String motorAction) {
        if (globalCallbackContext == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("step", step);
            json.put("message", message);
            if (success != null) {
                json.put("success", success);
            }
            if (motorAction != null) {
                json.put("motorAction", motorAction);
            }
            if (lockId != null && !lockId.isEmpty()) {
                json.put("lockId", lockId);
            }
            if (cachedLockPassword != null && !cachedLockPassword.isEmpty()) {
                json.put("lockPassword", cachedLockPassword);
            } else {
                json.put("lockPassword", getEffectiveLockPassword());
                json.put("usingDefaultPassword", true);
            }
            if ("manualFlowComplete".equals(type) && flowChargeDurationMs >= 0L) {
                json.put("totalMs", flowChargeDurationMs);
                json.put("chargeMs", flowChargeDurationMs);
                json.put("elapsedMs", flowChargeDurationMs);
                json.put("elapsedSec", Math.round(flowChargeDurationMs / 100.0) / 10.0);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, json);
            result.setKeepCallback(true);
            globalCallbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            android.util.Log.e("NFCLockPlugin", "手动流程事件发送失败: " + e.getMessage());
        }
    }

    private void handleAutoFlowStep(int respTypeValue, NFCLockResponse response) {
        if (!autoFlowRunning) {
            return;
        }
        switch (respTypeValue) {
            case RESP_QUERY_LOCK_ID: {
                if (lockId != null && !lockId.isEmpty() && !autoFlowRereadLockId) {
                    return;
                }
                autoFlowRereadLockId = false;
                String id = response.getLockID();
                if (id == null || id.isEmpty()) {
                    sendAutoFlowFail("未读取到锁ID，请重新贴卡");
                    return;
                }
                lockId = id;
                cacheLockId(id);
                markTagConnectedInFlow();
                cancelAutoFlowTimeout();
                sendAutoFlowProgress(1, "query_lock_id", "查询锁ID", "锁ID已获取，请保持贴卡");
                scheduleAutoFlowTimeout("查询密码超时");
                dispatchFlowCommand(new Runnable() {
                    @Override
                    public void run() {
                        NFCLockManager.getInstance().reqQueryLockPwd();
                        android.util.Log.d("NFCLockPlugin", "自动流程：查询密码已触发");
                    }
                }, FLOW_ARM_QUERY_PWD);
                break;
            }
            case RESP_QUERY_LOCK_PWD: {
                if (!autoFlowAwaitingPassword) {
                    return;
                }
                String pwd = response.getLockPwd();
                if (pwd == null || pwd.isEmpty()) {
                    sendAutoFlowFail("未读取到锁密码，请重新贴卡");
                    return;
                }
                autoFlowAwaitingPassword = false;
                lockPassword = pwd;
                cacheLockPassword(pwd);
                markTagConnectedInFlow();
                cancelAutoFlowTimeout();
                sendAutoFlowProgress(2, "query_password", "查询密码", "密码已获取，请保持贴卡");
                scheduleAutoFlowTimeout("查询状态超时");
                dispatchFlowCommand(new Runnable() {
                    @Override
                    public void run() {
                        NFCLockManager.getInstance().reqQueryPowerLevel();
                        android.util.Log.d("NFCLockPlugin", "自动流程：查询状态已触发");
                    }
                }, FLOW_ARM_QUERY_POWER);
                break;
            }
            case RESP_QUERY_LOCK_POWER_STATE:
                if (awaitingMotorStateVerify) {
                    verifyMotorStateAfterCommand(response, true);
                    break;
                }
                cancelAutoFlowTimeout();
                handleFlowPowerLevelResponse(response, true);
                break;
            case RESP_LOCK_MOTOR_FORWARD:
            case RESP_LOCK_MOTOR_REVERSAL: {
                handleMotorCommandResponse(respTypeValue, response, true);
                break;
            }
            default:
                break;
        }
    }

    private void handleMotorCommandResponse(int respTypeValue, NFCLockResponse response, boolean auto) {
        if (!awaitingMotorResponse) {
            android.util.Log.w("NFCLockPlugin", "忽略非本次流程的电机响应 type=" + respTypeValue);
            return;
        }
        boolean ok = respTypeValue == RESP_LOCK_MOTOR_FORWARD
                ? response.motorForwardSuccess()
                : response.motorReverseSuccess();
        android.util.Log.d("NFCLockPlugin", "电机指令响应: success=" + ok + ", params=" + response.getParams());
        awaitingMotorResponse = false;
        cancelMotorResponseTimeout();
        cancelAutoFlowChargeTimeout();
        if (auto) {
            cancelAutoFlowTimeout();
        } else {
            cancelManualFlowTimeout();
        }
        if (!ok) {
            motorSucceededPendingVerify = false;
            if (auto) {
                String reason = isMotorIdError(respTypeValue, response)
                        ? "锁ID不匹配" : "电机操作失败";
                failAutoFlowOrRetryLockId(reason);
            } else {
                String reason = isMotorIdError(respTypeValue, response)
                        ? "锁ID不匹配" : "电机操作失败";
                failManualFlowOrRetryLockId(reason);
            }
            return;
        }
        motorSucceededPendingVerify = true;
        completeMotorFlowAfterSuccess(auto);
    }

    private void verifyMotorStateAfterCommand(NFCLockResponse response, boolean auto) {
        awaitingMotorStateVerify = false;
        String afterState = response.getLockState();
        String motorAction = auto ? autoFlowMotorAction : manualFlowMotorAction;
        android.util.Log.d("NFCLockPlugin", "电机后验状态 before=" + lockStateBeforeMotor
                + " after=" + afterState + " action=" + motorAction);
        if (isMotorActionSuccessful(lockStateBeforeMotor, afterState, motorAction)) {
            completeMotorFlowAfterSuccess(auto);
        } else {
            motorSucceededPendingVerify = false;
            if (auto) {
                failAutoFlowOrRetryLockId("电机未转动");
            } else {
                failManualFlowOrRetryLockId("电机未转动");
            }
        }
    }

    private void completeMotorFlowAfterSuccess(boolean auto) {
        motorSucceededPendingVerify = false;
        awaitingMotorStateVerify = false;
        lastFlowMotorCompleteMs = System.currentTimeMillis();
        cancelAutoFlowChargeTimeout();
        chargeFeedback.stop();
        if (auto && autoFlowMotorAction != null) {
            lastKnownLockState = "open".equals(autoFlowMotorAction) ? "0" : "1";
        } else if (!auto && manualFlowMotorAction != null) {
            lastKnownLockState = "open".equals(manualFlowMotorAction) ? "0" : "1";
        }
        if (auto) {
            cancelAutoFlowTimeout();
            autoFlowLockIdRetries = 0;
            autoFlowRereadLockId = false;
            sendAutoFlowComplete(true, "自动流程完成", autoFlowMotorAction);
        } else {
            cancelManualFlowTimeout();
            manualFlowLockIdRetries = 0;
            manualFlowRereadLockId = false;
            sendManualFlowComplete(true, "手动流程完成", manualFlowMotorAction);
        }
    }

    private boolean isMotorActionSuccessful(String beforeState, String afterState, String motorAction) {
        if (afterState == null || afterState.trim().isEmpty()) {
            return false;
        }
        String after = afterState.trim();
        if ("open".equals(motorAction)) {
            return "0".equals(after);
        }
        if ("close".equals(motorAction)) {
            return "1".equals(after);
        }
        return beforeState != null && !beforeState.trim().equals(after);
    }

    private void continueAutoFlowAfterPower(NFCLockResponse response) {
        String lockState = response.getLockState();
        String powerLevel = response.getLockPowerLevel();
        lockStateBeforeMotor = lockState;
        if (lockState != null && !lockState.isEmpty()) {
            lastKnownLockState = lockState.trim();
        }
        autoFlowMotorAction = resolveAutoMotorAction(lockState);
        String progressDetail = "close".equals(autoFlowMotorAction) ? "准备关锁" : "准备开锁";
        if ("close".equals(autoFlowMotorAction)) {
            pendingOperation = "MOTOR_REVERSE";
            sendAutoFlowProgress(3, "query_power", "查询状态",
                    progressDetail + (isPowerReady(powerLevel) ? "" : "，正在充电"));
        } else {
            pendingOperation = "MOTOR_FORWARD";
            sendAutoFlowProgress(3, "query_power", "查询状态",
                    progressDetail + (isPowerReady(powerLevel) ? "" : "，正在充电"));
        }

        if (isPowerReady(powerLevel)) {
            finishChargeAndExecuteMotor(powerLevel, lockState, true);
        } else {
            continueFlowChargePolling(powerLevel, lockState, true);
        }
    }

    /** 参考 xhky：满电后先发电机(≈4ms)，UI/日志延后 */
    private void finishChargeAndExecuteMotor(String powerLevel, String lockState, boolean auto) {
        markFlowChargeStarted();
        chargeFeedback.stop();
        executePendingMotorOperation();
        final String powerSnapshot = powerLevel;
        final String stateSnapshot = lockState;
        autoFlowHandler.post(new Runnable() {
            @Override
            public void run() {
                if (stateSnapshot != null && !stateSnapshot.isEmpty()) {
                    lastKnownLockState = stateSnapshot.trim();
                }
                notifyFlowChargeComplete(powerSnapshot, stateSnapshot);
                if (auto) {
                    sendAutoFlowProgress(4, "充电完成，正在执行电机操作");
                } else {
                    sendManualFlowProgress(3, "充电完成，正在执行电机");
                }
            }
        });
    }

    /** 充电中：立即 WithLoop，其它工作全部延后/省略 */
    private void continueFlowChargePolling(String powerLevel, String lockState, boolean auto) {
        if (powerLevel != null) {
            chargeFeedback.update(powerLevel);
        }
        reqQueryPowerLevelWithLoopImmediate();
        if (flowChargeStartMs == 0L) {
            markFlowChargeStarted();
            scheduleAutoFlowChargeTimeout();
            if (!chargePhaseNotified) {
                chargePhaseNotified = true;
                final boolean autoFlow = auto;
                autoFlowHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (autoFlow) {
                            sendAutoFlowProgress(2, "开始充电，请保持贴卡");
                        } else {
                            sendManualFlowProgress(2, "开始充电，请保持贴卡");
                        }
                    }
                });
            }
        }
        autoFlowChargePollCount++;
    }

    /** 贴卡会话内立即下发；reader 未就绪则等待重新贴卡 */
    private void dispatchInFlowPowerPoll() {
        if (isTagReaderReady()) {
            tagConnectedInFlow = true;
            reqQueryPowerLevelWithLoopImmediate();
        } else {
            tagConnectedInFlow = false;
            waitForTagAndArm(FLOW_ARM_QUERY_POWER);
        }
    }

    /** 链式步骤：同一次贴卡内立即 write；否则武装等待下次贴卡 */
    private void dispatchFlowCommand(final Runnable command, final int armWhenNeedTag) {
        if (isTagReaderReady()) {
            markTagConnectedInFlow();
            runNfcWriteCommand(command);
        } else {
            waitForTagAndArm(armWhenNeedTag);
        }
    }

    /**
     * 参考 xhky processQueryPowerLevel：未达标时只做 WithLoop，不做 Cordova/状态等额外工作。
     */
    private void handleFlowPowerLevelResponse(NFCLockResponse response, boolean auto) {
        if (awaitingMotorResponse) {
            return;
        }
        flowChargeErrorRetries = 0;
        markTagConnectedInFlow();
        String powerLevel = response.getLockPowerLevel();
        String lockState = response.getLockState();
        int percent = parsePowerPercent(powerLevel);

        if (auto && autoFlowMotorAction == null) {
            updateFlowLockStateFromPower(lockState, auto);
            continueAutoFlowAfterPower(response);
            if (autoFlowRunning) {
                scheduleAutoFlowTimeout("电机操作超时");
            }
            return;
        }

        if (pendingOperation == null) {
            return;
        }

        if (percent >= 100) {
            finishChargeAndExecuteMotor(powerLevel, lockState, auto);
            return;
        }

        continueFlowChargePolling(powerLevel, lockState, auto);
    }

    private void beginChargePollingIfNeeded() {
        if (autoFlowChargeStartMs == 0L) {
            autoFlowChargeStartMs = System.currentTimeMillis();
            scheduleAutoFlowChargeTimeout();
        }
        autoFlowChargePollCount++;
    }

    /** 与参考 app queryPowerLevelWithLoop 一致：req + writeAndRead 立即执行 */
    private void reqQueryPowerLevelWithLoopImmediate() {
        if ((!autoFlowRunning && !manualFlowRunning) || pendingOperation == null || awaitingMotorResponse) {
            return;
        }
        NFCLockManager.getInstance().reqQueryPowerLevelWithLoop();
    }

    private void notifyFlowChargeComplete(String powerLevel, String lockState) {
        lastNotifiedChargePercent = parsePowerPercent(powerLevel);
        notifyFlowChargePowerLevel(powerLevel, lockState);
    }

    private void updateFlowLockStateFromPower(String lockState, boolean auto) {
        if (lockState == null || lockState.isEmpty()) {
            return;
        }
        lastKnownLockState = lockState.trim();
        if (auto && !awaitingMotorStateVerify && !awaitingMotorResponse && lockStateBeforeMotor == null) {
            lockStateBeforeMotor = lockState;
        }
    }

    private void notifyFlowChargePowerLevel(String powerLevel, String lockState) {
        if (globalCallbackContext == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", "queryPowerLevel");
            json.put("powerLevel", powerLevel);
            if (lockState != null) {
                json.put("lockState", lockState);
            }
            PluginResult result = new PluginResult(PluginResult.Status.OK, json);
            result.setKeepCallback(true);
            globalCallbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            android.util.Log.e("NFCLockPlugin", "充电电量通知失败: " + e.getMessage());
        }
    }

    private void ensureChargeSessionStarted() {
        beginChargePollingIfNeeded();
    }

    private void scheduleImmediatePowerPoll() {
        reqQueryPowerLevelWithLoopImmediate();
    }

    /** 开始充电：无贴卡则等待贴卡，有贴卡则立即 WithLoop 链式轮询 */
    private void startPowerLevelPollingForMotor() {
        chargeFeedback.reset();
        autoFlowChargePollCount = 0;
        lastNotifiedChargePercent = -1;
        flowChargeStartMs = 0L;
        flowChargeDurationMs = -1L;
        autoFlowChargeStartMs = 0L;
        flowChargeErrorRetries = 0;
        cancelAutoFlowChargeTimeout();
        if (isTagReaderReady()) {
            markTagConnectedInFlow();
            reqQueryPowerLevelWithLoopImmediate();
        } else {
            waitForTagAndArm(FLOW_ARM_QUERY_POWER);
        }
    }

    private boolean isMotorIdError(int respTypeValue, NFCLockResponse response) {
        try {
            if (respTypeValue == RESP_LOCK_MOTOR_FORWARD) {
                return response.motorForwardIDError();
            }
            if (respTypeValue == RESP_LOCK_MOTOR_REVERSAL) {
                return response.motorReverseIDError();
            }
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "解析电机ID错误状态失败: " + e.getMessage());
        }
        return false;
    }

    private boolean shouldSuppressGenericCallback(int respTypeValue, boolean suppressMotorGenericCallback) {
        if (suppressMotorGenericCallback) {
            return true;
        }
        if (isMotorRespType(respTypeValue) && lastFlowMotorCompleteMs > 0L
                && System.currentTimeMillis() - lastFlowMotorCompleteMs < 5000L) {
            return true;
        }
        if (!autoFlowRunning && !manualFlowRunning) {
            return false;
        }
        return respTypeValue == RESP_QUERY_LOCK_ID
                || respTypeValue == RESP_QUERY_LOCK_PWD
                || respTypeValue == RESP_QUERY_LOCK_POWER_STATE
                || respTypeValue == RESP_LOCK_MOTOR_FORWARD
                || respTypeValue == RESP_LOCK_MOTOR_REVERSAL;
    }

    private void scheduleAutoFlowChargeTimeout() {
        cancelAutoFlowChargeTimeout();
        autoFlowChargeTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if ((autoFlowRunning || manualFlowRunning) && pendingOperation != null) {
                    android.util.Log.w("NFCLockPlugin", "充电等待超时，强制执行电机");
                    if (autoFlowRunning) {
                        sendAutoFlowProgress(4, "充电超时，尝试执行电机");
                    } else {
                        sendManualFlowProgress(3, "充电超时，尝试执行电机");
                    }
                    executePendingMotorOperation();
                }
            }
        };
        autoFlowHandler.postDelayed(autoFlowChargeTimeoutRunnable, AUTO_FLOW_CHARGE_TIMEOUT_MS);
    }

    private void cancelAutoFlowChargeTimeout() {
        if (autoFlowChargeTimeoutRunnable != null) {
            autoFlowHandler.removeCallbacks(autoFlowChargeTimeoutRunnable);
            autoFlowChargeTimeoutRunnable = null;
        }
        autoFlowChargePollCount = 0;
    }

    private int parsePowerPercent(String powerLevel) {
        if (powerLevel == null || powerLevel.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(powerLevel.trim().replace("%", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void executePendingMotorOperation() {
        if (awaitingMotorResponse) {
            android.util.Log.w("NFCLockPlugin", "电机指令已在等待响应，跳过重复发送");
            return;
        }
        if (lockId == null || lockPassword == null || pendingOperation == null) {
            android.util.Log.e("NFCLockPlugin", "电机操作条件不满足 lockId=" + lockId
                    + " password=" + (lockPassword != null) + " pending=" + pendingOperation);
            if (autoFlowRunning) {
                sendAutoFlowFail("电机操作条件不满足，请重新贴卡");
            } else if (manualFlowRunning) {
                sendManualFlowFail("电机操作条件不满足，请重新贴卡");
            }
            return;
        }
        if (lockStateBeforeMotor == null && lastKnownLockState != null) {
            lockStateBeforeMotor = lastKnownLockState;
        }
        final String motorOp = pendingOperation;
        chargeFeedback.stop();
        finalizeFlowChargeDuration();
        cancelAutoFlowChargeTimeout();
        pendingOperation = null;
        awaitingMotorResponse = true;
        scheduleMotorResponseTimeout();
        try {
            if ("MOTOR_FORWARD".equals(motorOp)) {
                NFCLockManager.getInstance().reqMotorForwardWithPowerLevel(lockId, lockPassword);
            } else if ("MOTOR_REVERSE".equals(motorOp)) {
                NFCLockManager.getInstance().reqMotorReverseWithPowerLevel(lockId, lockPassword);
            }
        } catch (Exception e) {
            awaitingMotorResponse = false;
            cancelMotorResponseTimeout();
            android.util.Log.e("NFCLockPlugin", "执行电机操作失败: " + e.getMessage());
            if (autoFlowRunning) {
                failAutoFlowOrRetryLockId("电机指令发送失败");
            } else if (manualFlowRunning) {
                failManualFlowOrRetryLockId("电机指令发送失败");
            }
            return;
        }
    }

    private void scheduleMotorResponseTimeout() {
        cancelMotorResponseTimeout();
        motorResponseTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!awaitingMotorResponse) {
                    return;
                }
                awaitingMotorResponse = false;
                android.util.Log.w("NFCLockPlugin", "电机响应超时");
                if (autoFlowRunning) {
                    failAutoFlowOrRetryLockId("电机响应超时");
                } else if (manualFlowRunning) {
                    failManualFlowOrRetryLockId("电机响应超时");
                }
            }
        };
        autoFlowHandler.postDelayed(motorResponseTimeoutRunnable, MOTOR_RESPONSE_TIMEOUT_MS);
    }

    private void cancelMotorResponseTimeout() {
        if (motorResponseTimeoutRunnable != null) {
            autoFlowHandler.removeCallbacks(motorResponseTimeoutRunnable);
            motorResponseTimeoutRunnable = null;
        }
    }

    /**
     * 完成后 cState 会回到 2，无法靠状态判断方向。
     * 按累计自动流程次数交替：偶数次开锁，奇数次关锁。
     */
    private String resolveAutoMotorAction(String lockState) {
        int count = getAutoToggleCount();
        String action = (count % 2 == 0) ? "open" : "close";
        android.util.Log.d("NFCLockPlugin", "自动流程 count=" + count
                + ", cState=" + lockState + ", action=" + action);
        return action;
    }

    private int getAutoToggleCount() {
        try {
            return getPrefs().getInt(PREF_AUTO_TOGGLE_COUNT, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private void incrementAutoToggleCount() {
        try {
            int next = getAutoToggleCount() + 1;
            getPrefs().edit().putInt(PREF_AUTO_TOGGLE_COUNT, next).apply();
            android.util.Log.d("NFCLockPlugin", "自动流程次数已更新: " + next);
        } catch (Exception e) {
            android.util.Log.w("NFCLockPlugin", "更新自动流程次数失败: " + e.getMessage());
        }
    }

    private boolean isPowerReady(String powerLevel) {
        return parsePowerPercent(powerLevel) >= 100;
    }

    private void postAutoFlowCommand(final Runnable command) {
        runAutoFlowCommand(command);
    }

    private void scheduleAutoFlowTimeout(final String reason) {
        cancelAutoFlowTimeout();
        autoFlowTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (autoFlowRunning) {
                    if (awaitingMotorStateVerify && motorSucceededPendingVerify) {
                        completeMotorFlowAfterSuccess(true);
                    } else {
                        sendAutoFlowFail(reason + "，请重新贴卡");
                    }
                }
            }
        };
        autoFlowHandler.postDelayed(autoFlowTimeoutRunnable, AUTO_FLOW_TIMEOUT_MS);
    }

    private void cancelAutoFlowTimeout() {
        if (autoFlowTimeoutRunnable != null) {
            autoFlowHandler.removeCallbacks(autoFlowTimeoutRunnable);
            autoFlowTimeoutRunnable = null;
        }
    }

    private void sendAutoFlowProgress(int step, String message) {
        sendAutoFlowProgress(step, null, null, message);
    }

    private void sendAutoFlowProgress(int step, String stepKey, String stepLabel, String message) {
        sendAutoFlowEvent("autoFlowProgress", step, message, null);
    }

    private void resetAutoFlowStepTimings() {
        autoFlowStepTimings = new org.json.JSONArray();
        autoFlowLastStepMs = 0L;
    }

    private void resetManualFlowStepTimings() {
        manualFlowStepTimings = new org.json.JSONArray();
        manualFlowLastStepMs = 0L;
    }

    private void recordAutoFlowChargeStep(long chargeStartMs) {
        long now = System.currentTimeMillis();
        long stepMs = Math.max(0L, now - chargeStartMs);
        long totalMs = autoFlowStartMs > 0L ? Math.max(0L, now - autoFlowStartMs) : stepMs;
        autoFlowLastStepMs = now;
        appendFlowStepTiming(autoFlowStepTimings, "charge", "充电", stepMs, totalMs);
        android.util.Log.d("NFCLockPlugin", "自动流程充电耗时: " + stepMs + "ms");
    }

    private void recordManualFlowChargeStep(long chargeStartMs) {
        long now = System.currentTimeMillis();
        long stepMs = Math.max(0L, now - chargeStartMs);
        long totalMs = manualFlowStartMs > 0L ? Math.max(0L, now - manualFlowStartMs) : stepMs;
        manualFlowLastStepMs = now;
        appendFlowStepTiming(manualFlowStepTimings, "charge", "充电", stepMs, totalMs);
        android.util.Log.d("NFCLockPlugin", "手动流程充电耗时: " + stepMs + "ms");
    }

    private void recordAutoFlowStep(String stepKey, String stepLabel) {
        long now = System.currentTimeMillis();
        long stepMs = autoFlowLastStepMs > 0L ? Math.max(0L, now - autoFlowLastStepMs) : 0L;
        long totalMs = autoFlowStartMs > 0L ? Math.max(0L, now - autoFlowStartMs) : 0L;
        autoFlowLastStepMs = now;
        appendFlowStepTiming(autoFlowStepTimings, stepKey, stepLabel, stepMs, totalMs);
    }

    private void recordManualFlowStep(String stepKey, String stepLabel) {
        long now = System.currentTimeMillis();
        long stepMs = manualFlowLastStepMs > 0L ? Math.max(0L, now - manualFlowLastStepMs) : 0L;
        long totalMs = manualFlowStartMs > 0L ? Math.max(0L, now - manualFlowStartMs) : 0L;
        manualFlowLastStepMs = now;
        appendFlowStepTiming(manualFlowStepTimings, stepKey, stepLabel, stepMs, totalMs);
    }

    private void appendFlowStepTiming(org.json.JSONArray timings, String stepKey, String stepLabel,
                                      long stepMs, long totalMs) {
        try {
            JSONObject step = new JSONObject();
            step.put("key", stepKey);
            step.put("label", stepLabel);
            step.put("stepMs", stepMs);
            step.put("totalMs", totalMs);
            step.put("stepSec", Math.round(stepMs / 10.0) / 100.0);
            step.put("totalSec", Math.round(totalMs / 10.0) / 100.0);
            timings.put(step);
        } catch (JSONException e) {
            android.util.Log.e("NFCLockPlugin", "记录步骤耗时失败: " + e.getMessage());
        }
    }

    private void sendAutoFlowComplete(boolean success, String message, String motorAction) {
        autoFlowRunning = false;
        autoFlowAwaitingPassword = false;
        awaitingMotorResponse = false;
        awaitingMotorStateVerify = false;
        motorSucceededPendingVerify = false;
        pendingOperation = null;
        cancelAutoFlowTimeout();
        cancelAutoFlowChargeTimeout();
        chargeFeedback.stop();
        cancelMotorResponseTimeout();
        awaitingMotorResponse = false;
        if (success) {
            incrementAutoToggleCount();
            nfcSoundHelper.play(NFCLockSoundHelper.SOUND_SUCCESS);
        } else {
            nfcSoundHelper.play(NFCLockSoundHelper.SOUND_ERROR);
        }
        sendAutoFlowEvent("autoFlowComplete", 4, message, success, motorAction);
        resetNfcAfterFlow();
    }

    private void sendAutoFlowFail(String message) {
        sendAutoFlowComplete(false, message, null);
    }

    private void sendAutoFlowEvent(String type, int step, String message, Boolean success) {
        sendAutoFlowEvent(type, step, message, success, null);
    }

    private void sendAutoFlowEvent(String type, int step, String message, Boolean success, String motorAction) {
        if (globalCallbackContext == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("step", step);
            json.put("message", message);
            if (success != null) {
                json.put("success", success);
            }
            if (motorAction != null) {
                json.put("motorAction", motorAction);
            }
            if (lockId != null && !lockId.isEmpty()) {
                json.put("lockId", lockId);
            }
            if (lockPassword != null && !lockPassword.isEmpty()) {
                json.put("lockPassword", lockPassword);
            }
            if ("autoFlowComplete".equals(type) && flowChargeDurationMs >= 0L) {
                json.put("totalMs", flowChargeDurationMs);
                json.put("chargeMs", flowChargeDurationMs);
                json.put("elapsedMs", flowChargeDurationMs);
                json.put("elapsedSec", Math.round(flowChargeDurationMs / 100.0) / 10.0);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, json);
            result.setKeepCallback(true);
            globalCallbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            android.util.Log.e("NFCLockPlugin", "自动流程事件发送失败: " + e.getMessage());
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
        chargeFeedback.stop();
        // 参考原生程序：在onPause时停止NFC读取
        if (isInitialized) {
            NFCLockManager.getInstance().stopReadNFCTag(cordova.getActivity());
            android.util.Log.d("NFCLockPlugin", "NFC读取已停止");
        }
    }

    @Override
    public void onDestroy() {
        // 资源清理
        cancelAutoFlowTimeout();
        cancelAutoFlowChargeTimeout();
        cancelManualFlowTimeout();
        cancelAutoFlowChargeTimeout();
        cancelMotorResponseTimeout();
        autoFlowRunning = false;
        manualFlowRunning = false;
        awaitingMotorResponse = false;
        nfcSoundHelper.release();
        chargeFeedback.release();
        if (isInitialized) {
        NFCLockManager.getInstance().unRegisterNFCLockCallBack();
        NFCLockManager.getInstance().onDestroy();
        }
        super.onDestroy();
    }
}