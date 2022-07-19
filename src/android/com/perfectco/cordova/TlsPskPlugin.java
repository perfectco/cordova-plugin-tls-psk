package com.perfectco.cordova;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

public class TlsPskPlugin extends CordovaPlugin {
  static final String TAG = "TlsPskPlugin";
  static final String ACTION_CONNECT = "connect";
  static final String ACTION_CLOSE = "close";
  static final String ACTION_SEND = "send";
  static final String ACTION_START = "start";
  static final String ACTION_STOP = "stop";

  HashMap<UUID, TlsPskClient> clients = new HashMap<>();

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
    switch (action) {
      case ACTION_CONNECT: {
        String host;
        try {
          host = args.getString(0);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Hostname error");
          return false;
        }

        int port;
        try {
          port = args.getInt(1);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Port error");
          return false;
        }

        byte[] key;
        try {
          key = toByteArray(args.get(2));
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Key error");
          return false;
        }

        cordova.getThreadPool().execute(() -> {
          try {
            UUID uuid = connect(host, port, key);
            JSONObject status = new JSONObject();
            status.put("uuid", uuid.toString());
            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            result.setKeepCallback(true);
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
      case ACTION_START:
        break;
      case ACTION_STOP:
        break;
    }
    return false;
  }

  private UUID connect(String host, int port, byte[] key) throws IOException {
    TlsPskClient client = new TlsPskClient(key);
    clients.put(client.getId(), client);
    client.connect(host, port);
    return client.getId();
  }

  private void send(UUID id, byte[] data) throws IOException {
    TlsPskClient client = clients.get(id);
    if (client != null) {
      client.send(data);
    }
  }

  private void close(UUID id) throws IOException {
    TlsPskClient client = clients.get(id);
    if (client != null) {
      client.close();
      clients.remove(id);
    }
  }

  private byte[] toByteArray(Object fromJson) throws JSONException {
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
}
