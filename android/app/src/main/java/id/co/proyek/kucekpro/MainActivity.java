package id.co.proyek.kucekpro;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    registerPlugin(ThermalPrinterPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
