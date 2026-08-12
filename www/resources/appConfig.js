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

    /**
     * grantAllLocks：是否对任意锁 ID 跳过权限校验（仅配合 lockPasswordApiUrl / 本地列表使用）
     * 开/关锁默认从 NFC 卡读取密码，不再使用固定默认密码
     */
    grantAllLocks: false,
    /** 无本地/接口匹配时是否兜底；开/关锁流程不走此项，须贴卡读密码 */
    useFallbackWhenNoMatch: false,

    /** NFC 流程超时（与线上 nfcJrx 对齐，可按现场调优） */
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
    },

  };
})(typeof window !== 'undefined' ? window : this);
