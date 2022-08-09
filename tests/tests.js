exports.defineAutoTests = () => {
  const key = Uint8Array.from([0x01, 0x23]);

  // Internal API calls for testing
  function getInternalServerCount() {
    return new Promise((res) => window.cordova.exec(res, null, 'tls_psk', 'getServerCount'));
  }

  function getInternalClientCount() {
    return new Promise((res) => window.cordova.exec(res, null, 'tls_psk', 'getClientCount'));
  }

  describe('TLS-PSK server', () => {
    const server = new window.cordova.plugins.tls_psk.TlsPskServer();
    afterEach(async () => {
      await new Promise((res) => server.stop(res, res));
      expect(await getInternalServerCount()).toBe(0);
    });

    [40000, undefined].forEach((port) => {
      it(`starts and stops on port ${port}`, async () => {
        expect(server.uuid).toBeUndefined();
        expect(server.port).toBeUndefined();

        let result = await new Promise((res, rej) => server.start(res, rej, key, port));
        expect(result).toBe('OK');
        expect(server.uuid).toBeDefined();
        expect(server.port).toBeDefined();
        if (port) {
          expect(server.port).toBe(port);
        }

        result = await new Promise((res, rej) => server.stop(res, rej));
        expect(result).toBe('OK');
        expect(server.uuid).toBeUndefined();
        expect(server.port).toBeUndefined();
      })
    });

    it('errors if already started', async () => {
      expect(await new Promise((res, rej) => server.start(res, rej, key))).toBe('OK');

      let error;
      try {
        await new Promise((res, rej) => server.start(res, rej, key));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Server already started');
    });

    it('errors on stop if not yet started', async () => {
      let error;
      try {
        await new Promise((res, rej) => server.stop(res, rej));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Unknown server');
    });

    it('errors on restricted port', async () => {
      let error;
      try {
        await new Promise((res, rej) => server.start(res, rej, key, 9));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Start error');
      expect(server.uuid).toBeUndefined();
      expect(server.port).toBeUndefined();
    });
  });

  describe('TLS-PSK client', () => {
    it('errors if server not available', async () => {
      const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();

      let error;
      try {
        await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', 9));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Connect error');
      expect(client.uuid).toBeUndefined();
      expect(client.host).toBeUndefined();
      expect(client.port).toBeUndefined();
      expect(await getInternalClientCount()).toBe(0);
    });

    ['send', 'close'].forEach((method) => it(`errors on ${method} if not connected`, async () => {
      const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();

      let error;
      try {
        await new Promise((res, rej) => client[method](res, rej, 'payload'));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Unknown client');
      expect(await getInternalClientCount()).toBe(0);
    }));
  });

  describe('TLS-PSK sockets', () => {
    const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();
    const server = new window.cordova.plugins.tls_psk.TlsPskServer();
    const clients = [];
    server.onAccept = (socket) => clients.push(socket);

    beforeEach(async () => {
      await new Promise((res, rej) => server.start(res, rej, key));
    });

    afterEach(async () => {
      await new Promise((res) => client.close(res, res));
      delete client.onReceive;
      delete client.onClose;
      await Promise.all(clients.map(async (client) => {
        return new Promise((res) => client.close(res, res));
      }));
      clients.length = 0;
      await new Promise((res, rej) => server.stop(res, rej));
      delete server.onReceive;
      expect(await getInternalClientCount()).toBe(0);
      expect(await getInternalServerCount()).toBe(0);
    });

    it('accepts client connections', async () => {
      let result = await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      expect(result).toBe('OK');
      expect(client.host).toBe('localhost');
      expect(client.port).toBe(server.port);
      expect(clients.length).toBe(1);

      result = await new Promise((res, rej) => client.close(res, rej));
      expect(client.host).toBeUndefined();
      expect(client.port).toBeUndefined();
    });

    it('errors if already connected', async () => {
      expect(await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port))).toBe('OK');

      let error;
      try {
        await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Client already connected');
    });

    it('errors on incorrect key', async () => {
      const badKey = Uint8Array.from([0xAB, 0xCD, 0xEF]);

      let error;
      try {
        await new Promise((res, rej) => client.connect(res, rej, badKey, 'localhost', server.port));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Connect error');
      expect(client.uuid).toBeUndefined();
      expect(client.host).toBeUndefined();
      expect(client.port).toBeUndefined();
    });

    it('errors on multiple clients w/ same key', async () => {
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      const client2 = new window.cordova.plugins.tls_psk.TlsPskClientSocket();

      let error;
      try {
        await new Promise((res, rej) => client2.connect(res, rej, key, 'localhost', server.port));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Connect error');
    });

    function dataToString(data) {
      return new TextDecoder('utf-8').decode(Uint8Array.from(data).buffer);
    }

    it('can send data from the client', async () => {
      const payload = 'foobar';
      let receive = new Promise((res) => {
        server.onReceive = (conn, data) => res(dataToString(data));
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      await new Promise((res, rej) => client.send(res, rej, payload));

      expect(await receive).toBe(payload);
    });

    it('can send data from the server', async () => {
      const payload = 'foobar';
      let receive = new Promise((res) => {
        client.onReceive = (conn, data) => res(dataToString(data));
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      await new Promise((res, rej) => clients[0].send(res, rej, payload));

      expect(await receive).toBe(payload);
    });

    it('detects close from the client', async () => {
      let close = new Promise((res) => {
        server.onClose = (conn) => res(conn);
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      await new Promise((res, rej) => client.close(res, rej));

      expect(await close).toBe(clients[0]);
    });

    it('detects close from the server', async () => {
      let close = new Promise((res) => {
        client.onClose = (conn) => res(conn);
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      await new Promise((res, rej) => clients[0].close(res, rej));

      expect(await close).toBe(client);
    });

    it('handles multiple servers and clients', async () => {
      const key2 = Uint8Array.from([0xAB, 0xCD]);
      const server2 = new window.cordova.plugins.tls_psk.TlsPskServer();
      server2.onAccept = server.onAccept;
      const client2 = new window.cordova.plugins.tls_psk.TlsPskClientSocket();
      try {
        await new Promise((res, rej) => server2.start(res, rej, key2));
        await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
        await new Promise((res, rej) => client2.connect(res, rej, key2, 'localhost', server2.port));

        expect(clients.length).toBe(2);
        expect(await getInternalClientCount()).toBe(4); // x2 for server-side sockets
      } finally {
        try {
          await new Promise((res, rej) => client2.close(res, rej));
        } finally {
          await new Promise((res, rej) => server2.stop(res, rej));
        }
      }
    });
  });
}
