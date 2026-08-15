# Architecture — trik-gamepad

<!-- encoding: utf-8 -->

Scope: how the app is put together — module map, the TCP command protocol, the
MJPEG video pipeline, and how the test layers map onto the code. Also the home
for **useful architecture notes and quirks** and the **domain knowledge /
best-practice reference** (threading, ViewModel/StateFlow, SurfaceView, sensors,
build tooling). Written for new contributors and future sessions; keep it
code-accurate (it is reviewed against `app/src` whenever it is touched).
Decisions (problem → alternatives → why → out-of-scope) live in
`DECISIONS.md`, not here. Live gate values/commands live in the build scripts
(see AGENTS.md "Sources of truth") — reference, don't restate.
Structure: Module map → TCP command protocol → Input handling → MJPEG video
pipeline → Settings → Test layering → Cross-cutting constraints → Domain
knowledge & best-practice reference → Known pitfalls & quirks.

## Module map

Single Gradle module `app/` (pure Kotlin, 0 `.java`). Two activities:

- **`MainActivity`** — the gamepad itself: two touch pads, a configurable
  magic-button row (0–5, default 3), a sensor-driven wheel, and the MJPEG video
  stream. Owns the
  `SenderService`, implements the `SettingsUi` view callbacks, and is the only
  place the app's UI lives. UI helpers extracted (Campaign 2): `MagicButtonPanel`
  builds the `btn N down` buttons; `SystemUiController` owns the immersive
  system-bar toggle + delayed auto-hide. Campaign 8 wiring: owns a
  `VideoRetryController` (bounded video-stream retry, see "MJPEG video
  pipeline") and the video-loading indicator (`@+id/videoLoading` spinner shown
  on load/reconnect, hidden on the first decoded frame).
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
  `SettingsFragment`, `SenderService` (TCP) + its helpers `ConnectRunnable`,
  `KeepAliveTimer`, `ConnectionState`, `SenderViewModel`, `ConnectionFeedback` +
  `ConnectionIndicator` (status pill / gear-border color), `SquareTouchPadLayout`
  (pads) + `TouchPadController` (pad math), `MagicButtonPanel` +
  `MagicButtonSymbols` (magic buttons), `HardwareGamepadController`,
  `VideoStreamLoader` (MJPEG HTTP opener), `RawSocketHttpStream` (raw-socket
  HTTP client), `VideoRetryController` (bounded retry),
  `VideoStreamErrorNotifier` (throttled failure notice), `RobotPresetStore`,
  `WheelController`, `SystemUiController`, `MainActivitySettingsController`.
- `com.trikset.gamepad.mjpeg` — the MJPEG player (vendored origin, renamed
  from `com.demo.mjpeg` in Phase 5): `MjpegView` (SurfaceView + render
  thread), `MjpegInputStream` (frame parser), `MjpegFrameRenderer` (decoding
  - center-crop cover + FPS overlay).
- `com.trikset.gamepad.diagnostics` — user-facing diagnostics (Campaign 14):
  `AppLog` (logcat + ring buffer) + `LogRingBuffer`, `DiagLevel`, `DiagnosticsReport`,
  `ReportDiagnosticsWriter`/`ReportSharer`, `CrashLogStore`, `CrashHandler`,
  `CrashReportDialog`. `App` (Application) lives in the root package.

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
- **Magic buttons**: `MagicButtonPanel` builds `1..count` buttons (count 0–5,
  default 3; display glyphs via `MagicButtonSymbols`, defaults ▲ ■ ● ✕ ◆),
  each sends `btn N down` and fires haptic feedback.
- **Wheel**: `MainActivity` registers an accelerometer `SensorEventListener`.
  The tilt maps to a `-100..100` angle in the pure `WheelController`
  (`nextAngle(x, y, currentAngle, step, enabled)`: acceleration floor, dead
  zone, clamp, and a `SK_WHEEL_STEP`-sized hysteresis step), and `MainActivity`
  sends `wheel <angle>` only when the controller reports a new angle (extracted
  from `processSensor`, ROADMAP Phase 2-F).

## MJPEG video pipeline

```
RawSocketHttpStream (http; bypasses NSC) / HttpURLConnection (https fallback)
  5 s connect/read timeouts
  -> MjpegInputStream   parses the multipart stream into JPEG frames
  -> MjpegFrameRenderer decodes a frame, computes the center-crop Rect, draws
  -> MjpegView          SurfaceView; its render thread owns the loop + lockCanvas
```

- **`VideoStreamLoader`** (AsyncTask successor) opens the HTTP stream off the
  main thread and hands it to `MjpegView`; it reports open success/failure via
  an `onResult` callback (a failed open is no longer silent). Executor + main
  handler are injectable for deterministic tests.
- **`MjpegView.MjpegRenderThread`** loops `readMjpegFrame()`; on `IOException`
  it stops and fires `OnStreamErrorListener`; on the first decoded frame of each
  playback cycle it fires `OnFirstFrameListener` (the loading indicator hides
  here — fired on decode, not canvas draw).
- **Reconnect-on-error** (no forced periodic restart): `MainActivity` registers
  the listener in `onResume`; it marshals to the main thread and calls
  `restartVideoStream()`, which re-runs `VideoStreamLoader`. See `DECISIONS.md`
  "MJPEG: reconnect-on-error" for the rationale.
- **Bounded retry (Campaign 8)** — `VideoRetryController`: while the activity is
  resumed ∧ control `connectionState is Connected` (the keepalive proxy) ∧ a
  video is configured ∧ `!view.isPlaying`, reloads the stream on a 5 s tick,
  and immediately on the control-`Connected` edge. Gated on the control
  connection so a dead robot is never hammered; idle recovery is bounded by user
  interaction. Decision + rationale: `DECISIONS.md` "Campaign 8 — bounded,
  control-gated video retry". **Loading indicator:** a centered spinner
  (`@+id/videoLoading`) shows while loading/reconnecting and stays up while the
  robot's video is disabled (no frame ever decodes).
- Default URI is `http://<host>:8080/?action=stream`; changing the host
  preference rewrites `SK_VIDEO_URI` to match. Cleartext HTTP is handled by the
  raw-socket client (Campaign 5), which bypasses Network Security Config, so the
  NSC `192.168.77.1` cleartext whitelist is defensive only (for any platform-HTTP
  path); see `res/xml/network_security_config.xml`.

`MjpegFrameRenderer` is deliberately separate from the view so the decode /
center-crop / FPS logic is testable under Robolectric (inject a decoder + plain
`Canvas`); the SurfaceView render-thread plumbing itself stays ~0 % covered
and is excluded from the coverage gates.

## Settings

Keys are the `SK_*` constants in `SettingsFragment` (host/port, pads alpha,
video URI + `SK_RESET_VIDEO_URI`, wheel step + `SK_WHEEL_ENABLED`, keepalive,
`SK_KEEP_SCREEN_ON`, `SK_HIDE_CONTROLS`, `SK_SHOW_FPS`, `SK_GAMEPAD_SWAP`,
`SK_MAGIC_BUTTON_COUNT` + glyphs `magicSymbol1..5`, robot presets
save/delete/apply, copy-IP, about-system, and the `SK_ADVANCED` sub-screen
key). The root screen shows the Basic categories inline; the Advanced settings
sub-screen (Campaign 10 restructure) is reached through `SK_ADVANCED`.
`MainActivitySettingsController` (implements the `SettingsUi`
callback interface) owns the preference-change handling: retargets the
`SenderService`, hides pads/buttons on a blank host, animates pad opacity,
parses the video URL, validates the keepalive value, and clamps the wheel
step. The video URI is never implicitly overwritten on host change — the
explicit "Reset video URI to robot default" preference fills it. Extracted
from `MainActivity`'s inline listener (ROADMAP Phase 2-E) so the logic is
directly testable without reflection.

## Diagnostics & user-facing reporting (Campaign 14)

Offline-first: all diagnostic data stays on-device until the user reviews and
explicitly shares it — no new permissions, no proprietary SDKs, works on every
store incl. F-Droid (rationale: DECISIONS.md). The pieces live in the
`com.trikset.gamepad.diagnostics` package:

- **`AppLog`** — the single log channel. Every `d/i/w/e/v` call mirrors to
  logcat (gated by `Log.isLoggable`, preserving the old DEBUG-gated behavior)
  and, when it passes the buffer floor (`minBufferLevel`, default INFO), into a
  **500-line synchronized ring buffer** (`LogRingBuffer`, extracted pure class).
  The floor is set by the "Diagnostics verbosity" list (Errors only / Info /
  Debug / Verbose) via `DiagLevel`, applied at `App` startup and in
  SettingsFragment. Level rebalance (Campaign 14): connect/disconnect and the
  keepalive heartbeat log at INFO (they land in the default report); per-command
  "Sending" stays DEBUG (excluded by default).
- **`DiagnosticsReport`** — builds the one-file markdown data block: app
  version/versionCode/build type, device manufacturer/model/product, Android
  release/API, display resolution/density/font scale, locale, the live
  `ConnectionState` (the crash dialog passes it; SettingsFragment has no
  sender, so it reports "not running"), the full settings snapshot with
  `(default)` markers, robot presets, the log tail and the last crash trace.
- **`ReportDiagnosticsWriter` + `ReportSharer`** — write
  `cacheDir/diagnostics/trik-gamepad-report-<ts>.md` and open it via a
  FileProvider (`exported=false`, `<cache-path diagnostics/>` only; manifest
  also declares the `<queries>` intent for `ACTION_EDIT`/`text/plain` so
  `queryIntentActivities` sees editors on API 30+). Default flow: **text editor
  for review/edit** (chooser title = the instruction), then share from the
  editor (mail/IM). The "Share logs without editing" switch (default off) or a
  missing editor falls back to a direct `ACTION_SEND` share sheet with the file
  attached. The FileProvider call is a thin device-only seam (Robolectric
  cannot resolve path XML); the intent logic takes an injected `Uri`.
- **Crash capture** — `App : Application` installs a chaining
  `CrashHandler`; `CrashLogStore` persists the newest ≤3 stack traces to
  internal storage and owns the once-per-crash prompt state. `CrashReportDialog`
  (shown by MainActivity at launch) offers Review & share / Copy / Dismiss,
  honoring the share-without-editing switch.
- **About rows** — "Report an issue", "Share logs without editing", "Copy
  report", "View log" (in-app 200-line tail dialog); the About-system tap now
  copies the full report. Every option carries a description (summary /
  summaryOn+Off), per the repo convention.

## Test layering

| Layer | Location | Runs |
|-------|----------|------|
| Robolectric unit tests | `app/src/test/` | `./gradlew test` (3 build-type variants in parallel JVMs, no device) |
| Instrumented (Espresso) | `app/src/androidTest/` | `./gradlew connectedDebugAndroidTest` (emulator; CI runs them on `aosp_atd` — green since 2026-08-08) |

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
- Resources ship 5 locales (`en`/`ru`/`fr`/`de`/`vi`); translation key +
  format-specifier parity is enforced by `scripts/check_translations.py --sync`
  (gate.py + CI), so `MissingTranslation` stays a lint warning — see
  `DESIGN.md "Localization"`.

## Domain knowledge & best-practice reference

Durable reference from the strict full-project review (Campaign 3). Each item
is the "why" behind the code shape above; revisit the decision log in
`DECISIONS.md` for the problem/alternatives context.

### Raw TCP sockets in an Activity (gamepad pattern)

Work that runs only while the user interacts belongs on a thread/executor
created by the component, NOT a Service — a Service spawns its own thread
anyway and a started Service is still killable. A **foreground Service** is
only for surviving the user leaving the app, and needs a declared
`android:foregroundServiceType` + permission on targetSdk 34+ and Play scrutiny
(a gamepad connection has no natural FGS type). **The real problem is
rotation**: an Activity-owned socket is closed/reopened on every config change.
The recommended fix is a **ViewModel-owned** connection (survives rotation,
`onCleared()` closes it) — which this app adopted in Campaign 3 P2. Process
death still kills it, but settings live in SharedPreferences so re-derivation
is free.

### Threading model

Coroutines are the modern recommendation, but the socket must be cancelled
cooperatively — a blocking `read()` will NOT cancel; the only reliable unblock
is closing the socket/connection from another thread. Keep the single dedicated
connection thread + SurfaceView render thread if not migrating to coroutines;
create the pool once, not per connection.

### MJPEG-over-HTTP client

`multipart/x-mixed-replace` — per-part `Content-Length` is often ABSENT, so
byte-scan to the next boundary. Read/socket timeouts must exceed the
inter-frame gap (else a paused robot causes a needless reconnect). On any
IOException: tear down fully + reconnect (with backoff); validate HTTP 200 +
Content-Type first. `InputStream.read` blocks and is not interruptible — unblock
by disconnecting. Decode JPEGs off the network thread; reuse Bitmaps; always
draw the LATEST complete frame.

### SurfaceView vs TextureView

SurfaceView is the recommended, higher-perf pattern for a render thread
(`lockCanvas`); it punches a hole in the window so sibling overlays cost an
alpha-blend per frame, and post-layout transforms of siblings glitch below
API 24. TextureView behaves like a normal View (overlays "just work") but does
an extra buffer copy per frame and has hardware-acceleration +
single-producer constraints. This app draws its FPS text INTO the Surface canvas
— keep it that way (fastest).

### ViewModel / StateFlow

ViewModel is the sanctioned "business logic state holder"; survives rotation,
cleared on Activity finish via `onCleared()` (close the socket there).
`viewModelScope` is hardcoded to `Dispatchers.Main` — background it with
`withContext(Dispatchers.IO)` for socket work. Don't hold a Context in a
ViewModel (use `AndroidViewModel` if you must). Expose connection state as an
immutable `StateFlow<ConnectionState>` (sealed Connecting/Connected/
Disconnected); collect with `repeatOnLifecycle(STARTED)` — NOT the deprecated
`launchWhenX` (they suspend instead of cancel, wasting resources).
`SavedStateHandle` survives process death; only primitives/small strings
(never sockets/threads). UI-logic state → `onSaveInstanceState`.

### Preferences in 2026

androidx.preference is still the documented settings UI (the platform
`android.preference` package is deprecated since API 29). Backend defaults to
SharedPreferences; **DataStore** is the recommended storage layer (async,
transactional, Flow reads, singleton per file) via `PreferenceDataStore`. The
`OnSharedPreferenceChangeListener` is held strongly and process-global — an
Activity that registers one and skips unregister LEAKS; the fragment-managed
`setOnPreferenceChangeListener` or a DataStore Flow avoids manual lifetime
entirely.

### Sensors

Register in `onResume`, unregister in `onPause` (hard best practice — the
system doesn't disable sensors on screen-off; unregistered listeners drain
battery). Use the slowest rate that works (`SENSOR_DELAY_NORMAL` = 200 ms,
`UI` = 60 ms, `GAME` = 20 ms; capped at 200 Hz). For a gamepad wheel,
`SENSOR_DELAY_GAME` is the sweet spot.

### Edge-to-edge (targetSdk 35+/36)

Enforced — the window draws behind the system bars automatically; you must
handle insets. `WindowCompat.enableEdgeToEdge` for older devices. There is **no
action bar and no options menu** on the gamepad HUD (Campaign 17 removed the
legacy green action bar + `menu.xml`): the top-left robot chip opens Robot
settings and the gear button opens App settings directly. `SystemUiController`
hides the system bars for the immersive gamepad view.

### Kotlin idioms

Prefer string templates to `String.format` for pure interpolation (keep format
only for locale-aware numeric padding); the current recommendation is
**`Locale.ROOT`, not `Locale.US`** (detekt 2.0-alpha.6 ships a rule). Prefer
`if` for binary conditions, `when` for 3+ options; `data class` for value
holders (e.g. `TouchPadController.Command`); `@JvmField`/`@JvmOverloads`/
`@JvmStatic` exist only for Java interop — this repo has 0 Java files, so they
are dead weight to remove. Default to `private`, use `internal` only for
cross-package test access.

### AGP 9 / Gradle 9 build

AGP 9 runtime-depends on KGP 2.2.10 (this repo's built-in Kotlin). Config cache
is preferred and will be on by default in Gradle 10; when active it FORCES
intra-project parallelism (the spotlessApply-vs-test race is inherent — the
two-invocation gate `scripts/gate.py` is correct). Version catalogs
(`gradle/libs.versions.toml`) are the documented centralization (Google's AGP-9
migration docs assume TOML). Dependency locking optional at this size. **detekt
1.23.8 predates AGP 9** (built vs AGP 8.8/Gradle 8.12) — 2.0.0-alpha.3+ adds
real built-in-Kotlin support; upgrade when stable, don't disable config-cache
if detekt flakes. ktfmt = deterministic zero-config formatter; ktlint = linter
(de-facto standard, ships detekt integration). Lint 9.2.1: report-output DSL
(`htmlReport`/`textReport`) is deprecated → `SingleArtifact.LINT_*_REPORT`;
known lint bugs (SDK resolution not a task input → caching, "Could not clean up
K2 caches"). Generate baselines with the aggregate `lint` task.

### Robolectric

PAUSED is the only recommended LooperMode (LEGACY deprecated). Threads/
executors are NOT under the shadow scheduler — stop every ExecutorService
explicitly and gate on latches/timeouts. Plain `java.net.Socket` works on the
JVM. **Robolectric 4.16 dropped API 21/22 (min now 23)** — the app's minSdk
was raised to 23 in Campaign 3, so `Config.OLDEST_SDK` now equals the declared
min (intended). `@Config(sdk = [OLDEST, TARGET, NEWEST])` triples run time
(each SDK downloads its own android-all jar). Known AGP-9 issue: built-in
Kotlin breaks kapt-based custom-shadow registration (workaround
`com.android.legacy-kapt`; fix = KSP).

### Espresso on headless CI

`RootViewWithoutFocusException` causes are system overlays/dialogs, animations
mid-flight, keyguard, keyboard, immersive confirmation. Fixes: disable all 3
animation scales, dismiss keyguard, request focus, `inRoot(...)`, verify with
`dumpsys window mCurrentFocus`. Custom ViewActions: always send UP in `finally`
(a missed UP hangs); coords must be within the view's visible bounds; known
Espresso bugs to avoid replicating (TOOL_TYPE_UNKNOWN swipes, coordinate defect
#1840).

### CI emulator

aosp_atd = less CPU/faster boot/no GMS but documented as less reliable for
complex UI tests (this repo's 9 tests pass — fine). Use KVM on ubuntu-latest;
`swiftshader_indirect` is the correct headless GPU. Orchestrator: per-test
restarts (crash isolation + clean package data) vs runtime cost — for a 9-test
suite it's a deliberate keep; re-weigh if it grows. Fixed `localhost:12345`
DummyServer port collides if ever sharded.

### JaCoCo

Branch > line as a signal; the coverage gate keeps branch stricter than line.
The current thresholds are not repeated here — they live and are enforced in
`app/build.gradle` (`jacocoTestCoverageVerification`) via `scripts/gate.py` and
CI; the QA discipline around them is in TESTING.md.
`includeNoLocationClasses` defaults false (Kotlin classes silently excluded →
report can look better than reality). Verification task reports only the FIRST
violated rule. Instrumented tests aren't covered by the JVM agent (offline
instrumentation needed) — the coverage gates measure Robolectric + JVM only.
Coverage measures what RAN, not correctness — the wheel-step and TYPE_ALL bugs
passed the coverage gates because their tests assert "no crash", not real
behavior.

## Known pitfalls & quirks

Bug classes and traps that cost real debugging time; the rule for each is in
AGENTS.md / TESTING.md, the full story in the relevant decision/retrospective.

- **"Setting parsed via the wrong API" bug class.** `Integer.getInteger(pref, default)` reads a **JVM system property**, not the pref; `Sensor.TYPE_ALL` is
  a **mask**, not a sensor type. Both passed the coverage gate because the
  tests asserted "no crash". Assert real behavior, not just the absence of an
  exception.
- **Static-analysis UP-TO-DATE false-green.** detekt (and other analyzers) can
  stay `UP-TO-DATE` when new source files arrive via an untracked path — the
  local gate "passes" while CI fails. After adding/renaming sources, force one
  real pass: `./gradlew detekt --rerun-tasks` (AGENTS.md "Exit 0 ≠ tool ran").
- **pre-commit re-format dance.** The local `spotlessApply` hook reformats
  Kotlin files AFTER staging, so the first commit attempt silently fails with
  "files were modified by this hook". Fix: re-`git add` + re-commit.
- **Robolectric `@GraphicsMode(NATIVE)` API-23 quirk.** Under native graphics,
  `BitmapFactory` decodes correctly on the default SDK but **API 23 pixels
  decode near-black** (e.g. solid red → r=1,g=0,b=0). Not an app bug —
  byte-identical frames still prove parser/decode correctness. Color-asserting
  tests must pin `@Config(sdk=[TARGET_SDK])`.
- **MJPEG parser pacing rule.** `MjpegInputStream.readMjpegFrame()` returns a
  frame only when `available() < 2*contentLength`; small frames are dropped
  first when the client lags. Synthetic MJPEG servers should emit
  **comparable-JPEG-size** frames at ~50 ms pacing.
- **MJPEG leak fix pattern.** A render thread blocked in a non-interruptible
  `InputStream.read` cannot be unblocked by `join(timeout)` — close the stream
  **from another thread** (the documented unblock pattern) and suppress the
  error-listener on deliberate stops via a `stopping` flag, or the close
  spuriously triggers a reconnect.
- **`window`/activity-scoped state in a field initializer.** `Activity.window`
  is only assigned during `attach()` (after the constructor) — a field
  initializer touching it throws in Robolectric ("Window creation failed!") and
  NPEs on device. Use `by lazy` or a provider lambda.
- **Lint-baseline location matching is exact.** An entry whose `location file`
  is a machine-specific absolute path (e.g. the wrapper properties outside the
  module) silently stops matching on CI. Env-dependent checks
  (`OldTargetApi`, `AndroidGradlePluginVersion`) belong in `lint.xml`, not the
  baseline; prune stale baseline entries by hand after a build-file refactor.
