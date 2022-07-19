package com.perfectco.cordova;

import org.bouncycastle.tls.PSKTlsClient;
import org.bouncycastle.tls.TlsClient;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.UUID;

public class TlsPskClient {
  private static final TlsCrypto crypto = new BcTlsCrypto(new SecureRandom());
  private final TlsClient client;
  private final UUID id = UUID.randomUUID();

  private Socket socket;
  private TlsClientProtocol protocol;

  public TlsPskClient(byte[] key) {
    client = createClient(key);
  }

  public UUID getId() { return id; }

  public void connect(String host, int port) throws IOException {
    socket = new Socket(host, port);
    protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());

    protocol.connect(client);
  }

  public void send(byte[] data) throws IOException {
    protocol.getOutputStream().write(data);
  }

  public void close() throws IOException {
    protocol.close();
    socket.close();
  }

  private static TlsClient createClient(byte[] key) {
    return new PSKTlsClient(crypto, new byte[0], key);
  }
}
