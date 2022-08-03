package com.perfectco.cordova;

import org.bouncycastle.tls.PSKTlsServer;
import org.bouncycastle.tls.TlsPSKIdentityManager;
import org.bouncycastle.tls.TlsServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TlsPskServer {
  private final TlsServer server;

  private ServerSocket socket;
  private Thread acceptThread;

  public TlsPskServer(byte[] key) {
    server = createServer(key);
  }

  public int start(int port) throws IOException {
    if (socket != null || acceptThread != null) {
      throw new IllegalStateException();
    }

    socket = new ServerSocket(port);
    if (port == 0) {
      port = socket.getLocalPort();
    }

    acceptThread = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
        try {
          Socket client = socket.accept();
        } catch (IOException ignored) {}
      }
    });
    acceptThread.start();

    return port;
  }

  public void stop() {
    if (acceptThread != null) {
      acceptThread.interrupt();
    }
    if (socket != null) {
      try {
        socket.close();
        socket = null;
      } catch (IOException ignored) {
      }
    }
    if (acceptThread != null) {
      try {
        acceptThread.join();
        acceptThread = null;
      } catch (InterruptedException ignored) {}
    }
  }

  private static TlsServer createServer(final byte[] key) {
    return new PSKTlsServer(TlsPskPlugin.CRYPTO, new TlsPSKIdentityManager() {
      @Override
      public byte[] getHint() {
        return new byte[0];
      }

      @Override
      public byte[] getPSK(byte[] identity) {
        return key;
      }
    });
  }
}
