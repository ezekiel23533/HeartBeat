# HeartBeat (Mi Band 6 Companion)

HeartBeat is an Android app foundation for sharing live heart rate between two users and converting incoming HR into ambient heartbeat audio.

## Configurazione completata (Firebase + WebRTC)

Questo repository include una baseline configurata per collegare due dispositivi con signaling Firebase e trasporto WebRTC.

- Plugin Gradle Google Services configurato (`com.google.gms.google-services`).
- Firebase BoM (`34.9.0`) e Firebase Realtime Database SDK integrati.
- File `app/google-services.json` installato per il package `com.heartbeaten`.
- `WebRtcPeerTransport` con server STUN Google:
  - `stun:stun.l.google.com:19302`
- Signaling repository su Firebase Realtime Database (`/sessions/{sessionCode}/signals/...`).

## Note importanti sul signaling

Non viene usato un endpoint WebSocket personalizzato nella configurazione attuale.

Il signaling passa tramite Firebase Realtime Database (SDK Firebase), quindi i messaggi SDP/ICE transitano nel path sessione e non su un tuo `wss://...` dedicato.

## Package map

- `com.heartbeaten.ble`: BLE contracts and normalization.
- `com.heartbeaten.network`: signaling + WebRTC transport contracts and payload.
- `com.heartbeaten.audio`: heartbeat playback timing core.
- `com.heartbeaten.service`: foreground service orchestration.
- `com.heartbeaten.ui`: UI state and controls.

See `docs/architecture.md` for flow and operational details.

## Build prerequisites

- JDK 21 (the project is pinned via `org.gradle.java.home` in `gradle.properties`).
- Android SDK installed.

Set SDK path with one of the following:

- Environment variable: `ANDROID_HOME=/path/to/android-sdk`
- Or file `local.properties` in project root:

```properties
sdk.dir=/path/to/android-sdk
```

## Build the app (debug APK)

```bash
gradle assembleDebug --no-daemon
```

Output APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```
