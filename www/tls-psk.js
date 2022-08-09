'use strict';
var exec = require('cordova/exec');

const TLS_PSK = 'tls_psk';

const ACTION_CONNECT = 'connect';
const ACTION_CLOSE = 'close';
const ACTION_SEND = 'send';
const ACTION_RECEIVE = 'receive';
const ACTION_START = 'start';
const ACTION_STOP = 'stop';
const ACTION_ON_ACCEPT = 'onAccept';
const ACTION_ON_RECEIVE = 'onReceive';
const ACTION_ON_CLOSE = 'onClose';

function toByteArrayOrString(data) {
  if (typeof data === 'string')
    return data;
  // coerce any iterable object to byte array
  return Array.from(Uint8Array.from(data));
}

class TlsPskSocket {
  close(success, failure) {
    exec((result) => {
      delete this.uuid;
      delete this.host;
      delete this.port;
      if (success) {
        success(result);
      }
    }, failure, TLS_PSK, ACTION_CLOSE, [this.uuid]);
  }

  send(success, failure, data) {
    data = toByteArrayOrString(data);
    exec(success, failure, TLS_PSK, ACTION_SEND, [this.uuid, data]);
  }

  connect() {
    exec((result) => {
      switch (result.action) {
        case ACTION_ON_RECEIVE:
          if (this.onReceive) {
            this.onReceive(this, result.data);
          }
          break;
        case ACTION_ON_CLOSE:
          if (this.onClose) {
            this.onClose(this);
          }
          break;
        }
    }, null, TLS_PSK, ACTION_RECEIVE, [this.uuid]);
  }
}

class TlsPskClientSocket extends TlsPskSocket {
  connect(success, failure, key, host, port) {
    if (this.uuid) {
      if (failure) {
        failure('Client already connected');
      }
      return;
    }
    key = toByteArrayOrString(key);
    exec((result) => {
      this.uuid = result.uuid;
      this.host = host;
      this.port = port;
      super.connect();
      if(success) {
        success('OK');
      }
    }, failure, TLS_PSK, ACTION_CONNECT, [key, host, port]);
  }
};

class TlsPskServer {
  start(success, failure, key, port) {
    if (this.uuid) {
      if (failure) {
        failure('Server already started');
      }
      return;
    }
    key = toByteArrayOrString(key);
    exec((result) => {
      switch (result.action) {
        case ACTION_ON_ACCEPT:
          if (this.onAccept) {
            var socket = new TlsPskSocket();
            socket.uuid = result.uuid;
            socket.host = result.host;
            socket.port = result.port;
            socket.onReceive = this.onReceive;
            socket.onClose = this.onClose;
            socket.connect();
            if (this.onAccept) {
              this.onAccept(socket);
            }
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
    }, failure, TLS_PSK, ACTION_START, [key, port]);
  }

  stop(success, failure) {
    exec((result) => {
      delete this.uuid;
      delete this.port;
      if (success) {
        success(result);
      }
    }, failure, TLS_PSK, ACTION_STOP, [this.uuid]);
  }
};

module.exports = Object.freeze({
  TlsPskClientSocket,
  TlsPskServer
});
