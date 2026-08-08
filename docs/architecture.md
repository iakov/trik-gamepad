# Architecture — trik-gamepad

<!-- encoding: utf-8 -->

Scope: how the app is put together — module map, the TCP command protocol, the
MJPEG video pipeline, and how the test layers map onto the code. Written for
new contributors and future sessions; keep it code-accurate (it is reviewed
against `app/src` whenever it is touched).

## Module map

Single Gradle module `app/` (pure Kotlin, 0 `.java`). Two activities:

- **`MainActivity`** — the gamepad itself: two touch pads, five magic buttons,
  a sensor-driven wheel, and the MJPEG video stream. Owns the
  `SenderService`, implements the `SettingsUi` view callbacks, and is the only
  place the app's UI lives.
- **`MainActivitySettingsController`** — the preference-change handling
  extracted from `MainActivity` (ROADMAP Phase 2-E); see "Settings" below.
- **`SettingsActivity`** — a thin shell that hosts the `SettingsFragment`
  (a `PreferenceFragmentCompat`) as its only content.

The two screens share one `SharedPreferences` store (androidx
`androidx.preference.PreferenceManager`, migrated from the legacy
`android.preference.PreferenceManager`), read/written through the `SK_*` key
constants in `SettingsFragment`.

### Packages

- `com.trikset.gamepad` — app logic: `MainActivity`, `SettingsActivity`,
  `SettingsFragment`, `SenderService` (TCP), `SquareTouchPadLayout` (pads),
  `VideoStreamLoader` (MJPEG HTTP opener).
- `com.trikset.gamepad.mjpeg` — the MJPEG player (vendored origin, renamed
  from `com.demo.mjpeg` in Phase 5): `MjpegView` (SurfaceView + render
  thread), `MjpegInputStream` (frame parser), `MjpegFrameRenderer` (decoding
  - letterbox + FPS overlay).

## TCP command protocol (`SenderService`)

One persistent TCP connection to the robot (default `192.168.77.1:4444`).
Commands are newline-terminated plain text:

| Source | Command |
|--------|---------|
| Touch pad | `pad<N> x y`, `pad<N> up` |
| Magic button | `btn N down` |
| Wheel | `wheel <angle>` (`-100..100`) |
| Keepalive | `keepalive <ms>` |

Connection setup: connect timeout 5 s, `tcpNoDelay`, `keepAlive`,
`setSoLinger(true, 0)`, traffic class `0x0F`, input half-close. All network
work runs on a single-thread executor injected via the `SenderService`
constructor (default `Executors.newSingleThreadExecutor()`; tests substitute a
Robolectric `PausedExecutorService`). `connectAsync()` is guarded
by a `syncFlag` so only one connect task is ever in flight.

`send()` lazily connects, posts the write to the executor, and confirms on the
main thread — `PrintWriter.checkError()` after the write detects a dead socket
and triggers `disconnect("Send failed.")`. `setTarget()` disconnects whenever
the host or port changes (the preference listener calls it on every change).

### Keepalive

`DEFAULT_KEEPALIVE = 5000` ms, `MINIMAL_KEEPALIVE = 1000` ms. A
`ScheduledExecutorService` (constructor-injected, daemon-thread default)
fires a keepalive tick every `keepaliveTimeout - 300` ms (300 ms "to
compensate ping") and sends `keepalive <ms>`. Any sent command restarts the
timer. The timeout is configurable via `SK_KEEPALIVE`.

## Input handling

- **`SquareTouchPadLayout`** (left = pad 1, right = pad 2): converts a touch
  position to `-100..100` integer coordinates (y inverted) and sends
  `pad<N> x y` on movement beyond a sensitivity threshold; `pad<N> up` on
  `ACTION_UP`/`ACTION_CANCEL`. Coordinates are letterboxed into a square via
  `onMeasure`.
- **Magic buttons**: five buttons created in code (`recreateMagicButtons`),
  each sends `btn N down` and fires haptic feedback.
- **Wheel**: `MainActivity` registers an accelerometer `SensorEventListener`.
  The tilt maps to a `-100..100` angle in the pure `WheelController`
  (`nextAngle(x, y, currentAngle, step, enabled)`: acceleration floor, dead
  zone, clamp, and a `SK_WHEEL_STEP`-sized hysteresis step), and `MainActivity`
  sends `wheel <angle>` only when the controller reports a new angle (extracted
  from `processSensor`, ROADMAP Phase 2-F).

## MJPEG video pipeline

```
HttpURLConnection (5 s connect/read timeouts)
  -> MjpegInputStream   parses the multipart stream into JPEG frames
  -> MjpegFrameRenderer decodes a frame, computes the letterboxed Rect, draws
  -> MjpegView          SurfaceView; its render thread owns the loop + lockCanvas
```

- **`VideoStreamLoader`** (AsyncTask successor) opens the HTTP stream off the
  main thread and hands it to `MjpegView`. Executor + main handler are
  injectable for deterministic tests.
- **`MjpegView.MjpegRenderThread`** loops `readMjpegFrame()`; on `IOException`
  it stops and fires `OnStreamErrorListener`.
- **Reconnect-on-error** (no forced periodic restart): `MainActivity` registers
  the listener in `onResume`; it marshals to the main thread and calls
  `restartVideoStream()`, which re-runs `VideoStreamLoader`. See MEMORY.md
  "MJPEG: reconnect-on-error" for the rationale.
- Default URI is `http://<host>:8080/?action=stream`; changing the host
  preference rewrites `SK_VIDEO_URI` to match. Cleartext HTTP is allowed via
  `android:usesCleartextTraffic="true"`.

`MjpegFrameRenderer` is deliberately separate from the view so the decode /
letterbox / FPS logic is testable under Robolectric (inject a decoder + plain
`Canvas`); the SurfaceView render-thread plumbing itself stays ~0 % covered
and is excluded from the coverage gates.

## Settings

Seven keys (`SK_*` in `SettingsFragment`): `SK_HOST_ADDRESS`, `SK_HOST_PORT`,
`SK_SHOW_PADS` (pad opacity), `SK_VIDEO_URI`, `SK_WHEEL_STEP`,
`SK_ABOUT_SYSTEM` (read-only system-info row that copies to clipboard),
`SK_KEEPALIVE`. `MainActivitySettingsController` (implements the `SettingsUi`
callback interface) owns the preference-change handling: retargets the
`SenderService`, updates the action-bar title, rewrites the video URI on host
change, animates pad opacity, parses the video URL, validates the keepalive
value, and clamps the wheel step. Extracted from `MainActivity`'s inline
listener (ROADMAP Phase 2-E) so the logic is directly testable without
reflection.

## Test layering

| Layer | Location | Runs |
|-------|----------|------|
| Robolectric unit tests | `app/src/test/` | `./gradlew test` (3 build-type variants in parallel JVMs, no device) |
| Instrumented (Espresso) | `app/src/androidTest/` | `./gradlew connectedDebugAndroidTest` (emulator; best-effort on CI) |

Unit-test network coverage avoids real sockets: `SenderServiceTest` uses its
own `DummyServer` on an ephemeral port, `SquareTouchPadLayoutTest` exercises
the touch math with an injected `SenderService`, and `MjpegInputStreamTest`
feeds synthetic frame bytes. Two distinct `DummyServer` classes exist (unit vs
androidTest) — do not merge them (see `TESTING.md`).

Instrumented tests use `FocusAwareActivityTestRule` and pre-empt the
immersive-mode confirmation overlay; the app runs edge-to-edge landscape.

## Cross-cutting constraints

- Immersive mode: system bars hidden by default, shown transiently by swipe;
  a `HideRunnable` re-hides them after 3 s. Required for targetSdk 36
  (edge-to-edge).
- Haptics on pads/buttons via `HapticFeedbackConstants`.
- Resources are English-only; `MissingTranslation` is a lint warning, not an
  error.
