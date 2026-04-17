cordova.define('cordova/plugin_list', function(require, exports, module) {
  module.exports = [
    {
      "id": "cordova-plugin-nfc-lock.NFCLockPlugin",
      "file": "plugins/cordova-plugin-nfc-lock/www/NFCLockPlugin.js",
      "pluginId": "cordova-plugin-nfc-lock",
      "clobbers": [
        "cordova.plugins.NFCLockPlugin"
      ]
    }
  ];
  module.exports.metadata = {
    "cordova-plugin-nfc-lock": "1.0.0"
  };
});