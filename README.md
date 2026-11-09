# WhiteRTC Client Applications 📱💻

<p align="left">
  <strong>English</strong> • <a href="README.ru.md">Русский</a>
</p>

Cross-platform client applications (Android, Linux, Windows) and privileged system background daemon for the [WhiteRTC](https://github.com/13absolute/whitertc-core) high-performance L3 VPN overlay.

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
* **`:desktopApp`** — Desktop Compose application (Linux, Windows, macOS preview):
  * Modern OLED dark theme UI
  * Inter-Process Communication (IPC) client communicating with `:daemon`
  * Packaging formats: Linux Debian (`.deb`), Windows (`.msi` / `.exe`)
* **`:daemon`** — Privileged system background VPN service:
  * High-performance TUN device management (Linux `tun` / Windows `wintun`)
  * Real-time packet multiplexer and EAT (Early-Ack-Transmission) scheduler
  * Secure local IPC interface for unprivileged desktop UI communication
  * Dynamic relay provider discovery from `./plugins/` or system paths

---

## 🛠️ Building & Running

### Prerequisites
* JDK 21+
* Android SDK (API 34+)

### 1. Run Desktop Client (Unprivileged)
```bash
./gradlew runDesktop
```

### 2. Run Background VPN Daemon (Requires Root / Administrator)
```bash
# Linux
sudo ./gradlew runDaemon

# Windows (Run terminal as Administrator)
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

To enable external relay providers (such as third-party RFC 8656 TURN relays):
1. Download the required provider plugin (`.jar` for Desktop or `.apk` for Android) from [WhiteRTC Plugins Releases](https://github.com/13absolute/whitertc-plugins/releases).
2. **Desktop:** Place the `.jar` file into `./plugins/` next to the executable, or into `~/.whitertc/plugins/`.
3. **Android:** Install or import the plugin file via the in-app Plugin Manager.
