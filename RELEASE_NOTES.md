# Ibn e Ilyas Technologies - Home Automation

## ESP32 firmware (ESP32 DevKit V1 and compatible boards)
- `ibn-firmware-<version>-full.bin`: first install. Flash it at address 0x0.
  This erases the saved Wi-Fi settings and the device token.
- `ibn-firmware-<version>-ota.bin`: update file used by the app (Devices > Options > Update now).
- `SHA256SUMS.txt`: checksums to verify your download.

## How to flash the full file
1. Connect the ESP32 with a data USB cable.
2. Use Espressif's esptool-js in Chrome or Edge, pick the file, set the address to 0x0 and press Program.
   Or use esptool on a computer:
   `esptool.py --chip esp32 --baud 460800 write_flash 0x0 ibn-firmware-<version>-full.bin`
3. After flashing, the ESP32 creates a Wi-Fi network named IbnEIlyas-XXXXXX.
   Connect to it and enter your home Wi-Fi. The device token is shown once after saving. Keep it safe, the app needs it.

## Android app
The app is locked per phone. You need an activation code from the developer.
