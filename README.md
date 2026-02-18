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

## Foreground execution (anti-kill)

È stato aggiunto `AndroidHeartbeatForegroundService`, che avvia una notifica persistente a bassa priorità durante una sessione attiva.

- Canale notifiche dedicato: `heartbeat.session`
- Azione rapida `Stop` dalla notifica
- Supporto aggiornamento stato/BPM tramite intent di update
- Integrazione pronta con il core service via `SessionForegroundNotifier` / `AndroidSessionForegroundNotifier`
- Azione notifica `Stop` emette anche `ACTION_STOP_SESSION_REQUESTED` per permettere stop end-to-end della sessione
- `AndroidSessionStopActionHandler` disponibile per collegare il broadcast di stop al tuo orchestratore app-layer
- `AndroidHeartbeatSessionRuntime` offre un wiring unico (service + notifier + stop handler) con API `attach/start/stop/detach`
- Manifest aggiornato con permessi BLE moderni (`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`) e compatibilità legacy
- Runtime permission flow is wired end-to-end in `MainActivity` via `ActivityResultContracts.RequestMultiplePermissions`, with result propagation to `HeartbeatViewModel`

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
