# HeartBeat (Mi Band 6 Companion)

HeartBeat is an Android app foundation for sharing live heart rate between two users and converting incoming HR into ambient heartbeat audio.

## Configurazione completata (Firebase + WebRTC)

Questo repository ora include una baseline configurata per collegare due dispositivi con signaling Firebase e trasporto WebRTC.

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

- `com.heartbeat.ble`: BLE contracts and normalization.
- `com.heartbeat.network`: signaling + WebRTC transport contracts and payload.
- `com.heartbeat.audio`: heartbeat playback timing core.
- `com.heartbeat.service`: foreground service orchestration.
- `com.heartbeat.ui`: UI state and controls.

See `docs/architecture.md` for flow and operational details.
