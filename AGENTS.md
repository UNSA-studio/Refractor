# AGENTS.md

## What this is

**Refractor** — a single-module Android app (`:app`) for WebRTC live streaming / screen sharing, with a Chinese (zh-CN) UI. Package/namespace: `unsa.rfr.com`. Kotlin 2.1.0 + Jetpack Compose (Material3), AGP 8.9.0, Gradle 9.2.1 (wrapper), compileSdk/targetSdk 36, minSdk 28, Java 17.

## Build & verify

- Build: `./gradlew assembleDebug` (this is exactly what CI runs; APK lands in `app/build/outputs/apk/debug/app-debug.apk`). JDK 17 required.
- **There are no tests** (no `test`/`androidTest` source sets) and no lint/ktlint/format config. Compilation via `assembleDebug` is the only verification.
- `local.properties` is gitignored — local builds need `ANDROID_HOME` or a `local.properties` with the SDK path.
- **Recommended workflow is GitHub Actions** (`.github/workflows/build.yml`), which builds on push to `main` — the debug `debug.keystore` is committed so CI signing works without secrets.

## Architecture

- **All UI is Compose inside `MainActivity`** using Navigation Compose: routes are `home`, `create`, `room/{roomId}/{role}` (`role` = `broadcaster`|`viewer`), `settings`. `RoomActivity` is a dead stub that immediately `finish()`es — don't build on it.
- **Backend is external, hardcoded, and not in this repo**: signaling WebSocket `wss://rfr-sl.cc.cd/room/{roomId}` plus HTTP `https://rfr-sl.cc.cd/create` and `/check/{roomId}` (all in `SignalingClient.kt`). `network_security_config.xml` blocks cleartext to that domain. There is no local server to run.
- **Signaling protocol**: JSON messages with `type` ∈ `join`, `ping`/`pong` (heartbeat every 30s), `signal`, `user-joined`/`user-left`, `chat`, `error`. Incoming messages are parsed in `SignalingClient.handleMessage()` and pushed to a `Channel<SignalMessage>`.
- **WebRTC**: `WebRtcManager` uses `org.webrtc.*` (provided by `io.getstream:stream-webrtc-android`). Broadcaster captures 720x1280@30 and sends an offer; viewers answer. ICE candidates are **hand-serialized with string interpolation** in `WebRtcManager` — fragile, be careful when touching SDP/candidate escaping. STUN: Google + Cloudflare, no TURN.
- **Capture**: `ScreenCaptureService` (foreground `mediaProjection`) uses `org.webrtc.ScreenCapturerAndroid` and exposes the capturer via a **static companion** field (`videoCapturer`/`mediaProjection`) consumed by other classes. `AudioCaptureService` is foreground `microphone|mediaProjection`.
- **JSON**: plain Android `org.json` (`JSONObject`) — no kotlinx.serialization / Gson / Moshi. Add new signaling fields using `optString`/`optInt`/`optBoolean`.
- **Settings**: plain `SharedPreferences` (`"settings"`: `theme_color`, `dynamic_color`, `first_launch`) read in `MainActivity`. `androidx.datastore` is a declared dependency but unused — don't assume it's wired up.

## Conventions & gotchas

- **Custom debug signing is intentional**: debug builds sign with the committed `debug.keystore` (alias `refractor`, password `android`) so debug builds get a fixed signature. Do not replace it with the default debug keystore.
- **Logging**: use `RefractorLog.write(...)` (appends to `cacheDir/refractor_log.txt`) rather than only `android.util.Log`. A global uncaught-exception handler in `App.kt` writes crash dumps to the public **Downloads** dir (`refractor_crash_*.txt`). Existing log strings are Chinese — match that.
- UI strings and comments are predominantly Chinese; keep new user-facing strings consistent (do not introduce English-only UI copy).
- No room code / ID scheme is standardized beyond `RfrIdGenerator` — check it before assuming ID format.
