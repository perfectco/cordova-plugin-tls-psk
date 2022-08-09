package com.perfectco.cordova;

import org.bouncycastle.tls.PSKTlsClient;
import org.bouncycastle.tls.TlsClient;
import org.bouncycastle.tls.TlsClientProtocol;

import java.io.IOException;
import java.net.Socket;

public class TlsPskClientSocket extends TlsPskSocket {
  private final TlsClient client;

  public TlsPskClientSocket(byte[] key) {
    client = createClient(key);
  }

  public void connect(String host, int port) throws IOException {
    if (isConnected()) {
      throw new IllegalStateException();
    }

    Socket socket = new Socket(host, port);
    TlsClientProtocol protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
    protocol.connect(client);
    connected(socket, protocol);
  }

  private static TlsClient createClient(byte[] key) {
    return new PSKTlsClient(CRYPTO, new byte[0], key);
  }
}
