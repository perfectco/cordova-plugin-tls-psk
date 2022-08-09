package com.perfectco.cordova;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

public class TlsPskPlugin extends CordovaPlugin {
  private static final String TAG = "TlsPskPlugin";
  private static final String ACTION_CONNECT = "connect";
  private static final String ACTION_CLOSE = "close";
  private static final String ACTION_SEND = "send";
  private static final String ACTION_RECEIVE = "receive";
  private static final String ACTION_START = "start";
  private static final String ACTION_STOP = "stop";
  private static final String GET_SERVER_COUNT = "get_server_count";
  private static final String GET_CLIENT_COUNT = "get_client_count";

  private final HashMap<UUID, TlsPskSocket> clients = new HashMap<>();
  private final HashMap<UUID, TlsPskServer> servers = new HashMap<>();

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
    switch (action) {
      case ACTION_CONNECT: {
        byte[] key;
        try {
          key = toByteArray(args.get(0));
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Key error");
          return false;
        }

        String host;
        try {
          host = args.getString(1);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Hostname error");
          return false;
        }

        int port;
        try {
          port = args.getInt(2);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Port error");
          return false;
        }

        cordova.getThreadPool().execute(() -> {
          try {
            TlsPskSocket client = connect(host, port, key, callbackContext);
            JSONObject status = new JSONObject();
            status.put("uuid", client.getUuid().toString());
            status.put("host", host);
            status.put("port", port);
            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            callbackContext.sendPluginResult(result);
          } catch (IOException | JSONException e) {
            Log.e(TAG, e.getMessage(), e);
            callbackContext.error("Connect error");
          }
        });
      } return true;
      case ACTION_CLOSE: {
        UUID uuid;
        try {
          uuid = UUID.fromString(args.getString(0));
        } catch (JSONException | IllegalArgumentException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Unknown client");
          return false;
        }

        cordova.getThreadPool().execute(() -> {
          try {
            close(uuid);
            callbackContext.success();
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            callbackContext.error("Error closing");
          }
        });
      } return true;
      case ACTION_SEND: {
        UUID uuid;
        try {
          uuid = UUID.fromString(args.getString(0));
        } catch (JSONException | IllegalArgumentException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Unknown client");
          return false;
        }

        byte[] data;
        try {
          data = toByteArray(args.get(1));
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Unable to serialize message");
          return false;
        }

        cordova.getThreadPool().execute(() -> {
          try {
            send(uuid, data);
            callbackContext.success();
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            callbackContext.error("Error sending message");
          }
        });
      } return true;
      case ACTION_RECEIVE: {
        UUID uuid;
        try {
          uuid = UUID.fromString(args.getString(0));
        } catch (JSONException | IllegalArgumentException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Unknown client");
          return false;
        }

        receive(uuid, callbackContext);
      } return true;
      case ACTION_START: {
        byte[] key;
        try {
          key = toByteArray(args.get(0));
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Key error");
          return false;
        }

        int port = args.optInt(1, 0);

        cordova.getThreadPool().execute(() -> {
          try {
            TlsPskServer server = start(key, port, callbackContext);
            JSONObject status = new JSONObject();
            status.put("uuid", server.getUuid().toString());
            status.put("port", server.getPort());
            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
          } catch (IOException | JSONException e) {
            Log.e(TAG, e.getMessage(), e);
            callbackContext.error("Start error");
          }
        });
      } return true;
      case ACTION_STOP: {
        UUID uuid;
        try {
          uuid = UUID.fromString(args.getString(0));
        } catch (JSONException | IllegalArgumentException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Unknown server");
          return false;
        }

        cordova.getThreadPool().execute(() -> {
          try {
            stop(uuid);
            callbackContext.success();
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            callbackContext.error("Error stopping");
          }
        });
      } return true;
      case GET_SERVER_COUNT:
        callbackContext.success(servers.size());
        return true;
      case GET_CLIENT_COUNT:
        callbackContext.success(clients.size());
        return true;
    }
    return false;
  }

  private TlsPskSocket connect(String host, int port, byte[] key, final CallbackContext cb) throws IOException {
    TlsPskClientSocket client = new TlsPskClientSocket(key);
    client.connect(host, port, cb);
    clients.put(client.getUuid(), client);
    return client;
  }

  private void close(UUID id) throws IOException {
    TlsPskSocket socket = clients.remove(id);
    if (socket != null) {
      socket.close();
    }
  }

  private void send(UUID id, byte[] data) throws IOException {
    TlsPskSocket socket = clients.get(id);
    if (socket != null) {
      socket.send(data);
    }
  }

  private void receive(UUID id, CallbackContext cb) {
    final TlsPskSocket socket = clients.get(id);
    if (socket != null) {
      socket.startReceiveThread(new TlsPskSocket.ReceiveCallback() {
        @Override
        public void onReceive(byte[] data, int len) {
          try {
            JSONObject status = new JSONObject();
            status.put("action", "receive");
            status.put("uuid", socket.getUuid());
            status.put("data", toJSONArray(data, 0, len));
            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            result.setKeepCallback(true);
            cb.sendPluginResult(result);
          } catch (JSONException ignored) {}
        }

        @Override
        public void onClose() {
          try {
            JSONObject status = new JSONObject();
            status.put("action", "close");
            status.put("uuid", socket.getUuid());
            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            cb.sendPluginResult(result);
          } catch (JSONException ignored) {}
        }
      });
    }
  }

  private TlsPskServer start(byte[] key, int port, final CallbackContext cb) throws IOException {
    TlsPskServer server = new TlsPskServer(key);
    server.start(port, cordova.getThreadPool(), (client) -> {
      clients.put(client.getUuid(), client);
      InetSocketAddress addr = client.getSocketAddress();
      try {
        JSONObject status = new JSONObject();
        status.put("action", "onAccept");
        status.put("uuid", client.getUuid());
        status.put("host", addr.getAddress().getHostAddress());
        status.put("port", addr.getPort());
        PluginResult result = new PluginResult(PluginResult.Status.OK, status);
        result.setKeepCallback(true);
        cb.sendPluginResult(result);
      } catch (JSONException ignored) {}
    });
    servers.put(server.getUuid(), server);
    return server;
  }

  private void stop(UUID id) throws IOException {
    TlsPskServer server = servers.remove(id);
    if (server != null) {
      server.stop();
    }
  }

  private static byte[] toByteArray(Object fromJson) throws JSONException {
    if (fromJson instanceof String) {
      return ((String) fromJson).getBytes(StandardCharsets.UTF_8);
    } else if (fromJson instanceof JSONArray) {
      JSONArray arry = (JSONArray) fromJson;
      byte[] bytes = new byte[arry.length()];
      for (int i = 0; i < arry.length(); i++) {
        bytes[i] = (byte)arry.getInt(i);
      }
      return bytes;
    } else {
      throw new JSONException("unknown type");
    }
  }

  private static JSONArray toJSONArray(final byte[] bytes, final int off, final int len) {
    JSONArray array = new JSONArray();
    for (int i = off; i < off + len; i++) {
      array.put(bytes[i]);
    }
    return array;
  }
}
