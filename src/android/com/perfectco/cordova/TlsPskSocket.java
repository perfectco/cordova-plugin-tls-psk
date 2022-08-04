package com.perfectco.cordova;

import org.bouncycastle.tls.TlsProtocol;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.UUID;

public class TlsPskSocket {
  public static final TlsCrypto CRYPTO = new BcTlsCrypto(new SecureRandom());
  private final UUID uuid = UUID.randomUUID();

  private Socket socket;
  private TlsProtocol protocol;
  private Thread receiveThread;

  public interface ReceiveCallback {
    void onReceive(byte[] data, int len);
  }

  public TlsPskSocket(Socket socket, TlsProtocol protocol) {
    connected(socket, protocol);
  }

  protected TlsPskSocket() {}

  public UUID getUuid() { return uuid; }

  public InetSocketAddress getSocketAddress() {
    return socket != null ? (InetSocketAddress) socket.getRemoteSocketAddress() : null;
  }

  public void send(byte[] data) throws IOException {
    protocol.getOutputStream().write(data);
  }

  public void close() {
    if (receiveThread != null) {
      receiveThread.interrupt();
    }
    if (protocol != null) {
      try {
        protocol.close();
        protocol = null;
      } catch (IOException ignored) {}
    }
    if (socket != null) {
      try {
        socket.close();
        socket = null;
      } catch (IOException ignored) {}
    }
    if (receiveThread != null) {
      try {
        receiveThread.join();
        receiveThread = null;
      } catch (InterruptedException ignored) {
      }
    }
  }

  public boolean isConnected() {
    return socket != null || protocol != null || receiveThread != null;
  }

  protected void connected(Socket socket, TlsProtocol protocol) {
    if (isConnected()) {
      throw new IllegalStateException();
    }

    this.socket = socket;
    this.protocol = protocol;
  }

  public void startReceiveThread(ReceiveCallback receiveCallback) {
    if (receiveThread != null) {
      throw new IllegalStateException();
    }

    receiveThread = new Thread(() -> {
      try (InputStream inputStream = protocol.getInputStream()) {
        byte[] buf = new byte[1024];
        while (!Thread.currentThread().isInterrupted()) {
          int read = inputStream.read(buf);
          if (read > 0) {
            receiveCallback.onReceive(buf, read);
          }
        }
      } catch (IOException ignored) {
      }
    });
    receiveThread.start();
  }
}
