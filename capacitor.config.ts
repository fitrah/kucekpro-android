import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "id.co.proyek.kucekpro",
  appName: "KucekPro",
  webDir: "web",
  server: {
    url: "https://kucekpro.kanezza.com",
    cleartext: false,
  },
};

export default config;
