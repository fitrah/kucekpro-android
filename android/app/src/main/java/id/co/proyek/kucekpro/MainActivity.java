package id.co.proyek.kucekpro;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    registerPlugin(ThermalPrinterPlugin.class);
    super.onCreate(savedInstanceState);
    configureBackButton();
  }

  private void configureBackButton() {
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        WebView webView = bridge != null ? bridge.getWebView() : null;

        if (webView != null && webView.canGoBack()) {
          webView.goBack();
          return;
        }

        moveTaskToBack(true);
      }
    });
  }
}
