'use strict';
var exec = require('cordova/exec');

function toByteArrayOrString(data) {
  if (typeof data === 'string')
    return data;
  // coerce any iterable object to byte array
  return Array.from(Uint8Array.from(data));
}

class TlsPskSocket {
  close(success, failure) {
    exec((c) => {
      delete this.uuid;
      success(c);
    }, failure, 'tls_psk', 'close', [this.uuid]);
  }

  send(data, success, failure) {
    data = toByteArrayOrString(data);
    exec(success, failure, 'tls_psk', 'send', [this.uuid, data]);
  }

  connect() {
    exec((result) => {
      if (this.onReceive) {
        this.onReceive(this, result.buffer);
      }
    }, null, 'tls_psk', 'receive', [this.uuid]);
  }
}

class TlsPskClientSocket extends TlsPskSocket {
  connect(key, host, port, success, failure) {
    key = toByteArrayOrString(key);
    exec((result) => {
      this.uuid = result.uuid;
      this.host = host;
      this.port = port;
      super.connect();
      success('OK');
    }, failure, 'tls_psk', 'connect', [key, host, port]);
  }
};

class TlsPskServer {
  start(key, port, success, failure) {
    key = toByteArrayOrString(key);
    exec((result) => {
      switch (result.action) {
        case 'onAccept':
          if (this.onAccept) {
            var socket = new TlsPskSocket();
            socket.uuid = result.uuid;
            socket.host = result.host;
            socket.port = result.port;
            socket.onReceive = this.onReceive;
            socket.connect();
            this.onAccept(socket);
          }
          break;
        default:
          this.uuid = result.uuid;
          this.port = result.port;
          if (success) {
            success('OK');
          }
          break;
      }
    }, failure, 'tls_psk', 'start', [key, port]);
  }

  stop(success, failure) {
    exec(success, failure, 'tls_psk', 'stop', [this.uuid]);
  }
};

const tls_psk = {
  TlsPskClientSocket,
  TlsPskServer
};

module.exports = tls_psk;
