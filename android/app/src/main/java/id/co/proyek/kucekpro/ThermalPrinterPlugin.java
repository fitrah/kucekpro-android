package id.co.proyek.kucekpro;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;

@CapacitorPlugin(
  name = "ThermalPrinter",
  permissions = {
    @Permission(strings = { Manifest.permission.BLUETOOTH_CONNECT }, alias = "bluetoothConnect")
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
    if (!hasBluetoothConnectPermission()) {
      requestBluetoothConnectPermission(call);
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
    if (!hasBluetoothConnectPermission()) {
      requestBluetoothConnectPermission(call);
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
      socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
      adapter.cancelDiscovery();
      socket.connect();

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
      if (socket != null) {
        try {
          socket.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  @PermissionCallback
  private void bluetoothConnectPermsCallback(PluginCall call) {
    if (getPermissionState("bluetoothConnect") == PermissionState.GRANTED) {
      if ("listPrinters".equals(call.getMethodName())) listPrinters(call);
      else if ("printText".equals(call.getMethodName())) printText(call);
      else call.resolve();
    } else {
      call.reject("Izin Bluetooth dibutuhkan untuk mencetak struk.");
    }
  }

  private boolean hasBluetoothConnectPermission() {
    return (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    );
  }

  private void requestBluetoothConnectPermission(PluginCall call) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      call.reject("Izin Bluetooth tidak tersedia.");
      return;
    }
    saveCall(call);
    requestPermissionForAlias("bluetoothConnect", call, "bluetoothConnectPermsCallback");
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

  private String safeDeviceName(BluetoothDevice device) {
    try {
      String name = device.getName();
      return name == null || name.trim().isEmpty() ? "Printer Bluetooth" : name;
    } catch (SecurityException error) {
      return "Printer Bluetooth";
    }
  }
}
