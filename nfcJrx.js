import {ref} from "vue";

const isPluginInitialized = ref(false)
const displayLockId = ref('')
const displayPassword = ref('')

// 自动流程相关变量
let autoRunning = false
let pendingQueryLockIdResolve = null
let pendingQueryLockPasswordResolve = null
let pendingQueryPowerResolve = null
let pendingMotorResolve = null
let latestLockState = null
let latestPowerLevel = -1
let latestLockId = ''
let latestLockPassword = ''
let $this;
let operSuccessFn;

let stopNextStep = false;

class NfcJrxUtil{
  /**
   * 停止读取NFC并注销注册
   */
  stopReadNFCJrx() {
    stopNextStep = true
    cordova.plugins.NFCLockPlugin.stopReadNFC(function (ok) {
      console.log(`停止读取NFC指令已发送: ${ok}`, 'success')
    },  function (err) {
      console.log(`停止读取NFC失败: ${err}`, 'error')
    })
  }
  // 初始化插件
  initPluginJrx() {
    console.log('初始化插件...')
    $this = this;
    stopNextStep = false
    cordova.plugins.NFCLockPlugin.init(
      function(res) {
        isPluginInitialized.value = true
        console.log(`插件初始化成功: ${res}`, 'success')
        $this.registerCallbackJrx()
      },
      function(err) {
        console.log(`插件初始化失败: ${err}`, 'error')
      }
    )
  }
  // 查询锁ID
  queryLockId() {
    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }

    console.log('查询锁ID...')
    cordova.plugins.NFCLockPlugin.queryLockId(
      function(ok) {
        console.log(`查询锁ID请求已发送: ${ok}`, 'success')
      },
      function(err) {
        console.log(`查询锁ID失败: ${err}`, 'error')
      }
    )
  }

  // 查询锁密码
  queryLockPassword() {
    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }

    console.log('查询锁密码...')
    cordova.plugins.NFCLockPlugin.queryLockPassword(
      function(ok) {
        console.log(`查询锁密码请求已发送: ${ok}`, 'success')
      },
      function(err) {
        console.log(`查询锁密码失败: ${err}`, 'error')
      }
    )
  }

  // 查询锁状态
  queryLockStatusWithCredential(lockId, lockPassword) {
    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }

    cordova.plugins.NFCLockPlugin.queryLockStatus(
      lockId,
      lockPassword,
      function(ok) {
        console.log(`查询状态请求已发送: ${ok}`, 'success')
      },
      function(err) {
        console.log(`查询状态失败: ${err}`, 'error')
      }
    )
  }

  // 开锁
  openLock() {
    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }

    const id = displayLockId.value.trim()
    const pwd = displayPassword.value.trim()

    if (!id || !pwd) {
      console.log('请先查询或填写锁ID/密码', 'error')
      return
    }

    console.log('发送开锁...')
    cordova.plugins.NFCLockPlugin.motorForward(
      id,
      pwd,
      function(ok) {
        console.log(`开锁指令已发送: ${ok}`, 'success')
      },
      function(err) {
        console.log(`开锁失败: ${err}`, 'error')
      }
    )
  }

  // 关锁
  closeLock() {
    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }

    const id = displayLockId.value.trim()
    const pwd = displayPassword.value.trim()

    if (!id || !pwd) {
      console.log('请先查询或填写锁ID/密码', 'error')
      return
    }

    console.log('发送关锁...')
    cordova.plugins.NFCLockPlugin.motorReverse(
      id,
      pwd,
      function(ok) {
        console.log(`关锁指令已发送: ${ok}`, 'success')
      },
      function(err) {
        console.log(`关锁失败: ${err}`, 'error')
      }
    )
  }

  normalizeRespType(result) {
    if (!result || !result.respCmdType) return ''
    return String(result.respCmdType)
  }

  // 注册回调
  registerCallbackJrx() {
    cordova.plugins.NFCLockPlugin.registerCallback(
      function(result) {
        const respType = $this.normalizeRespType(result)
        const isQueryIdResp = !!(result && (result.type === 'queryLockId' || respType === 'RESP_QUERY_LOCK_ID'))
        const isQueryPwdResp = !!(result && (result.type === 'queryLockPassword' || respType === 'RESP_QUERY_LOCK_PWD'))

        if (result && result.lockId) {
          const id = String(result.lockId).trim()
          if (id) {
            latestLockId = id
            displayLockId.value = id
          }
        }
        if ((isQueryIdResp || (result && result.lockId)) && pendingQueryLockIdResolve) {
          pendingQueryLockIdResolve(result || {})
          pendingQueryLockIdResolve = null
        }

        if (isQueryPwdResp && result && result.lockPassword) {
          latestLockPassword = String(result.lockPassword).trim()
          displayPassword.value = latestLockPassword
        }

        if (isQueryPwdResp && pendingQueryLockPasswordResolve) {
          pendingQueryLockPasswordResolve(result || {})
          pendingQueryLockPasswordResolve = null
        }

        if (result && result.type === 'queryPowerLevel' && result.powerLevel != null) {
          latestLockState = result.lockState
          latestPowerLevel = Number(result.powerLevel)
          console.log(`当前电量: ${result.powerLevel}%`, 'success')

          if (pendingQueryPowerResolve) {
            pendingQueryPowerResolve(result)
            pendingQueryPowerResolve = null
          }
        }

        if (result && (result.type === 'motorForward' || result.motorForwardSuccess !== undefined)) {
          console.log(`开锁:`, `${result.motorForwardSuccess ? '已开锁' : '开锁失败'}`)
          console.log(`开锁回调:`, typeof $this.operSuccessFn)
          if ($this.operSuccessFn && typeof $this.operSuccessFn === 'function'){
            $this.operSuccessFn(result.motorForwardSuccess)
          }
          if (result.motorForwardSuccess) {
            // 开锁成功
            latestLockState = '0'
          }
          if (pendingMotorResolve) {
            pendingMotorResolve({ ok: !!result.motorForwardSuccess, action: 'open', result })
            pendingMotorResolve = null
          }
        }

        if (result && (result.type === 'motorReverse' || result.motorReverseSuccess !== undefined)) {
          console.log(`关锁:`, `${result.motorReverseSuccess ? '已关锁' : '关锁失败'}`)
          console.log(`关锁回调:`, typeof $this.operSuccessFn)
          if ($this.operSuccessFn && typeof $this.operSuccessFn === 'function'){
            $this.operSuccessFn(result.motorReverseSuccess)
          }
          if (result.motorReverseSuccess) {
            // 关锁成功
            latestLockState = '2'
          }

          if (pendingMotorResolve) {
            pendingMotorResolve({ ok: !!result.motorReverseSuccess, action: 'close', result })
            pendingMotorResolve = null
          }
        }
      },
      function(error) {
        console.log(`回调错误: ${JSON.stringify(error)}`, 'error')
      }
    )
  }
  withTimeout(ms, executor) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('超时，请将NFC卡靠近设备后重试')), ms)
      executor((value) => {
        clearTimeout(timer)
        resolve(value)
      })
    })
  }
  sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }

  async runStepWithRetry(stepText, attempts, timeoutMs, fireCommand, pendingSetter, resultValidator) {
    let lastErr = null
    for (let i = 1; i <= attempts; i++) {
      try {
        if (i > 1) {
          console.log(`${stepText} 第${i}次重试...`)
          await $this.sleep(200)
        }

        const result = await $this.withTimeout(timeoutMs, (resolve) => {
          pendingSetter(resolve)
          fireCommand()
        })
        if (resultValidator && !resultValidator(result)) {
          throw new Error(`${stepText} 回包无效`)
        }
        return result
      } catch (e) {
        lastErr = e
        pendingSetter(null)
      }
    }
    throw lastErr || new Error(`${stepText} 失败`)
  }

  async runMotorActionWithRetry(actionName, fireCommand) {
    let lastErr = null
    for (let i = 1; i <= 4; i++) {
      try {
        if (i > 1) {
          console.log(`${actionName} 第${i}次重试（无需手动再点）...`)
          await $this.sleep(200)
        }
        await $this.withTimeout(1100, (resolve) => {
          pendingMotorResolve = resolve
          fireCommand()
        })
        return true
      } catch (e) {
        lastErr = e
        pendingMotorResolve = null
      }
    }
    throw lastErr || new Error(`${actionName} 执行失败`)
  }

  /**
   * 自动开锁或关锁
   * @param perType  open or close
   * @param readDeviceFn
   * @param operSuccessDealFn
   * @param operFailDealFn
   * @returns {Promise<void>}
   */
  async autoToggleOpenLock(perType, readDeviceFn, operSuccessDealFn, operFailDealFn) {
    $this.operSuccessFn = operSuccessDealFn

    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }

    try {
      console.log('开始自动开锁或关锁流程：查询ID -> 查询密码 -> 查询状态 -> 执行开关锁')
      const idResult = await $this.runStepWithRetry(
        '查询锁ID',
        2,
        1000,
        () => $this.queryLockId(),
        (resolve) => { pendingQueryLockIdResolve = resolve },
        (r) => !!(r && r.lockId)
      )
      const lockId = (idResult && idResult.lockId ? String(idResult.lockId) : '').trim()
      if (!lockId) {
        throw new Error('未读取到锁ID')
      }

      // const pwdResult = await $this.runStepWithRetry(
      //   '查询锁密码',
      //   4,
      //   1100,
      //   () => $this.queryLockPassword(),
      //   (resolve) => { pendingQueryLockPasswordResolve = resolve },
      //   (r) => !!(r && r.lockPassword)
      // )
      // const lockPassword = (pwdResult && pwdResult.lockPassword ? String(pwdResult.lockPassword) : '').trim()
      // if (!lockPassword) {
      //   throw new Error('未读取到锁密码')
      // }

      latestLockId = lockId
      let lockPassword = undefined;
      if(readDeviceFn && typeof readDeviceFn === 'function'){
        lockPassword = await readDeviceFn(lockId, '');
        if (!lockPassword) {
          // 不在继续往下操作
          return;
        }
      }
      latestLockPassword = lockPassword
      console.log(`锁密码：`, lockPassword)

      console.log(`停止：下一步查询锁状态`, stopNextStep)
      if (stopNextStep) {
        console.log(`停止：下一步查询锁状态`)
        return;
      }

     /* await $this.runStepWithRetry(
        '查询锁状态',
        2,
        1100,
        () => $this.queryLockStatusWithCredential(lockId, lockPassword),
        (resolve) => { pendingQueryPowerResolve = resolve },
        (r) => !!(r && (r.lockState !== undefined && r.lockState !== null))
      )

      const powerResult = { lockState: latestLockState }
      const currentCode = String(powerResult && powerResult.lockState != null ? powerResult.lockState : '')
      if (currentCode !== '0' && currentCode !== '1' && currentCode !== '2') {
        throw new Error(`无法识别当前锁状态码（lockState=${currentCode}）`)
      }

      const targetCode = currentCode === '0' ? '2' : '0'
      console.log(`识别到当前锁状态码: ${currentCode}，目标状态码: ${targetCode}`)*/

      // 充电过程
      if (!(latestPowerLevel === 100)) {
        const chargeStart = Date.now()
        const maxChargeWaitMs = 90000

        while (latestPowerLevel !== 100 && (Date.now() - chargeStart) < maxChargeWaitMs) {
          console.log(`充电检测中：当前电量${latestPowerLevel}%，等待达到100%...`)
          await $this.sleep(900)

          try {
            await $this.withTimeout(1800, (resolve) => {
              pendingQueryPowerResolve = resolve
              $this.queryLockStatusWithCredential(lockId, lockPassword)
            })
          } catch (_e) {
            pendingQueryPowerResolve = null
          }
        }

        if (latestPowerLevel !== 100) {
          throw new Error(`充电等待超时，当前电量${latestPowerLevel}%，请重新尝试自动开锁或关锁`)
        }
        console.log('电量已达到100%，开始执行开关锁', 'success')
      }
      // const targetAction = currentCode === '0' ? 'close' : 'open'

      console.log(`停止：下一步开关锁操作`, stopNextStep)
      if (stopNextStep) {
        console.log(`停止：下一步开关锁操作`)
        return;
      }

      if (perType === 'open') {
        // console.log(`执行状态码切换：${currentCode} -> ${targetCode}（调用开锁动作）`)
        await $this.runMotorActionWithRetry('开锁', () => {
          cordova.plugins.NFCLockPlugin.motorForward(
            lockId,
            lockPassword,
            function(ok) { console.log(`开锁指令已发送: ${ok}`, 'success') },
            function(err) { console.log(`开锁失败: ${err}`, 'error') }
          )
        })
      } else {
        // console.log(`执行状态码切换：${currentCode} -> ${targetCode}（调用关锁动作）`)
        await $this.runMotorActionWithRetry('关锁', () => {
          cordova.plugins.NFCLockPlugin.motorReverse(
            lockId,
            lockPassword,
            function(ok) { console.log(`关锁指令已发送: ${ok}`, 'success') },
            function(err) { console.log(`关锁失败: ${err}`, 'error') }
          )
        })
      }
      console.log('自动流程完成', 'success')
    } catch (e) {
      console.log(`自动流程失败: ${e && e.message ? e.message : e}`, 'error')
      if (operFailDealFn && typeof operFailDealFn === 'function'){
        operFailDealFn(e);
      }
    } finally {
      pendingQueryLockIdResolve = null
      pendingQueryLockPasswordResolve = null
      pendingQueryPowerResolve = null
      pendingMotorResolve = null
      autoRunning = false
    }
  }

  /**
   * 读取锁ID
   * @param readDeviceFn
   * @param readFailDealFn
   * @returns {Promise<void>}
   */
  async autoReadLockId(readDeviceFn, readFailDealFn) {
    if (!isPluginInitialized.value) {
      console.log('请先初始化插件', 'error')
      return
    }
    try {
      console.log('开始自动开锁或关锁流程：查询ID -> 查询密码 -> 查询状态 -> 执行开关锁')
      const idResult = await $this.runStepWithRetry(
        '查询锁ID',
        2,
        1000,
        () => $this.queryLockId(),
        (resolve) => { pendingQueryLockIdResolve = resolve },
        (r) => !!(r && r.lockId)
      )
      const lockId = (idResult && idResult.lockId ? String(idResult.lockId) : '').trim()
      if (!lockId) {
        throw new Error('未读取到锁ID')
      }

      if (readDeviceFn && typeof readDeviceFn === 'function') {
        readDeviceFn(lockId)
      }
    } catch (e){
      if (readFailDealFn && typeof readFailDealFn === 'function'){
        readFailDealFn(e);
      }
    }
  }
}
export default new NfcJrxUtil()
