# Cordova TLS-PSK plugin

This implements a cordova plugin for network communication over Transport Layer Security (TLS) in Pre-shared Key (PSK) mode.
Both client and server are supported.
Currently, the only supported platform is Android.

## Usage

Usage of this plugin involves two main classes: `TlsPskServer` and `TlsPskClientSocket`.
All methods are asynchronous, taking functions to call on success or failure.
Keys are specified as binary data encoded in `Uint8Array`s.

### Client

To connect to a TLS-PSK server, create an instance of the `TlsPskClientSocket` class.

```javascript
const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();

const key = Uint8Array.from([...]);
client.connect(onSuccess, onFailure, key, host, port);
```

To close the socket, call `close()`:

```javascript
client.close(onSuccess, onFailure);
```

### Server

To start a new TLS-PSK server, first create an instance of the `TlsPskServer` class.

```javascript
const server = new window.cordova.plugins.tls_psk.TlsPskServer();
```

Call the `start()` method, specifying the PSK to use.
You can optionally specify a TCP port to listen on as well.

```javascript
const key = Uint8Array.from([...]);
server.start(onSuccess, onFailure, key, port);
```

If `port` is not specified, an available port is chosen and you can retrieve it from `server.port` after starting:

```javascript
function onSuccess() {
  let port = server.port;
  ...
}
```

To stop the server, call the `stop()` method:

```javascript
server.stop(onSuccess, onFailure);
```

When a client connects, the `onAccept()` handler will be called, if it exists:

```javascript
server.onAccept = function (client) {
  ...
};
```

`client` is the socket instance of the newly connected client.
If you need the IP address or port of this client, they are available on the `client.host` and `client.port` fields.

### Send and receive

Whether the socket is created as a client, or accepted by a server, you can send data over it using the `send()` method.
If the data to send isn't a `string`, it will be coerced into a byte array before sending.

```javascript
client.send(onSuccess, onFailure, data);
```

Likewise, data received by the socket is handled by an `onReceive()` handler, if specified:

```javascript
client.onReceive = function (client, data) {
  ...
};
```

The `client` parameter is the socket instance that is receiving the data.
The `data` passed to this handler will be an array of byte values.

If the remote side closes the connection, `onClose()` will be called if defined:

```javascript
client.onClose = function (client) {
  ...
}
```

Again, `client` is the instance that was closed.

In the case of server-side sockets, you can specify `server.onReceive` and `server.onClose` to automatically apply those handlers to connecting clients.

## Examples

Server:

```javascript
const server = new window.cordova.plugins.tls_psk.TlsPskServer();
const key = Uint8Array.from([1, 2, 3, 4]);

server.onAccept = (client) => {
  console.log(`Accepted new client from ${client.host}:${client.port}`);
  client.send(console.log, console.log, 'payload from server');
};
server.onReceive = (client, data) => {
  console.log(new TextDecoder('utf-8').decode(Uint8Array.from(data).buffer));
};
server.onClose = (client) => {
  console.log(`Client from ${client.host}:${client.port} disconnected`);
};

await new Promise((s, f) => server.start(s, f, key, 8000));

...

await new Promise((s, f) => server.stop(s, f));
```

Client:

```javascript
const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();
const key = Uint8Array.from([1, 2, 3, 4]);

client.onReceive = (client, data) => {
  console.log(new TextDecoder('utf-8').decode(Uint8Array.from(data).buffer));
}
client.onClose = (client) => {
  console.log(`Server on port ${client.port} disconnected`);
}

await new Promise((s, f) => client.connect(s, f, key, 'example.com', 8000));

...

await new Promise((s, f) => client.send(s, f, 'payload from client'));

...

await new Promise((s, f) => client.close(s, f));
```

For more examples, see the test suite located in `tests/test.js`
