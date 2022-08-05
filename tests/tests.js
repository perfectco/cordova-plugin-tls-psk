exports.defineAutoTests = () => {
  describe('TLS-PSK server', () => {
    let server = new window.cordova.plugins.tls_psk.TlsPskServer();
    [40000, undefined].forEach((port) => {
      it('creates, starts, and stops server', async () => {
        expect(server.uuid).toBeUndefined();
        expect(server.port).toBeUndefined();

        var result = await new Promise((res, rej) => server.start(Uint8Array.from([0x12, 0x34]), port, res, rej));
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
}
