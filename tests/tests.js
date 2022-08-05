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
  });
}
