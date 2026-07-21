/**
 * 聚如信 NFC 锁 JS SDK（可独立使用，不依赖 index.html）
 *
 * 依赖：cordova.js、NFCLockPlugin（Java + jar）、可选 appConfig.js / lockPermission.js
 *
 * 总耗时定义：原生上报的「充电结束时间 − 充电开始时间」（chargeMs），不含查 ID/密码与电机时间。
 *
 * 快速用法：
 *   document.addEventListener('deviceready', function () {
 *     NfcJrxUtil.openLock().then(function (r) { console.log('开锁成功', r.chargeMs); });
 *   });
 */
(function (global) {
  var isPluginInitialized = false;
  var callbackRegistered = false;
  var flowRunning = false;
  var stopNextStep = false;
  /** 原生上报的充电开始时间戳（ms） */
  var chargeStartMs = 0;
  /** 原生上报的充电结束时间戳（ms） */
  var chargeEndMs = 0;
  /** 充电结束−开始的时间差（ms），作为总耗时 */
  var latestNativeChargeMs = -1;

  var latestLockState = null;
  var latestPowerLevel = -1;
  var latestLockId = '';
  var latestLockPassword = '';

  var pendingQueryLockIdResolve = null;
  var pendingQueryLockPasswordResolve = null;
  var pendingQueryPowerResolve = null;
  var pendingMotorResolve = null;

  var hooks = {
    log: function (msg) { console.log(msg); },
    onPower: function () {},
    onLockId: function () {},
    onPasswordReady: function () {},
    onFlowPhase: function () {},
    setPluginInitialized: function () {}
  };

  /** 读取 appConfig.js 中的 nfc 配置段 */
  function cfg() {
    return (global.AppConfig && global.AppConfig.nfc) || {};
  }

  /** 获取 Cordova NFCLockPlugin 桥接对象 */
  function plugin() {
    return global.cordova && global.cordova.plugins && global.cordova.plugins.NFCLockPlugin;
  }

  /** 输出日志（经 hooks.log 转发到 UI 或 console） */
  function log(msg, type) {
    if (hooks.log) hooks.log(msg, type || 'info');
  }

  /** 格式化为 HH:mm:ss.SSS */
  function formatLogTime(ts) {
    var d = ts ? new Date(ts) : new Date();
    var p = function (n) { return String(n).padStart(2, '0'); };
    var ms = String(d.getMilliseconds()).padStart(3, '0');
    return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) + '.' + ms;
  }

  /** 毫秒转秒字符串 */
  function formatSec(ms) {
    if (typeof ms !== 'number' || ms < 0) return '-';
    return (ms / 1000).toFixed(2) + ' s';
  }

  /** 计算充电总耗时：结束时间 − 开始时间 */
  function resolveChargeDurationMs() {
    if (chargeStartMs > 0 && chargeEndMs > 0) {
      return Math.max(0, chargeEndMs - chargeStartMs);
    }
    if (latestNativeChargeMs >= 0) return latestNativeChargeMs;
    return 0;
  }

  /** 通知 UI 当前流程阶段（并同步更新贴卡/电量状态文案） */
  function phase(name, detail) {
    var d = detail || {};
    statusByPhase(name, d);
    if (hooks.onFlowPhase) hooks.onFlowPhase(name, d);
  }

  /** 统一 respCmdType 为字符串 */
  function normalizeRespType(result) {
    if (!result || !result.respCmdType) return '';
    return String(result.respCmdType);
  }

  /** 带超时的 Promise 包装（贴卡等待） */
  function withTimeout(ms, executor) {
    return new Promise(function (resolve, reject) {
      var timer = setTimeout(function () {
        reject(new Error('超时，请将NFC卡靠近设备后重试'));
      }, ms);
      executor(function (value) {
        clearTimeout(timer);
        resolve(value);
      });
    });
  }

  /** 延迟 Promise */
  function sleep(ms) {
    return new Promise(function (resolve) {
      setTimeout(resolve, ms);
    });
  }

  /** 解析电量百分比 0~100 */
  function parsePowerPercent(value) {
    if (value == null) return 0;
    var n = parseInt(String(value).replace('%', '').trim(), 10);
    return isNaN(n) ? 0 : Math.max(0, Math.min(100, n));
  }

  // ========== 电量圆环 / 贴卡状态 UI（供演示页绑定，逻辑集中在本文件） ==========

  /** 圆环周长，需与页面 CSS stroke-dasharray 一致 */
  var CHARGE_RING_CIRC = 326.7;

  /**
   * 电量圆环 UI 状态
   * - 每次原生 queryPowerLevel 都立即刷新圆环（升高/降低均同步）
   * - 用瞬时 Δ%/Δt 估算贴卡强弱，便于确认是否在 NFC 最强区域
   */
  var chargeUi = {
    bound: false,
    ringEl: null,
    percentEl: null,
    rateEl: null,
    statusEl: null,
    lastSample: null,
    /** 平滑速率 %/s：正=上升，负=下降 */
    smoothedRate: 0,
    /** 最近若干次瞬时速率，用于更灵敏地反映贴卡强弱 */
    recentRates: []
  };

  /**
   * 绑定电量圆环与状态文案 DOM（页面启动时调用一次）
   * @param {Object} els
   * @param {string|HTMLElement} [els.ring] 圆环前景 #chargeRingFg
   * @param {string|HTMLElement} [els.percent] 百分比文字 #powerLevel
   * @param {string|HTMLElement} [els.rate] 变化速率 #chargeRate
   * @param {string|HTMLElement} [els.status] 流程状态 #unlockStatus
   */
  function bindChargeUi(els) {
    els = els || {};
    function resolve(ref) {
      if (!ref) return null;
      if (typeof ref === 'string') return document.getElementById(ref);
      return ref;
    }
    chargeUi.ringEl = resolve(els.ring || 'chargeRingFg');
    chargeUi.percentEl = resolve(els.percent || 'powerLevel');
    chargeUi.rateEl = resolve(els.rate || 'chargeRate');
    chargeUi.statusEl = resolve(els.status || 'unlockStatus');
    chargeUi.bound = !!(chargeUi.ringEl || chargeUi.percentEl || chargeUi.rateEl);
    chargeUi.lastSample = null;
    chargeUi.smoothedRate = 0;
    chargeUi.recentRates = [];
    renderChargeRingPercent(0, 0);
  }

  /**
   * 根据瞬时速率生成提示文案（正上升 / 负下降，用于判断贴卡区域强弱）
   * @param {number} ratePerSec %/s
   * @returns {{cls:string, label:string}}
   */
  function formatChargeRateTone(ratePerSec) {
    if (!ratePerSec || Math.abs(ratePerSec) < 0.08) {
      return { cls: 'idle', label: '变化速率: --（请调整贴卡位置）' };
    }
    var abs = Math.abs(ratePerSec);
    var rising = ratePerSec > 0;
    var dir = rising ? '上升' : '下降';
    var speed = '慢';
    var cls = rising ? 'slow' : 'down';
    if (abs >= 8) {
      speed = '快';
      cls = rising ? 'fast' : 'down';
    } else if (abs >= 3) {
      speed = '正常';
      cls = rising ? 'normal' : 'down';
    }
    var sign = rising ? '+' : '';
    return {
      cls: cls,
      label: '变化速率: ' + sign + ratePerSec.toFixed(1) + '%/s · ' + dir + speed
        + (rising && abs >= 8 ? '（贴卡较强）' : '')
    };
  }

  /**
   * 仅渲染圆环百分比与速率文案（不改采样状态）
   * @param {number} percent 0~100
   * @param {number} ratePerSec 当前速率
   */
  function renderChargeRingPercent(percent, ratePerSec) {
    var safe = Math.max(0, Math.min(100, Number(percent) || 0));
    var rate = typeof ratePerSec === 'number' ? ratePerSec : chargeUi.smoothedRate;
    var isDown = rate < -0.08;
    var ring = chargeUi.ringEl;
    var label = chargeUi.percentEl;
    var rateEl = chargeUi.rateEl;

    if (ring) {
      ring.style.strokeDashoffset = String(CHARGE_RING_CIRC * (1 - safe / 100));
      ring.classList.toggle('full', safe >= 100);
      ring.classList.toggle('down', isDown && safe < 100);
    }
    if (label) {
      label.textContent = Math.round(safe) + '%';
      label.classList.toggle('full', safe >= 100);
      label.classList.toggle('down', isDown && safe < 100);
    }
    if (rateEl) {
      var tone = safe >= 100
        ? { cls: 'fast', label: '变化速率: 已满 100%' }
        : formatChargeRateTone(rate);
      rateEl.textContent = tone.label;
      rateEl.className = 'charge-rate ' + tone.cls;
    }
  }

  /**
   * 每次电量查询回调：立即刷新圆环（升/降都更新），并估算变化快慢
   * @param {number|string} percent 原生电量
   * @returns {number} 规范化后的 0~100
   */
  function applyPowerSample(percent) {
    var safe = parsePowerPercent(percent);
    var now = Date.now();
    var instant = 0;

    if (chargeUi.lastSample && now > chargeUi.lastSample.t) {
      var dtSec = (now - chargeUi.lastSample.t) / 1000;
      var dPercent = safe - chargeUi.lastSample.p;
      // 极短间隔也计入，提升灵敏度；同值则衰减速率
      if (dtSec > 0.02) {
        if (dPercent !== 0) {
          instant = dPercent / dtSec;
          chargeUi.recentRates.push(instant);
          if (chargeUi.recentRates.length > 5) chargeUi.recentRates.shift();
          var sum = 0;
          for (var i = 0; i < chargeUi.recentRates.length; i++) sum += chargeUi.recentRates[i];
          var avg = sum / chargeUi.recentRates.length;
          // 瞬时权重更高，贴卡强弱变化更快反映到文案
          chargeUi.smoothedRate = chargeUi.smoothedRate !== 0
            ? (chargeUi.smoothedRate * 0.35 + avg * 0.65)
            : avg;
        } else if (dtSec >= 0.25) {
          chargeUi.smoothedRate *= 0.6;
          if (Math.abs(chargeUi.smoothedRate) < 0.08) chargeUi.smoothedRate = 0;
        }
      }
    } else {
      chargeUi.smoothedRate = 0;
      chargeUi.recentRates = [];
    }

    chargeUi.lastSample = { p: safe, t: now };
    latestPowerLevel = safe;
    renderChargeRingPercent(safe, chargeUi.smoothedRate);
    return safe;
  }

  /**
   * 新开/关锁流程开始：圆环归零，清空速率采样
   */
  function resetChargeUiForNewFlow() {
    chargeUi.lastSample = null;
    chargeUi.smoothedRate = 0;
    chargeUi.recentRates = [];
    latestPowerLevel = 0;
    renderChargeRingPercent(0, 0);
  }

  /**
   * 流程结束（成功或失败）：固定显示电量 100%
   * 开锁成功/失败都应保持满电展示，不再重置为 0
   */
  function finishChargeUiFull() {
    chargeUi.lastSample = { p: 100, t: Date.now() };
    chargeUi.smoothedRate = 0;
    chargeUi.recentRates = [];
    latestPowerLevel = 100;
    renderChargeRingPercent(100, 0);
  }

  /**
   * 更新锁状态主文案（#unlockStatus）
   * @param {string} text 显示文字
   * @param {string} [mode] tip-red | charging | err | lock-done
   */
  function setLockStatus(text, mode) {
    var el = chargeUi.statusEl || document.getElementById('unlockStatus');
    if (!el) return;
    el.textContent = text || '-';
    if (text === '开锁成功' || text === '关锁成功') {
      el.className = 'lock-status-below lock-done';
      return;
    }
    el.className = 'lock-status-below' + (mode ? ' ' + mode : '');
  }

  /**
   * 按流程阶段更新贴卡/充电状态文案
   * @param {string} phaseName query_lock_id | fetch_password | charging | motor | done | failed
   * @param {Object} [detail]
   */
  function statusByPhase(phaseName, detail) {
    if (phaseName === 'query_lock_id') return setLockStatus('请贴卡', 'tip-red');
    if (phaseName === 'fetch_password') return setLockStatus('匹配权限中', 'charging');
    if (phaseName === 'charging') return setLockStatus('请保持贴卡', 'charging');
    if (phaseName === 'motor') {
      var act = detail && detail.action;
      setLockStatus(act === 'close' ? '关锁中' : '开锁中', 'charging');
      return;
    }
    if (phaseName === 'done') {
      var doneAct = detail && detail.motorAction;
      setLockStatus(doneAct === 'close' ? '关锁成功' : '开锁成功', 'lock-done');
      finishChargeUiFull();
      return;
    }
    if (phaseName === 'failed') {
      setLockStatus('失败', 'err');
      finishChargeUiFull();
    }
  }

  /**
   * 电机动作转成功提示文案
   * @param {'open'|'close'|string} action
   * @returns {string}
   */
  function motorActionText(action) {
    if (action === 'open') return '开锁成功';
    if (action === 'close') return '关锁成功';
    return '完成';
  }

  /** 清空所有 pending 回调 */
  function clearPending() {
    pendingQueryLockIdResolve = null;
    pendingQueryLockPasswordResolve = null;
    pendingQueryPowerResolve = null;
    pendingMotorResolve = null;
  }

  /** 以失败结束电机 pending（查 ID 阶段 NFC 错误不调用） */
  function rejectPending(err) {
    var message = err && err.message ? err.message : String(err || 'NFC错误');
    if (pendingMotorResolve) {
      var motorResolve = pendingMotorResolve;
      pendingMotorResolve = null;
      motorResolve({ ok: false, action: '', error: message });
    }
  }

  /** 有 lockId 时 resolve 查 ID pending */
  function resolveQueryLockIdIfPending(result, lockId) {
    if (!pendingQueryLockIdResolve || !lockId) return;
    var payload = result && typeof result === 'object' ? Object.assign({}, result) : {};
    payload.lockId = lockId;
    var resolve = pendingQueryLockIdResolve;
    pendingQueryLockIdResolve = null;
    resolve(payload);
  }

  /** 默认取密码：LockPermission 或 appConfig 兜底密码 */
  function defaultReadPasswordFn(lockId) {
    if (global.LockPermission && typeof global.LockPermission.fetchPassword === 'function') {
      if (global.LockPermission.applyConfig) global.LockPermission.applyConfig();
      return global.LockPermission.fetchPassword(lockId);
    }
    var pwd = (global.AppConfig && (global.AppConfig.defaultLockPassword || global.AppConfig.fallbackPassword)) || '';
    if (pwd) return Promise.resolve(String(pwd).trim());
    return Promise.reject(new Error('未配置 readPasswordFn 或 LockPermission'));
  }

  /** 读取自动开关锁计数（localStorage） */
  function getAutoToggleCount() {
    try {
      return parseInt(localStorage.getItem('nfc_auto_toggle_count') || '0', 10);
    } catch (e) {
      return 0;
    }
  }

  /** 自动开关锁成功后计数 +1 */
  function incrementAutoToggleCount() {
    try {
      localStorage.setItem('nfc_auto_toggle_count', String(getAutoToggleCount() + 1));
    } catch (e) {}
  }

  var NfcJrxUtil = {

    /**
     * 注入日志、电量、流程阶段等钩子（UI 层可选）
     * 注意：电量圆环与「请贴卡」状态已内置，一般只需传 log / setPluginInitialized
     */
    setHooks: function (h) {
      if (!h) return;
      Object.keys(h).forEach(function (k) {
        hooks[k] = h[k];
      });
    },

    /**
     * 绑定电量圆环与状态 DOM（演示页启动时调用）
     * @param {Object} [els] ring/percent/rate/status 元素或 id
     */
    bindChargeUi: function (els) {
      bindChargeUi(els);
    },

    /**
     * 手动刷新电量圆环（一般由原生回调自动调用，无需业务层再调）
     * @param {number|string} percent
     */
    updateChargeRing: function (percent) {
      return applyPowerSample(percent);
    },

    /**
     * 新流程开始：电量圆环归零
     */
    resetChargeUiForNewFlow: function () {
      resetChargeUiForNewFlow();
    },

    /**
     * 流程结束（成功/失败）：电量固定显示 100%
     */
    finishChargeUiFull: function () {
      finishChargeUiFull();
    },

    /**
     * 更新锁状态文案（请贴卡 / 开锁中 / 成功 等）
     * @param {string} text
     * @param {string} [mode] tip-red | charging | err
     */
    setLockStatus: function (text, mode) {
      setLockStatus(text, mode);
    },

    /**
     * 按流程阶段更新状态文案
     * @param {string} phaseName
     * @param {Object} [detail]
     */
    statusByPhase: function (phaseName, detail) {
      statusByPhase(phaseName, detail);
    },

    /**
     * 电机动作 → 成功文案
     * @param {'open'|'close'|string} action
     * @returns {string}
     */
    motorActionText: function (action) {
      return motorActionText(action);
    },

    /**
     * 当前平滑后的电量变化速率 %/s（正上升 / 负下降）
     * @returns {number}
     */
    getChargeChangeRate: function () {
      return chargeUi.smoothedRate;
    },

    /** 插件是否已完成 init */
    isInitialized: function () {
      return isPluginInitialized;
    },

    /** 开/关锁流程是否执行中 */
    isFlowRunning: function () {
      return flowRunning;
    },

    /** 最近一次读到的锁 ID */
    getLatestLockId: function () {
      return latestLockId;
    },

    /** 最近一次使用的锁密码 */
    getLatestLockPassword: function () {
      return latestLockPassword;
    },

    /** 最近一次电量 0~100 */
    getLatestPowerLevel: function () {
      return latestPowerLevel;
    },

    /** 最近一次锁状态（0 开 / 1 关） */
    getLatestLockState: function () {
      return latestLockState;
    },

    /** 最近一次充电耗时（结束−开始，ms） */
    getLatestChargeDurationMs: function () {
      return resolveChargeDurationMs();
    },

    /**
     * 检查 Cordova 与 NFCLockPlugin 是否可用；插件未就绪时等待 deviceready（最长 12s）
     */
    ensureCordovaReady: function () {
      return new Promise(function (resolve, reject) {
        function done() {
          if (typeof global.cordova === 'undefined') {
            reject(new Error('cordova 未注入'));
            return;
          }
          if (!plugin()) {
            reject(new Error('NFCLockPlugin 未找到'));
            return;
          }
          resolve();
        }

        if (plugin()) {
          resolve();
          return;
        }
        if (typeof global.cordova === 'undefined') {
          reject(new Error('cordova 未注入'));
          return;
        }

        var settled = false;
        function onReady() {
          if (settled) return;
          settled = true;
          document.removeEventListener('deviceready', onReady, false);
          clearTimeout(timer);
          done();
        }
        document.addEventListener('deviceready', onReady, false);
        var timer = setTimeout(function () {
          if (settled) return;
          settled = true;
          document.removeEventListener('deviceready', onReady, false);
          done();
        }, 12000);
      });
    },

    /**
     * 确保插件已初始化（幂等）
     * @param {Object} [options] manual: 是否输出手动初始化日志
     */
    ensureInitialized: function (options) {
      var self = this;
      return this.ensureCordovaReady().then(function () {
        if (isPluginInitialized) return 'already initialized';
        return self.initPluginJrx(options);
      });
    },

    /** 调用原生 init 并注册全局 NFC 回调 */
    initPluginJrx: function (options) {
      var self = this;
      var manual = options && options.manual;
      if (isPluginInitialized) {
        return Promise.resolve('already initialized');
      }
      stopNextStep = false;
      if (manual) log('初始化插件...');
      else log('插件未初始化，正在自动初始化...');

      return new Promise(function (resolve, reject) {
        plugin().init(
          function (res) {
            isPluginInitialized = true;
            if (hooks.setPluginInitialized) hooks.setPluginInitialized(true);
            log((manual ? '插件初始化成功: ' : '插件自动初始化成功: ') + res, 'success');
            if (!callbackRegistered) {
              self.registerCallbackJrx();
              callbackRegistered = true;
            }
            resolve(res);
          },
          function (err) {
            if (hooks.setPluginInitialized) hooks.setPluginInitialized(false);
            log('插件初始化失败: ' + err, 'error');
            reject(err);
          }
        );
      });
    },

    /** 注册原生异步回调，分发锁 ID/密码/电量/充电阶段/电机结果 */
    registerCallbackJrx: function () {
      plugin().registerCallback(
        function (result) {
          if (result && result.type === 'error') {
            if (pendingMotorResolve) {
              rejectPending(new Error(result.message || 'NFC读写错误'));
            }
            return;
          }

          var respType = normalizeRespType(result);
          var isQueryIdResp = !!(result && (
            result.type === 'queryLockId' ||
            respType === 'RESP_QUERY_LOCK_ID' ||
            respType === '0'
          ));
          var isQueryPwdResp = !!(result && (
            result.type === 'queryLockPassword' ||
            respType === 'RESP_QUERY_LOCK_PWD' ||
            respType === '2'
          ));

          if (result && result.lockId) {
            var id = String(result.lockId).trim();
            if (id) {
              latestLockId = id;
              if (hooks.onLockId) hooks.onLockId(id);
              if (pendingQueryLockIdResolve) resolveQueryLockIdIfPending(result, id);
            }
          } else if (isQueryIdResp && pendingQueryLockIdResolve) {
            resolveQueryLockIdIfPending(result, latestLockId);
          }

          if (isQueryPwdResp || (result && (result.lockPassword || result.password))) {
            var pwd = result && (result.lockPassword || result.password);
            if (pwd) {
              latestLockPassword = String(pwd).trim();
              if (pendingQueryLockPasswordResolve) {
                pendingQueryLockPasswordResolve({ lockPassword: latestLockPassword });
                pendingQueryLockPasswordResolve = null;
              }
            }
          }

          if (result && result.type === 'jsChargePhase') {
            if (result.phase === 'start') {
              chargeStartMs = result.timestamp || Date.now();
              chargeEndMs = 0;
              latestNativeChargeMs = -1;
              log('开始充电 [' + formatLogTime(chargeStartMs) + '] ' + (result.message || ''), 'info');
            } else if (result.phase === 'end') {
              chargeEndMs = result.timestamp || Date.now();
              latestNativeChargeMs = typeof result.chargeMs === 'number'
                ? result.chargeMs
                : (chargeStartMs > 0 ? Math.max(0, chargeEndMs - chargeStartMs) : 0);
              log('充电结束 [' + formatLogTime(chargeEndMs) + '] 总耗时 ' + formatSec(latestNativeChargeMs), 'success');
            }
          }

          if (result && result.type === 'queryPowerLevel' && result.powerLevel != null) {
            latestLockState = result.lockState;
            // 每次查询立即刷新圆环（升/降都更新），供确认 NFC 贴卡强弱
            latestPowerLevel = applyPowerSample(result.powerLevel);
            if (hooks.onPower) hooks.onPower(latestPowerLevel, latestLockState, chargeUi.smoothedRate);
            if (pendingQueryPowerResolve) {
              pendingQueryPowerResolve(result);
              pendingQueryPowerResolve = null;
            }
          }

          if (result && (result.type === 'motorForward' || result.motorForwardSuccess !== undefined)) {
            var openOk = !!result.motorForwardSuccess;
            if (openOk) latestLockState = '0';
            if (pendingMotorResolve) {
              pendingMotorResolve({ ok: openOk, action: 'open', result: result });
              pendingMotorResolve = null;
            }
          }

          if (result && (result.type === 'motorReverse' || result.motorReverseSuccess !== undefined)) {
            var closeOk = !!result.motorReverseSuccess;
            if (closeOk) latestLockState = '1';
            if (pendingMotorResolve) {
              pendingMotorResolve({ ok: closeOk, action: 'close', result: result });
              pendingMotorResolve = null;
            }
          }
        },
        function (error) {
          if (!flowRunning && !pendingMotorResolve) return;
          if (pendingMotorResolve) {
            rejectPending(new Error(typeof error === 'string' ? error : '回调错误'));
          }
        }
      );
    },

    /**
     * 通用步骤重试：发指令 → 等待回调 → 校验
     * @param {string} stepText 步骤名（日志用）
     * @param {number} attempts 最大尝试次数
     * @param {number} timeoutMs 单次超时
     */
    runStepWithRetry: function (stepText, attempts, timeoutMs, fireCommand, pendingSetter, resultValidator) {
      return new Promise(function (resolve, reject) {
        var lastErr = null;
        var attempt = 0;

        function tryOnce() {
          if (stopNextStep) {
            reject(new Error('流程已取消'));
            return;
          }
          attempt++;
          if (attempt > 1) log(stepText + ' 第' + attempt + '次重试...');

          withTimeout(timeoutMs, function (resolvePending) {
            pendingSetter(resolvePending);
            fireCommand();
          })
            .then(function (result) {
              if (resultValidator && !resultValidator(result)) {
                throw new Error(stepText + ' 回包无效');
              }
              resolve(result);
            })
            .catch(function (e) {
              lastErr = e;
              pendingSetter(null);
              if (attempt < attempts) {
                sleep(200).then(tryOnce);
              } else {
                reject(lastErr || new Error(stepText + ' 失败'));
              }
            });
        }

        tryOnce();
      });
    },

    /** 电机步骤重试（原生充电+电机一次调用，等电机回调） */
    runMotorActionWithRetry: function (actionName, fireCommand) {
      var motorTimeout = cfg().motorTimeoutMs || 8000;
      var motorRetries = cfg().motorRetries || 4;
      return new Promise(function (resolve, reject) {
        var lastErr = null;
        var attempt = 0;

        function tryOnce() {
          if (stopNextStep) {
            reject(new Error('流程已取消'));
            return;
          }
          attempt++;
          if (attempt > 1) log(actionName + ' 第' + attempt + '次重试...');

          withTimeout(motorTimeout, function (resolvePending) {
            pendingMotorResolve = resolvePending;
            fireCommand();
          })
            .then(function (motorResult) {
              if (!motorResult || !motorResult.ok) throw new Error(actionName + ' 失败');
              resolve(motorResult);
            })
            .catch(function (e) {
              lastErr = e;
              pendingMotorResolve = null;
              if (attempt < motorRetries) {
                sleep(200).then(tryOnce);
              } else {
                reject(lastErr || new Error(actionName + ' 执行失败'));
              }
            });
        }

        tryOnce();
      });
    },

    /** 发送查询锁 ID 指令（底层，不等待 Promise） */
    queryLockId: function () {
      if (!isPluginInitialized) return;
      phase('query_lock_id');
      plugin().queryLockId(function () {}, function () {});
    },

    /**
     * 查询锁 ID（Promise，需贴卡）
     * @returns {Promise<{lockId:string}>}
     */
    queryLockIdAsync: function (options) {
      var self = this;
      var nfcCfg = cfg();
      return this.ensureInitialized(options).then(function () {
        return self.runStepWithRetry(
          '查询锁ID',
          nfcCfg.queryLockIdRetries || 3,
          nfcCfg.queryLockIdTimeoutMs || 3500,
          function () { self.queryLockId(); },
          function (resolve) { pendingQueryLockIdResolve = resolve; },
          function (r) { return !!(r && r.lockId); }
        );
      });
    },

    /** 发送从卡读取锁密码指令（底层） */
    queryLockPassword: function () {
      if (!isPluginInitialized) return;
      phase('query_lock_password');
      plugin().queryLockPassword(function () {}, function () {});
    },

    /**
     * 从 NFC 卡查询锁密码（Promise）
     * @returns {Promise<{lockPassword:string}>}
     */
    queryLockPasswordAsync: function (options) {
      var self = this;
      var nfcCfg = cfg();
      return this.ensureInitialized(options).then(function () {
        return self.runStepWithRetry(
          '查询锁密码',
          nfcCfg.queryLockPasswordRetries || 2,
          nfcCfg.queryLockPasswordTimeoutMs || 3500,
          function () { self.queryLockPassword(); },
          function (resolve) { pendingQueryLockPasswordResolve = resolve; },
          function (r) { return !!(r && r.lockPassword); }
        );
      });
    },

    /** 带凭证查询电量/锁状态（底层） */
    queryLockStatusWithCredential: function (lockId, lockPassword) {
      if (!isPluginInitialized) return;
      plugin().queryLockStatus(lockId, lockPassword, function () {}, function () {});
    },

    /**
     * 查询电量及开关状态（Promise）
     * @returns {Promise<{powerLevel, lockState}>}
     */
    queryPowerLevelAsync: function (lockId, lockPassword, options) {
      var self = this;
      var nfcCfg = cfg();
      var id = lockId || latestLockId;
      var pwd = lockPassword || latestLockPassword;
      return this.ensureInitialized(options).then(function () {
        return self.runStepWithRetry(
          '查询电量',
          nfcCfg.queryPowerRetries || 2,
          nfcCfg.queryPowerTimeoutMs || 1200,
          function () { self.queryLockStatusWithCredential(id, pwd); },
          function (resolve) { pendingQueryPowerResolve = resolve; },
          function (r) { return !!(r && r.powerLevel != null); }
        );
      });
    },

    /**
     * 通过权限/配置匹配锁密码（不读卡）
     * @param {string} lockId
     */
    fetchLockPasswordAsync: function (lockId, readPasswordFn) {
      var fn = readPasswordFn || defaultReadPasswordFn;
      return Promise.resolve().then(function () {
        return fn(lockId);
      }).then(function (pwd) {
        if (!pwd) throw new Error('未获取到锁密码');
        latestLockPassword = String(pwd).trim();
        if (hooks.onPasswordReady) hooks.onPasswordReady(lockId, latestLockPassword);
        return { lockId: lockId, lockPassword: latestLockPassword };
      });
    },

    /**
     * 中止当前开/关锁流程（仅 JS 编排层，不关闭原生 Reader Mode）。
     * 其它 NFC 模块读卡前若提示「流程已取消」，请再调 clearFlowCancel()。
     */
    abortFlow: function () {
      stopNextStep = true;
      flowRunning = false;
      rejectPending(new Error('流程已取消'));
      clearPending();
    },

    /** 清除 abortFlow / 历史 stopReadNFCJrx 留下的「流程已取消」标记 */
    clearFlowCancel: function () {
      stopNextStep = false;
    },

    /**
     * 停止全局 NFC Reader Mode（原生 stopReadNFCTag）。
     * 会影响本 App 内所有 NFCLockPlugin 读卡；用完后须调 startReadNFCJrx() 恢复。
     * 取消进行中的开/关锁请用 abortFlow()，不要单独调本方法。
     */
    stopReadNFCJrx: function () {
      if (!plugin() || !plugin().stopReadNFC) return;
      plugin().stopReadNFC(function () {}, function () {});
    },

    /** 恢复 NFC Reader Mode（与 stopReadNFCJrx 配对） */
    startReadNFCJrx: function () {
      if (!plugin() || !plugin().startReadNFC) return;
      plugin().startReadNFC(function () {}, function () {});
    },

    /**
     * 为下一次读卡重置 NFC 会话（原生 stop→start 并等待 Tag 就绪）。
     * runLockFlow 开始前/结束后各调用一次，原生层会合并重复请求。
     */
    prepareNextNfcRead: function () {
      if (!plugin() || !plugin().resetNfcForNextRead) {
        return Promise.resolve();
      }
      return new Promise(function (resolve) {
        plugin().resetNfcForNextRead(function () { resolve(); }, function () { resolve(); });
      });
    },

    /**
     * 完整开/关锁：查 ID → 取密码 → 原生充电链式轮询 + 电机
     * @param {'open'|'close'} perType
     * @returns {Promise<{success, lockId, lockPassword, motorAction, chargeMs}>} chargeMs = 充电结束−开始
     */
    runLockFlow: function (perType, options) {
      var self = this;
      options = options || {};
      var nfcCfg = cfg();
      var readFn = options.readPasswordFn || defaultReadPasswordFn;
      var operFail = options.operFail;
      var operSuccess = options.operSuccess;

      if (flowRunning) {
        return Promise.reject(new Error('流程执行中'));
      }

      return this.ensureInitialized(options).then(function () {
        flowRunning = true;
        stopNextStep = false;
        chargeStartMs = 0;
        chargeEndMs = 0;
        latestNativeChargeMs = -1;
        latestPowerLevel = -1;
        phase('start', { action: perType });

        return self.prepareNextNfcRead().then(function () {
        return (async function () {
          try {
            var idResult = await self.runStepWithRetry(
              '查询锁ID',
              nfcCfg.queryLockIdRetries || 3,
              nfcCfg.queryLockIdTimeoutMs || 3500,
              function () { self.queryLockId(); },
              function (resolve) { pendingQueryLockIdResolve = resolve; },
              function (r) { return !!(r && r.lockId); }
            );

            var lockId = String(idResult.lockId).trim();
            latestLockId = lockId;
            if (hooks.onLockId) hooks.onLockId(lockId);
            else log('锁ID: ' + lockId, 'success');
            /**根据锁ID获取锁密码 */
            phase('fetch_password', { lockId: lockId });
            var lockPassword = await readFn(lockId, '');
            if (!lockPassword) throw new Error('未获取到锁密码');
            latestLockPassword = String(lockPassword).trim();
            if (hooks.onPasswordReady) hooks.onPasswordReady(lockId, latestLockPassword);

            if (stopNextStep) throw new Error('流程已取消');

            phase('charging');
            log('进入充电阶段，等待贴卡充电...');

            phase('motor', { action: perType });
            await self.runMotorActionWithRetry(perType === 'open' ? '开锁' : '关锁', function () {
              plugin().runJsChargeAndMotor(lockId, latestLockPassword, perType, function () {}, function () {});
            });

            var chargeMs = resolveChargeDurationMs();
            var result = {
              success: true,
              lockId: lockId,
              lockPassword: latestLockPassword,
              motorAction: perType,
              chargeMs: chargeMs,
              chargeStartMs: chargeStartMs,
              chargeEndMs: chargeEndMs
            };
            phase('done', result);
            if (operSuccess && typeof operSuccess === 'function') operSuccess(result);
            return result;
          } catch (e) {
            if (operFail && typeof operFail === 'function') operFail(e);
            phase('failed', { message: e && e.message ? e.message : String(e) });
            throw e;
          } finally {
            clearPending();
            flowRunning = false;
            // 异步预热下一次读卡，不阻塞本次结果返回
            self.prepareNextNfcRead();
          }
        })();
        });
      });
    },

    /** 开锁（runLockFlow('open')） */
    openLock: function (options) {
      return this.runLockFlow('open', options);
    },

    /** 关锁（runLockFlow('close')） */
    closeLock: function (options) {
      return this.runLockFlow('close', options);
    },

    /** 自动交替开/关锁（偶数次开，奇数次关） */
    autoToggleLock: function (options) {
      var perType = getAutoToggleCount() % 2 === 0 ? 'open' : 'close';
      var self = this;
      return this.runLockFlow(perType, options).then(function (result) {
        incrementAutoToggleCount();
        return result;
      });
    },

    /**
     * @deprecated 请使用 runLockFlow / openLock / closeLock
     */
    autoToggleOpenLock: function (perType, readDeviceFn, operSuccessDealFn, operFailDealFn) {
      var options = { readPasswordFn: readDeviceFn };
      return this.runLockFlow(perType, options)
        .then(function (result) {
          if (operSuccessDealFn) operSuccessDealFn(true, result);
          return result;
        })
        .catch(function (e) {
          if (operFailDealFn) operFailDealFn(e);
          throw e;
        });
    }
  };

  global.NfcJrxUtil = NfcJrxUtil;
})(typeof window !== 'undefined' ? window : this);
