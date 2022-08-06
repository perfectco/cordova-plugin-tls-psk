exports.defineAutoTests = () => {
  describe('TLS-PSK server', () => {
    const server = new window.cordova.plugins.tls_psk.TlsPskServer();
    [40000, undefined].forEach((port) => {
      it('creates, starts, and stops server', async () => {
        expect(server.uuid).toBeUndefined();
        expect(server.port).toBeUndefined();

        let result = await new Promise((res, rej) => server.start(res, rej, Uint8Array.from([0x12, 0x34]), port));
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
      const key = Uint8Array.from([0, 0]);
      expect(await new Promise((res, rej) => server.start(res, rej, key))).toBe('OK');

      let error;
      try {
        await new Promise((res, rej) => server.start(res, rej, key));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Server already started');
    });
  });

  describe('TLS-PSK sockets', () => {
    const server = new window.cordova.plugins.tls_psk.TlsPskServer();
    const clients = [];
    server.onAccept = (socket) => clients.push(socket);
    const key = Uint8Array.from([0x01, 0x23]);

    beforeEach(async () => {
      await new Promise((res, rej) => server.start(res, rej, key));
    });

    afterEach(async () => {
      await Promise.all(clients.map(async (client) => {
        return new Promise((res, rej) => client.close(res, rej));
      }));
      clients.length = 0;
      await new Promise((res, rej) => server.stop(res, rej));
    });

    it('accepts client connections', async () => {
      const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();

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
      const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();
      expect(await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port))).toBe('OK');

      let error;
      try {
        await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));
      } catch (e) {
        error = e;
      }

      expect(error).toBe('Client already connected');
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
      const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));

      await new Promise((res, rej) => client.send(res, rej, payload));
      let received = await receive;

      expect(received).toBe(payload);
    });

    it('can send data from the server', async () => {
      const payload = 'foobar';
      const client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();
      let receive = new Promise((res, rej) => {
        client.onReceive = (conn, data) => res(dataToString(data));
        setTimeout(rej, 1000);
      });
      await new Promise((res, rej) => client.connect(res, rej, key, 'localhost', server.port));

      await new Promise((res, rej) => clients[0].send(res, rej, payload));
      let received = await receive;

      expect(received).toBe(payload);
    });
  });
}
