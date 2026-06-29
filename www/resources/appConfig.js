/**
 * 应用运行时配置（集中管理，便于对接线上环境）
 */
(function (global) {
  global.AppConfig = {
    /** 权限锁密码接口；配置后 fetchPassword 会携带 lockId 请求 */
    lockPasswordApiUrl: '',
    lockPasswordApiMethod: 'GET',
    lockPasswordApiTimeoutMs: 10000,
    /** 请求中锁 ID 字段名：GET 为 query 参数名，POST 为 JSON body 字段名 */
    lockPasswordApiLockIdParam: 'lockId',

    /** 默认锁密码（仅 grantAllLocks / fallback 时使用，不走接口） */
    defaultLockPassword: 'D7UOebAQ',
    fallbackPassword: 'D7UOebAQ',

    /**
     * grantAllLocks：是否对「任意锁 ID」直接授权（演示/调试常用）
     * - true：不校验权限；本地/接口都拿不到密码时，一律使用 defaultLockPassword
     * - false：仅本地列表或线上接口返回的锁 ID 才有权限；否则 fetchPassword 报错
     * 对接线上权限接口后建议改为 false
     */
    grantAllLocks: true,
    /**
     * useFallbackWhenNoMatch：无匹配时是否用默认密码兜底
     * - true：本地列表无此 lockId、且未配接口或接口失败时，仍返回 defaultLockPassword
     * - false：必须本地或接口明确返回密码，否则报错「未匹配到锁ID」
     * 注意：grantAllLocks 为 true 时，本项效果会被覆盖（等效于全开）
     */
    useFallbackWhenNoMatch: true,

    /** NFC 流程超时（与线上 nfcJrx 对齐，可按现场调优） */
    nfc: {
      queryLockIdTimeoutMs: 3500,
      queryLockIdRetries: 3,
      queryLockPasswordTimeoutMs: 3500,
      queryLockPasswordRetries: 2,
      queryPowerTimeoutMs: 1200,
      queryPowerRetries: 2,
      motorTimeoutMs: 8000,
      motorRetries: 4,
      chargeMaxWaitMs: 90000
    },

  };
})(typeof window !== 'undefined' ? window : this);
