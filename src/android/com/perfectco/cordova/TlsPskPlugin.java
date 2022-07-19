package com.perfectco.cordova;

import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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
          String base64 = args.getString(2);
          key = Base64.decode(base64, Base64.DEFAULT);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Key error");
          return false;
        }

        try {
          UUID id = connect(host, port, key);
          JSONObject status = new JSONObject();
          status.put("uuid", id.toString());
          PluginResult result = new PluginResult(PluginResult.Status.OK, status);
          result.setKeepCallback(true);
          callbackContext.sendPluginResult(result);
        } catch (IOException | JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Connect error");
          return false;
        }
      } return true;
      case ACTION_CLOSE: {
        UUID id;
        try {
          String sid = args.getString(0);
          id = UUID.fromString(sid);
        } catch (JSONException | IllegalArgumentException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Unknown client");
          return false;
        }

        try {
          close(id);
          callbackContext.success();
        } catch (IOException e) {
          Log.e(TAG, e.getMessage(), e);
          callbackContext.error("Error closing");
          return false;
        }
      } return true;
      case ACTION_SEND:
        break;
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

  private void close(UUID id) throws IOException {
    TlsPskClient client = clients.get(id);
    if (client != null) {
      client.close();
      clients.remove(id);
    }
  }
}
