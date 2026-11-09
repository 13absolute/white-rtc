# WhiteRTC Client Applications 📱💻

Cross-platform client applications (Android, Linux, Windows, macOS) and privileged system background daemon for the [WhiteRTC](https://github.com/13absolute/whitertc-core) high-performance L3 VPN overlay.

---

## 📦 Architecture & Modules

* **`:app:shared`** — Compose Multiplatform (CMP) shared presentation and domain layer:
  * Reactive MVI UI architecture with unidirectional data flow (UDF)
  * Real-time multipath channel telemetry matrix (24 parallel subflows, RTT, jitter, loss rates)
  * Local offline-first persistence using AndroidX Room 3 & SQLite
  * Split-tunneling application filter and domain-based routing rules
  * Dynamic Relay Plugin loader (SPI integration)
* **`:app:androidApp`** — Native Android client:
  * Android `VpnService` with TUN interface integration
  * Deep link parsing (`white://...`) and QR code camera scanning
  * Per-app proxying / split tunneling
  * Hotspot tethering repeater support
* **`:desktopApp`** — Desktop Compose application (Linux, Windows, macOS):
  * Modern OLED dark theme UI
  * Inter-Process Communication (IPC) client communicating with `:daemon`
  * Packaging formats: Linux Debian (`.deb`), Windows (`.msi` / `.exe`)
* **`:daemon`** — Privileged system background VPN service:
  * High-performance TUN device management (Linux `tun` / Windows `wintun`)
  * Real-time packet multiplexer and EAT (Early-Ack-Transmission) scheduler
  * Local IPC server (`127.0.0.1:8088`) for unprivileged desktop UI communication
  * Dynamic relay provider discovery from `./plugins/` or system paths

---

## 🛠️ Building & Running

### Prerequisites
* JDK 21+
* Android SDK (API 34+)

### 1. Run Desktop Client
```bash
./gradlew runDesktop
```

### 2. Run Background VPN Daemon
```bash
./gradlew runDaemon
```

### 3. Build Android App (Debug APK)
```bash
./gradlew :app:androidApp:assembleDebug
```
Output: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`

### 4. Build Linux Desktop Package (.deb)
```bash
./gradlew packageLinuxDeb
```
Output: `desktopApp/build/compose/binaries/main/deb/*.deb`

### 5. Build Native Linux Daemon (GraalVM AOT)
```bash
./build_daemon_native.sh
```
Output: `daemon/build/native/nativeCompile/whitertc-daemon`

---

## 🔌 Dynamic Relay Plugins

To enable third-party relay providers (such as VK Video Calls TURN relays):
1. Download `provider-vk-desktop.jar` or `provider-vk-android.apk` from [WhiteRTC Plugins Releases](https://github.com/13absolute/whitertc-plugins/releases).
2. **Desktop:** Drop `provider-vk-desktop.jar` into `./plugins/` next to the executable, or `~/.whitertc/plugins/`.
3. **Android:** Install or import the plugin file via the in-app Plugin Manager.
