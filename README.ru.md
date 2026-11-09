# WhiteRTC Client Applications 📱💻

<p align="left">
  <a href="README.md">English</a> • <strong>Русский</strong>
</p>

Кроссплатформенные клиентские приложения (Android, Linux, Windows) и привилегированный системный фоновый демон для высокопроизводительного L3 VPN-оверлея [WhiteRTC](https://github.com/13absolute/whitertc-core).

---

## 📦 Архитектура и модули

* **`:app:shared`** — общий слой представления и доменной логики на Compose Multiplatform (CMP):
  * Реактивная архитектура UI на базе MVI с однонаправленным потоком данных (UDF)
  * Матрица телеметрии многопутевых каналов в реальном времени (24 параллельных субпотока, RTT, джиттер, потери)
  * Локальное offline-first хранилище на AndroidX Room 3 и SQLite
  * Раздельное туннелирование (split-tunneling): фильтрация приложений и маршрутизация по доменам
  * Загрузчик динамических плагинов реле (интеграция через Java SPI)
* **`:app:androidApp`** — нативный Android-клиент:
  * Интеграция с системным `VpnService` и интерфейсом TUN
  * Парсинг диплинков (`white://...`) и сканирование QR-кодов камерой
  * Проксирование отдельных приложений / раздельное туннелирование
  * Поддержка раздачи интернета в режиме точки доступа (Hotspot repeater)
* **`:desktopApp`** — десктопное приложение на Compose (Linux, Windows, macOS preview):
  * Современный brutalist-интерфейс в тёмной теме OLED
  * Клиент межпроцессного взаимодействия (IPC) для связи с `:daemon`
  * Форматы пакетов: Linux Debian (`.deb`), Windows (`.msi` / `.exe`)
* **`:daemon`** — привилегированная системная служба VPN:
  * Высокопроизводительное управление устройствами TUN (Linux `tun` / Windows `wintun`)
  * Мультиплексор пакетов реального времени и планировщик EAT (Early-Ack-Transmission)
  * Защищённый локальный интерфейс IPC для связи с непривилегированным UI
  * Автообнаружение внешних плагинов-провайдеров из `./plugins/` или системных каталогов

---

## 🛠️ Сборка и запуск

### Требования
* JDK 21+
* Android SDK (API 34+)

### 1. Запуск десктопного клиента (без привилегий)
```bash
./gradlew runDesktop
```

### 2. Запуск фонового VPN-демона (требуются права root / администратора)
```bash
# Linux
sudo ./gradlew runDaemon

# Windows (запустите терминал от имени Администратора)
./gradlew runDaemon
```

### 3. Сборка Android-приложения (Debug APK)
```bash
./gradlew :app:androidApp:assembleDebug
```
Результат: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`

### 4. Сборка пакета Linux Desktop (.deb)
```bash
./gradlew packageLinuxDeb
```
Результат: `desktopApp/build/compose/binaries/main/deb/*.deb`

### 5. Сборка нативного демона под Linux (GraalVM AOT)
```bash
./build_daemon_native.sh
```
Результат: `daemon/build/native/nativeCompile/whitertc-daemon`

---

## 🔌 Динамические плагины ретрансляции (Relay Plugins)

Для подключения внешних релейных провайдеров (например, сторонних серверов RFC 8656 TURN):
1. Скачайте необходимый плагин (`.jar` для Desktop или `.apk` для Android) со страницы [релизов WhiteRTC Plugins](https://github.com/13absolute/whitertc-plugins/releases).
2. **Desktop:** Поместите `.jar`-файл в папку `./plugins/` рядом с исполняемым файлом либо в `~/.whitertc/plugins/`.
3. **Android:** Установите или импортируйте файл плагина через менеджер плагинов в настройках приложения.
