'use strict';
var exec = require('cordova/exec');

class TlsPskClientSocket {
  connect(host, port, key, success, failure) {
    exec(success, failure, "tls_psk", "connect", [host, port, key]);
  }

  close() {}

  send(data) {}
};

class TlsPskServerSocket {
  start(port, key) {}

  stop() {}
};

const tls_psk = {
  TlsPskClientSocket,
  TlsPskServerSocket
};

module.exports = tls_psk;
