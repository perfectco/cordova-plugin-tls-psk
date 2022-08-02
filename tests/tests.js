exports.defineAutoTests = function() {
  describe('client socket', () => {
    let client = new window.cordova.plugins.tls_psk.TlsPskClientSocket();

    describe('connects', async () => {
      var res = await new Promise((resolve, reject) => client.connect('10.0.2.2', 40000, Uint8Array.from([0x12, 0x34]), resolve, reject));
      expect(res).toBe('OK');

      it('sends', async () => {
        res = await new Promise((resolve, reject) => client.send('foo', resolve, reject));
        expect(res).toBe('OK')
      });

      it('receives', async () => {
        client.onReceive = (bytes) => {
          let text = new TextDecoder('utf-8').decode(Uint8Array.from(bytes));
          expect(text).toBe('bar\n');
        };
      });
    });
  });
}
