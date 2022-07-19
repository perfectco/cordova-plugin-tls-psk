'use strict';
var exec = require('cordova/exec');

function toByteArrayOrString(data) {
  if (typeof data === "string")
    return data;
  // coerce any iterable object to byte array
  return Array.from(Uint8Array.from(data));
}

class TlsPskClientSocket {
  connect(host, port, key, success, failure) {
    key = toByteArrayOrString(key);
    exec((c) => {
      this.uuid = c.uuid;
      success("OK");
    }, failure, "tls_psk", "connect", [host, port, key]);
  }

  close(success, failure) {
    exec((c) => {
      delete this.uuid;
      success(c);
    }, failure, "tls_psk", "close", [this.uuid]);
  }

  send(data, success, failure) {
    data = toByteArrayOrString(data);
    exec(success, failure, "tls_psk", "send", [this.uuid, data]);
  }
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
