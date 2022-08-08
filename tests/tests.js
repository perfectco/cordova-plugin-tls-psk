exports.defineAutoTests = () => {
  const key = Uint8Array.from([0x01, 0x23]);

  describe('TLS-PSK server', () => {
    const server = new window.cordova.plugins.tls_psk.TlsPskServer();
    afterEach(async () => {
      try {
        await new Promise((res, rej) => server.stop(res, rej));
      } catch (e) {}
    });

    [40000, undefined].forEach((port) => {
      it(`starts, and stops on port ${port}`, async () => {
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

    it('errors if not yet started', async () => {
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
    });
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
      try {
        await new Promise((res, rej) => client.close(res, rej));
      } catch (e) {}
      delete client.onReceive;
      await Promise.all(clients.map(async (client) => {
        return new Promise((res, rej) => client.close(res, rej));
      }));
      clients.length = 0;
      await new Promise((res, rej) => server.stop(res, rej));
      delete server.onReceive;
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

      await new Promise((res, rej) => client.close(res, rej));
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

    function dataToString(data) {
      return new TextDecoder('utf-8').decode(Uint8Array.from(data).buffer);
    }

    it('can send data from the client', async () => {
      const payload = 'foobar';
      let receive = new Promise((res, rej) => {
        server.onReceive = (conn, data) => res(dataToString(data));
        setTimeout(rej, 1000);
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));

      await new Promise((res, rej) => client.send(res, rej, payload));
      let received = await receive;

      expect(received).toBe(payload);

      await new Promise((res, rej) => client.close(res, rej));
    });

    it('can send data from the server', async () => {
      const payload = 'foobar';
      let receive = new Promise((res, rej) => {
        client.onReceive = (conn, data) => res(dataToString(data));
        setTimeout(rej, 1000);
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));

      await new Promise((res, rej) => clients[0].send(res, rej, payload));
      let received = await receive;

      expect(received).toBe(payload);

      await new Promise((res, rej) => client.close(res, rej));
    });
  });
}
