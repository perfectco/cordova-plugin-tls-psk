package com.perfectco.cordova;

import org.bouncycastle.tls.PSKTlsServer;
import org.bouncycastle.tls.TlsPSKIdentityManager;
import org.bouncycastle.tls.TlsServer;
import org.bouncycastle.tls.TlsServerProtocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class TlsPskServer {
  private final UUID uuid = UUID.randomUUID();
  private final TlsServer server;

  private ServerSocket socket;
  private Thread acceptThread;

  public interface AcceptCallback {
    void onClientConnected(TlsPskSocket client);
  }

  public TlsPskServer(byte[] key) {
    server = createServer(key);
  }

  public UUID getUuid() { return uuid; }

  public int getPort() { return socket != null ? socket.getLocalPort() : 0; }

  public void start(int port, final ExecutorService executor, final AcceptCallback acceptCallback) throws IOException {
    if (socket != null || acceptThread != null) {
      throw new IllegalStateException();
    }

    socket = new ServerSocket(port);

    acceptThread = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
        try {
          final Socket client = socket.accept();
          executor.execute(() -> {
            try {
              TlsServerProtocol protocol = new TlsServerProtocol(client.getInputStream(), client.getOutputStream());
              protocol.accept(server);
              acceptCallback.onClientConnected(new TlsPskSocket(client, protocol));
            } catch (IOException ignored) {}
          });
        } catch (IOException ignored) {}
      }
    });
    acceptThread.start();
  }

  public void stop() throws IOException {
    if (acceptThread != null) {
      acceptThread.interrupt();
    }
    try {
      if (socket != null) {
        socket.close();
        socket = null;
      }
    } finally {
      if (acceptThread != null) {
        try {
          acceptThread.join();
          acceptThread = null;
        } catch (InterruptedException ignored) {
        }
      }
    }
  }

  private static TlsServer createServer(final byte[] key) {
    return new PSKTlsServer(TlsPskSocket.CRYPTO, new TlsPSKIdentityManager() {
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