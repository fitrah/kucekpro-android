package id.co.proyek.kucekpro;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelUuid;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@CapacitorPlugin(
  name = "ThermalPrinter",
  permissions = {
    @Permission(
      strings = {
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
      },
      alias = "bluetooth"
    )
  }
)
public class ThermalPrinterPlugin extends Plugin {
  private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

  @PluginMethod
  public void isAvailable(PluginCall call) {
    JSObject result = new JSObject();
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    result.put("available", adapter != null);
    result.put("enabled", adapter != null && adapter.isEnabled());
    call.resolve(result);
  }

  @PluginMethod
  public void listPrinters(PluginCall call) {
    if (!hasBluetoothPermission()) {
      requestBluetoothPermission(call);
      return;
    }

    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      call.reject("Bluetooth tidak tersedia di perangkat ini.");
      return;
    }
    if (!adapter.isEnabled()) {
      call.reject("Bluetooth belum aktif.");
      return;
    }

    JSArray devices = new JSArray();
    Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
    for (BluetoothDevice device : bondedDevices) {
      JSObject item = new JSObject();
      item.put("name", safeDeviceName(device));
      item.put("address", device.getAddress());
      devices.put(item);
    }

    JSObject result = new JSObject();
    result.put("devices", devices);
    call.resolve(result);
  }

  @PluginMethod
  public void printText(PluginCall call) {
    if (!hasBluetoothPermission()) {
      requestBluetoothPermission(call);
      return;
    }

    String text = call.getString("text");
    String address = call.getString("address");
    if (text == null || text.trim().isEmpty()) {
      call.reject("Teks struk kosong.");
      return;
    }

    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      call.reject("Bluetooth tidak tersedia di perangkat ini.");
      return;
    }
    if (!adapter.isEnabled()) {
      call.reject("Bluetooth belum aktif.");
      return;
    }

    BluetoothDevice device = findDevice(adapter, address);
    if (device == null) {
      call.reject("Printer Bluetooth belum dipilih atau belum dipairing.");
      return;
    }

    BluetoothSocket socket = null;
    try {
      socket = connectToPrinter(device, adapter);

      OutputStream output = socket.getOutputStream();
      output.write(new byte[] { 0x1B, 0x40 });
      output.write(text.getBytes(Charset.forName("UTF-8")));
      output.write(new byte[] { 0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00 });
      output.flush();

      JSObject result = new JSObject();
      result.put("printed", true);
      result.put("printerName", safeDeviceName(device));
      result.put("printerAddress", device.getAddress());
      call.resolve(result);
    } catch (Exception error) {
      call.reject("Gagal mencetak ke printer Bluetooth: " + error.getMessage(), error);
    } finally {
      closeQuietly(socket);
    }
  }

  @PluginMethod
  public void saveText(PluginCall call) {
    String text = call.getString("text");
    String requestedFileName = call.getString("fileName");
    if (text == null || text.trim().isEmpty()) {
      call.reject("Teks struk kosong.");
      return;
    }

    String fileName = sanitizeFileName(requestedFileName);
    try {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        File directory = new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
          "KucekPro"
        );
        if (!directory.exists() && !directory.mkdirs()) {
          throw new Exception("Tidak bisa membuat folder Download/KucekPro.");
        }
        File file = new File(directory, fileName);
        OutputStream output = new FileOutputStream(file);
        try {
          output.write(text.getBytes(Charset.forName("UTF-8")));
          output.flush();
        } finally {
          output.close();
        }

        JSObject result = new JSObject();
        result.put("saved", true);
        result.put("fileName", fileName);
        result.put("location", "Download/KucekPro/" + fileName);
        call.resolve(result);
        return;
      }

      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
      values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
      values.put(
        MediaStore.MediaColumns.RELATIVE_PATH,
        Environment.DIRECTORY_DOWNLOADS + "/KucekPro"
      );

      Uri uri = getContext()
        .getContentResolver()
        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
      if (uri == null) throw new Exception("Tidak bisa membuat file struk.");

      OutputStream output = getContext().getContentResolver().openOutputStream(uri);
      if (output == null) throw new Exception("Tidak bisa membuka file struk.");
      try {
        output.write(text.getBytes(Charset.forName("UTF-8")));
        output.flush();
      } finally {
        output.close();
      }

      JSObject result = new JSObject();
      result.put("saved", true);
      result.put("fileName", fileName);
      result.put("location", "Download/KucekPro/" + fileName);
      call.resolve(result);
    } catch (Exception error) {
      call.reject("Gagal menyimpan struk: " + error.getMessage(), error);
    }
  }

  @PluginMethod
  public void saveBase64(PluginCall call) {
    String base64 = call.getString("base64");
    String requestedFileName = call.getString("fileName");
    String mimeType = call.getString("mimeType", "application/octet-stream");
    if (base64 == null || base64.trim().isEmpty()) {
      call.reject("Data file kosong.");
      return;
    }

    String fileName = sanitizeFileName(requestedFileName, defaultExtension(mimeType));
    try {
      byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
      saveBytes(fileName, mimeType, bytes);

      JSObject result = new JSObject();
      result.put("saved", true);
      result.put("fileName", fileName);
      result.put("location", "Download/KucekPro/" + fileName);
      call.resolve(result);
    } catch (Exception error) {
      call.reject("Gagal menyimpan file: " + error.getMessage(), error);
    }
  }

  @PermissionCallback
  private void bluetoothPermsCallback(PluginCall call) {
    if (getPermissionState("bluetooth") == PermissionState.GRANTED) {
      if ("listPrinters".equals(call.getMethodName())) listPrinters(call);
      else if ("printText".equals(call.getMethodName())) printText(call);
      else call.resolve();
    } else {
      call.reject("Izin Bluetooth dibutuhkan untuk mencetak struk.");
    }
  }

  private boolean hasBluetoothPermission() {
    return (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      (
        getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
          PackageManager.PERMISSION_GRANTED &&
        getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
          PackageManager.PERMISSION_GRANTED
      )
    );
  }

  private void requestBluetoothPermission(PluginCall call) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      call.reject("Izin Bluetooth tidak tersedia.");
      return;
    }
    saveCall(call);
    requestPermissionForAlias("bluetooth", call, "bluetoothPermsCallback");
  }

  private BluetoothDevice findDevice(BluetoothAdapter adapter, String address) {
    Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
    if (address != null && !address.trim().isEmpty()) {
      for (BluetoothDevice device : bondedDevices) {
        if (address.equalsIgnoreCase(device.getAddress())) return device;
      }
      return null;
    }
    for (BluetoothDevice device : bondedDevices) {
      String name = safeDeviceName(device).toLowerCase();
      if (name.contains("printer") || name.contains("pos") || name.contains("thermal")) {
        return device;
      }
    }
    return bondedDevices.isEmpty() ? null : bondedDevices.iterator().next();
  }

  private BluetoothSocket connectToPrinter(BluetoothDevice device, BluetoothAdapter adapter)
    throws Exception {
    adapter.cancelDiscovery();

    Exception lastError = null;

    for (UUID uuid : printerUuids(device)) {
      try {
        return connectSocket(adapter, device.createInsecureRfcommSocketToServiceRecord(uuid));
      } catch (Exception error) {
        lastError = error;
      }

      try {
        return connectSocket(adapter, device.createRfcommSocketToServiceRecord(uuid));
      } catch (Exception error) {
        lastError = error;
      }
    }

    Method secureMethod = device.getClass().getMethod("createRfcommSocket", int.class);
    Method insecureMethod = null;
    try {
      insecureMethod = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
    } catch (Exception ignored) {
    }

    for (int channel = 1; channel <= 12; channel++) {
      if (insecureMethod != null) {
        try {
          return connectSocket(adapter, (BluetoothSocket) insecureMethod.invoke(device, channel));
        } catch (Exception error) {
          lastError = error;
        }
      }

      try {
        return connectSocket(adapter, (BluetoothSocket) secureMethod.invoke(device, channel));
      } catch (Exception error) {
        lastError = error;
      }
    }

    throw lastError == null ? new Exception("Tidak bisa membuka koneksi printer.") : lastError;
  }

  private BluetoothSocket connectSocket(BluetoothAdapter adapter, BluetoothSocket socket) throws Exception {
    try {
      adapter.cancelDiscovery();
      sleepBeforeRetry();
      socket.connect();
      return socket;
    } catch (Exception error) {
      closeQuietly(socket);
      sleepBeforeRetry();
      throw error;
    }
  }

  private void sleepBeforeRetry() {
    try {
      Thread.sleep(250);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  private void closeQuietly(BluetoothSocket socket) {
    if (socket == null) return;
    try {
      socket.close();
    } catch (Exception ignored) {
    }
  }

  private String safeDeviceName(BluetoothDevice device) {
    try {
      String name = device.getName();
      return name == null || name.trim().isEmpty() ? "Printer Bluetooth" : name;
    } catch (SecurityException error) {
      return "Printer Bluetooth";
    }
  }

  private List<UUID> printerUuids(BluetoothDevice device) {
    LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
    uuids.add(SPP_UUID);
    try {
      ParcelUuid[] deviceUuids = device.getUuids();
      if (deviceUuids != null) {
        for (ParcelUuid uuid : deviceUuids) {
          if (uuid != null) uuids.add(uuid.getUuid());
        }
      }
    } catch (Exception ignored) {
    }
    return new ArrayList<>(uuids);
  }

  private void saveBytes(String fileName, String mimeType, byte[] bytes) throws Exception {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      File directory = new File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "KucekPro"
      );
      if (!directory.exists() && !directory.mkdirs()) {
        throw new Exception("Tidak bisa membuat folder Download/KucekPro.");
      }
      File file = new File(directory, fileName);
      OutputStream output = new FileOutputStream(file);
      try {
        output.write(bytes);
        output.flush();
      } finally {
        output.close();
      }
      return;
    }

    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
    values.put(
      MediaStore.MediaColumns.RELATIVE_PATH,
      Environment.DIRECTORY_DOWNLOADS + "/KucekPro"
    );

    Uri uri = getContext()
      .getContentResolver()
      .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) throw new Exception("Tidak bisa membuat file.");

    OutputStream output = getContext().getContentResolver().openOutputStream(uri);
    if (output == null) throw new Exception("Tidak bisa membuka file.");
    try {
      output.write(bytes);
      output.flush();
    } finally {
      output.close();
    }
  }

  private String sanitizeFileName(String value) {
    return sanitizeFileName(value, ".txt");
  }

  private String sanitizeFileName(String value, String extension) {
    String fileName = value == null ? "" : value.trim();
    if (fileName.isEmpty()) fileName = "struk-kucekpro" + extension;
    fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
    if (!fileName.toLowerCase().endsWith(extension)) fileName = fileName + extension;
    return fileName;
  }

  private String defaultExtension(String mimeType) {
    if ("application/pdf".equalsIgnoreCase(mimeType)) return ".pdf";
    if ("text/plain".equalsIgnoreCase(mimeType)) return ".txt";
    return ".bin";
  }
}
