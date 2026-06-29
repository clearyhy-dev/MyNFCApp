/**
 * 权限锁密码服务：本地列表匹配 -> HTTP 接口 -> 可选 fallback
 */
(function (global) {
  var STORAGE_KEY = 'permission_lock_list';
  var sessionCache = Object.create(null);

  /** 读取全局 AppConfig 配置 */
  function getConfig() {
    return global.AppConfig || {};
  }

  /** 从 AppConfig 取默认锁密码（defaultLockPassword 或 fallbackPassword） */
  function defaultPassword() {
    var cfg = getConfig();
    return String(cfg.defaultLockPassword || cfg.fallbackPassword || 'D7UOebAQ').trim();
  }

  /** 是否允许在无匹配时使用 fallback 默认密码 */
  function useFallbackEnabled() {
    var cfg = getConfig();
    if (cfg.useFallbackWhenNoMatch || cfg.grantAllLocks) return true;
    try {
      return localStorage.getItem('nfc_use_fallback') === '1';
    } catch (e) {
      return false;
    }
  }

  /** 是否对任意锁 ID 授权（grantAllLocks 或 fallback 开启时视为全开） */
  function grantAllLocksEnabled() {
    var cfg = getConfig();
    if (cfg.grantAllLocks) return true;
    return useFallbackEnabled();
  }

  /**
   * 从对象或字符串中提取锁密码字段
   * 支持 password / lockPassword / pwd 及嵌套 data 结构
   */
  function extractPassword(data) {
    if (!data) return null;
    if (typeof data === 'string') return data.trim() || null;
    var pwd = data.password || data.lockPassword || data.pwd;
    if (!pwd && data.data) {
      pwd = data.data.password || data.data.lockPassword || data.data.pwd;
    }
    return pwd ? String(pwd).trim() : null;
  }

  var LockPermission = {
    /** 远程接口地址，空则不走 HTTP */
    apiUrl: '',
    /** HTTP 方法：GET 或 POST */
    apiMethod: 'GET',
    /** GET/POST 请求中传递锁 ID 的参数名（默认 lockId） */
    apiLockIdParam: 'lockId',
    /** 接口请求超时（毫秒） */
    apiTimeoutMs: 10000,
    /** 是否对全部锁 ID 授权 */
    grantAllLocks: true,
    /** 无匹配时是否使用 fallback 密码 */
    useFallbackWhenNoMatch: true,
    /** fallback 默认密码 */
    fallbackPassword: 'D7UOebAQ',
    /** 自定义解析接口响应，返回密码字符串；未设置则用 extractPassword */
    parseApiResponse: null,

    /**
     * 从 AppConfig 同步 apiUrl、超时、grantAllLocks、fallback 等配置
     * 模块加载时自动调用一次
     */
    applyConfig: function () {
      var cfg = getConfig();
      if (cfg.lockPasswordApiUrl) this.apiUrl = cfg.lockPasswordApiUrl;
      if (cfg.lockPasswordApiMethod) this.apiMethod = cfg.lockPasswordApiMethod;
      if (cfg.lockPasswordApiTimeoutMs) this.apiTimeoutMs = cfg.lockPasswordApiTimeoutMs;
      if (cfg.lockPasswordApiLockIdParam) this.apiLockIdParam = cfg.lockPasswordApiLockIdParam;
      this.useFallbackWhenNoMatch = !!cfg.useFallbackWhenNoMatch || !!cfg.grantAllLocks;
      if (cfg.defaultLockPassword) this.fallbackPassword = cfg.defaultLockPassword;
      else if (cfg.fallbackPassword) this.fallbackPassword = cfg.fallbackPassword;
      this.grantAllLocks = !!cfg.grantAllLocks;
    },

    /** 清空本次会话的锁 ID -> 密码内存缓存 */
    clearSessionCache: function () {
      sessionCache = Object.create(null);
    },

    /**
     * 保存权限锁列表到 localStorage
     * @param {Array<{lockId:string, password?:string}>} list 锁 ID 与密码列表
     */
    setPermissionLockList: function (list) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(list || []));
      } catch (e) {
        console.warn('保存权限锁列表失败', e);
      }
    },

    /**
     * 从 localStorage 读取权限锁列表
     * @returns {Array} 锁权限条目数组
     */
    getPermissionLockList: function () {
      try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      } catch (e) {
        return [];
      }
    },

    /**
     * 在本地权限列表中按锁 ID 精确匹配密码
     * @param {string} lockId 锁 ID
     * @returns {string|null} 匹配到的密码，无则 null
     */
    matchLocal: function (lockId) {
      var id = String(lockId || '').trim();
      if (!id) return null;
      var list = this.getPermissionLockList();
      for (var i = 0; i < list.length; i++) {
        var item = list[i] || {};
        var itemId = String(item.lockId || item.lockID || item.id || '').trim();
        if (itemId && itemId === id) {
          return extractPassword(item);
        }
      }
      return null;
    },

    /**
     * 通过 HTTP 接口按锁 ID 拉取密码（必传 lockId）
     * GET：{apiUrl}?{apiLockIdParam}={lockId}
     * POST：body { [apiLockIdParam]: lockId }
     * @param {string} lockId 贴卡读到的锁 ID
     * @returns {Promise<string>} 锁密码
     */
    fetchFromApi: function (lockId) {
      var self = this;
      if (!this.apiUrl) {
        return Promise.reject(new Error('未配置权限锁密码接口'));
      }

      var id = String(lockId || '').trim();
      if (!id) {
        return Promise.reject(new Error('锁ID为空，无法请求接口'));
      }

      var paramKey = this.apiLockIdParam || 'lockId';
      var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
      var timer = controller
        ? setTimeout(function () {
            controller.abort();
          }, this.apiTimeoutMs)
        : null;

      var url = this.apiUrl;
      var options = {
        method: this.apiMethod || 'GET',
        headers: { Accept: 'application/json' }
      };

      if (controller) options.signal = controller.signal;

      if ((this.apiMethod || 'GET').toUpperCase() === 'POST') {
        options.headers['Content-Type'] = 'application/json';
        var body = {};
        body[paramKey] = id;
        options.body = JSON.stringify(body);
      } else if (url.indexOf('?') >= 0) {
        url += '&' + encodeURIComponent(paramKey) + '=' + encodeURIComponent(id);
      } else {
        url += '?' + encodeURIComponent(paramKey) + '=' + encodeURIComponent(id);
      }

      return fetch(url, options)
        .then(function (res) {
          if (!res.ok) throw new Error('获取锁密码失败 HTTP ' + res.status);
          return res.json();
        })
        .then(function (data) {
          if (self.parseApiResponse) return self.parseApiResponse(data, lockId);
          return extractPassword(data);
        })
        .then(function (pwd) {
          if (!pwd) throw new Error('接口未返回锁密码');
          return pwd;
        })
        .finally(function () {
          if (timer) clearTimeout(timer);
        });
    },

    /**
     * 根据锁 ID 获取密码（供 nfcJrx 开/关锁流程调用）
     * 优先级：会话缓存 -> 本地列表 -> HTTP 接口（传 lockId）-> grantAll/fallback 默认密码
     * @param {string} lockId 贴卡读到的锁 ID
     * @returns {Promise<string>} 锁密码
     */
    fetchPassword: function (lockId) {
      var self = this;
      var id = String(lockId || '').trim();
      if (!id) return Promise.reject(new Error('锁ID为空'));

      if (sessionCache[id]) {
        return Promise.resolve(sessionCache[id]);
      }

      var local = this.matchLocal(id);
      if (local) {
        sessionCache[id] = local;
        return Promise.resolve(local);
      }

      if (this.apiUrl) {
        return this.fetchFromApi(id).then(function (pwd) {
          sessionCache[id] = pwd;
          return pwd;
        });
      }

      if (grantAllLocksEnabled()) {
        var pwd = defaultPassword();
        sessionCache[id] = pwd;
        return Promise.resolve(pwd);
      }

      return Promise.reject(new Error('未匹配到锁ID「' + id + '」的权限数据'));
    }
  };

  LockPermission.applyConfig();
  global.LockPermission = LockPermission;
})(typeof window !== 'undefined' ? window : this);
