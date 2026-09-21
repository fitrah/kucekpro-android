package id.co.proyek.kucekpro;

import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

@CapacitorPlugin(name = "QrScanner")
public class QrScannerPlugin extends Plugin {
  private final ScanContract scanContract = new ScanContract();

  @PluginMethod
  public void scan(PluginCall call) {
    ScanOptions options = new ScanOptions();
    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
    options.setPrompt("Arahkan kamera ke QR Work Order");
    options.setBeepEnabled(false);
    options.setOrientationLocked(false);
    options.setBarcodeImageEnabled(false);
    Intent intent = scanContract.createIntent(getContext(), options);
    startActivityForResult(call, intent, "scanResult");
  }

  @ActivityCallback
  private void scanResult(PluginCall call, androidx.activity.result.ActivityResult activityResult) {
    if (call == null) return;
    ScanIntentResult result = scanContract.parseResult(
      activityResult.getResultCode(),
      activityResult.getData()
    );
    JSObject response = new JSObject();
    response.put("cancelled", result.getContents() == null);
    if (result.getContents() != null) response.put("content", result.getContents());
    call.resolve(response);
  }
}
