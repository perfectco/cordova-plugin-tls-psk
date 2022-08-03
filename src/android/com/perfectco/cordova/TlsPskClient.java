package com.perfectco.cordova;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.bouncycastle.tls.PSKTlsClient;
import org.bouncycastle.tls.TlsClient;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsProtocol;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.UUID;

public class TlsPskClient {
  private final TlsClient client;
  private final UUID uuid = UUID.randomUUID();

  private Socket socket;
  private TlsProtocol protocol;
  private Thread receiveThread;

  public TlsPskClient(byte[] key) {
    client = createClient(key);
  }

  public UUID getUuid() { return uuid; }

  public void connect(String host, int port, final CallbackContext callbackContext) throws IOException {
    if (socket != null || protocol != null || receiveThread != null) {
      throw new IllegalStateException();
    }

    socket = new Socket(host, port);
    TlsClientProtocol clientProtocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
    protocol = clientProtocol;

    clientProtocol.connect(client);
    startReceiveThread(callbackContext);
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

  private void startReceiveThread(CallbackContext callbackContext) {
    receiveThread = new Thread(() -> {
      try (InputStream inputStream = protocol.getInputStream()) {
        byte[] buf = new byte[1024];
        while (!Thread.currentThread().isInterrupted()) {
          int read = inputStream.read(buf);
          if (read > 0) {
            try {
              JSONObject status = new JSONObject();
              status.put("action", "onReceive");
              status.put("uuid", uuid);
              status.put("buffer", toJSONArray(buf, 0, read));
              PluginResult result = new PluginResult(PluginResult.Status.OK, status);
              result.setKeepCallback(true);
              callbackContext.sendPluginResult(result);
            } catch (JSONException ignored) {}
          }
        }
      } catch (IOException ignored) {
      }
    });
    receiveThread.start();
  }

  private static TlsClient createClient(byte[] key) {
    return new PSKTlsClient(TlsPskPlugin.CRYPTO, new byte[0], key);
  }

  private static JSONArray toJSONArray(final byte[] bytes, final int off, final int len) {
    JSONArray array = new JSONArray();
    for (int i = off; i < off + len; i++) {
      array.put(bytes[i]);
    }
    return array;
  }
}
