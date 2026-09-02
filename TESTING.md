# Testing — quality-assurance discipline

<!-- encoding: utf-8 -->

Aim: how this app's quality is enforced, measured, and kept honest — written
for the next developer or agent to use as a reference when writing, running, or
debugging tests, and for a smart power user to read and trust the app. This is
NOT a changelog and NOT a metrics dashboard:

- **History** (campaign records, ratchet chronology, dated verifications) →
  `MEMORY.md` / `DECISIONS.md` / `docs/ROADMAP.md`.
- **Live numbers** (gate thresholds, measured coverage, token totals) → the
  build scripts + CI (`app/build.gradle`, `scripts/gate.py`,
  `.github/workflows/ci.yml`); re-measure at the end of each campaign and
  record the result in that campaign's record — never here.
- **Decisions** (problem → alternatives → why → out-of-scope) → `DECISIONS.md`.

Structure: Overview → Running tests → Diagnostic discipline → Patterns →
Edge-case audit → Test quality discipline → Known gaps.

## Overview

Two layers:

- **Robolectric unit tests** (JVM, no device): `SenderServiceTest` runs under
  three SDK configs per build type and covers the TCP `SenderService`.
- **Espresso instrumented tests** (`androidTest`): `KeepAliveTests`,
  `MainWindowTests`, `SettingsTests`, `MagicButtonsTests`, run with AndroidX
  Test Orchestrator. CI runs them on an API 36 emulator via GitHub Actions;
  locally they need a running emulator.

## Running tests

```sh
./gradlew test                      # all unit-test variants (debug/release/releaseDebug)
./gradlew testDebugUnitTest --tests "com.trikset.gamepad2.SenderServiceTest.senderServiceShouldSendSingleCommandCorrectly"   # single test
./gradlew connectedDebugAndroidTest # instrumented; needs emulator/device
```

> **Known local flake: Robolectric download lock.** `./gradlew test` runs the
> three variants in parallel JVMs, which race for the user-level
> `~/.robolectric-download-lock` file. One variant can fail its whole class
> with `Couldn't create lock file ...` pointing at `~/.robolectric-download-lock`
> (`IllegalStateException` at `MavenDependencyResolver`). Transient infra —
> re-run the failed variant (e.g. `testReleaseUnitTest`) and it passes. Never
> treat this as a code regression.

Local instrumented run (per-platform acceleration prerequisites):

| OS | Hypervisor | Verify |
|----|-----------|--------|
| Windows | AEHD (Android Emulator Hypervisor Driver) — installer: SDK `extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat` | `emulator -accel-check` → `0` |
| Linux | KVM (`/dev/kvm`, KVM group/mode 0666) | `emulator -accel-check` → `0` |
| macOS | Hypervisor.framework (macOS 11+, Intel & Apple Silicon) | `emulator -accel-check` → `0` (unverified — re-check on the first macOS box) |

1. Verify acceleration: `emulator -accel-check` (must return `0`).
1. Boot the **`aosp_atd` image** AVD `Atd_API36` — the lightweight, headless,
   CI-oriented image (lighter/faster boot than the `default` image; rationale +
   alternatives: `DECISIONS.md` "Local instrumented: adopt aosp_atd"). Correct
   launch (~instant via snapshot, suite ~5 min):
   `emulator -avd Atd_API36 -no-window -no-audio -no-boot-anim -gpu host`
   (drop `-no-window` to see the UI; keep snapshots enabled for fast reboots).
1. Wait for `adb shell getprop sys.boot_completed` → `1`.
1. **Disable the immersive-mode confirmation overlay** (required on API 35+):
   the gamepad runs immersive (system bars hidden), and the first time it enters
   immersive mode the system pops an `ImmersiveModeConfirmation` window that
   keeps focus away from the app. Espresso then fails every interaction with
   `RootViewWithoutFocusException`. Pre-empt it once per AVD:
   `adb shell settings put secure immersive_mode_confirmations confirmed`.
1. `./gradlew connectedDebugAndroidTest` — **with `--no-configuration-cache`**
   (under the configuration cache the task fails with an AGP/UTP serialization
   error, `field __testRunnerFactory__ ... DefaultConfigurableFileCollection`);
   on Windows also add `--no-daemon` for tool-driven runs (daemon
   handle-inheritance hang — AGENTS.md "Windows/PowerShell quirks").

> **Long-session emulator degradation:** after an emulator has been up through
> many suites, the suite fails *broadly* — logcat floods with
> `Sending oneway calls to frozen process` (the app-freezer under memory
> pressure freezes the app process, so the SenderService executor cannot run and
> commands after the first are dropped), the package service can go down
> (`Can't find service: package`), and the swiftshader focus flake returns.
> `adb reboot` does **not** clear it (the emulator process keeps its memory);
> kill + relaunch with the exact launch flags (`adb -s <port> emu kill`, then
> relaunch; Swiftshader_API36 uses `-no-snapshot` so every launch is cold) and
> settle ~30-60 s before re-running. (Full story: MEMORY.md "Testing".)

> **Do NOT use `-gpu swiftshader_indirect` locally.** Verified: with the
> software GPU the app window never receives focus and the same suite fails
> 8/8 with `RootViewWithoutFocusException` — the exact failure CI saw (decision
> and verification in `DECISIONS.md` "Local instrumented: adopt aosp_atd").
> Local runs must use `-gpu host`.

> **CI focus flake.** The `RootViewWithoutFocusException` storm on CI was traced
> to the immersive pre-empt racing the settings provider: `sys.boot_completed`
> reports `1` while the provider is still starting, so a single `settings put`
> during a slow headless boot was silently lost, and the
> `ImmersiveModeConfirmation` overlay then stole focus for the whole suite. The
> ci.yml pre-empt now **retries the settings write until `settings get` confirms
> it** (up to 60 s), and `FocusAwareActivityTestRule` waits (and
> bounded-BACK-dismisses) for window focus, skipping the wait for non-view
> tests. **Remaining CI instrumented instability:** swiftshader rendering errors
> (`Failed to find ColorBuffer`) can still hang Espresso interactions under
> load on 2-4-core runners — a software-GPU resource issue, not a code
> regression. Treat those (not focus) as the known CI flake.

## Diagnostic discipline

- **Baseline first**: unexpected errors? Stash changes, run the same command.
  If it still fails → pre-existing, don't chase ghosts.
- **Batch before re-run**: found one failure? Grep for siblings and fix all
  before re-running — each re-run costs the full suite.
- **Instrument the failing test, not just the app**: after reading the
  sources, add targeted diagnostics to the test itself — put the server's
  received messages in the assert message (`expected X, received so far: …`),
  log the tapped views' bounds at tap time, dump a non-blocking main-thread
  stack (`Thread.getAllStackTraces()`), and read the per-test logcat under
  `app/build/outputs/androidTest-results/<avd>/logcat-*.txt`. The root cause
  of a repeated instrumented failure is often in the test harness/injection,
  not the app.
- **`NoActivityResumedException: Pressed back and killed the app` = the test
  navigated one level too far**, not a flake. A navigation-flow change (e.g.
  flattening a two-level settings screen to one) leaves tests doing one extra
  `pressBack()`, which then pops the root activity and kills the app (C18:
  C17's settings split flattened `Settings → Advanced → back → back` to
  `chip → RobotSettings → back`, but the tests still pressed back twice).
  When this signature appears, re-count the navigation depth the test drives
  and update the test, don't re-run it.
- **`connectedDebugAndroidTest` uninstalls the app after the suite**: UI-proof
  screenshots taken after an instrumented run show the launcher, not the app.
  Capture `.tmp` proofs BEFORE the suite, or `adb install -r` the debug APK
  again afterwards (hit C18).
- **Full output while debugging**, quiet mode only for the final green check.

## Patterns

### Test TCP servers — ephemeral unit server vs fixed-port instrumented server

- `app/src/test/.../TestTcpServer.kt` is the **shared unit** server (merging
  the old inner `DummyServer` + `ReadUntilStopServer`). It binds an
  **ephemeral port** (`ServerSocket(0)`), exposes `port`, `awaitConnection()`,
  `awaitCount()`, the bounded-poll `awaitReceived()` (with a caller-supplied
  drain), and `closeClient()`. Always `use {}` it.
- `app/src/androidTest/.../DummyServer.kt` is a separate class binding
  `localhost:12345`, used by the instrumented tests.
- **Never reintroduce fixed ports in the unit test**: `./gradlew test` runs
  three variants in parallel JVMs; fixed ports caused `BindException` cascades
  (see MEMORY.md Testing).
- **Never merge the two**: the instrumented server is fixed-port by design
  (single emulator process); the unit server must stay ephemeral (3 parallel
  JVMs).

### Synthetic MJPEG server — the third test server

`app/src/test/.../mjpeg/SyntheticMjpegServer.kt` is a real HTTP
`multipart/x-mixed-replace` MJPEG server (ephemeral port) that streams seeded
JPEG frames to the app's real `VideoStreamLoader`/`MjpegInputStream` decode path
and can **drop the connection after N frames** to exercise the reconnect.
Companion `mjpeg/MjpegServerTest.kt` (the single consolidated video/streaming
test — it replaced the old `SyntheticMjpegServerTest` + `VintageCatVideoStreamTest`):

- **Must run under `@GraphicsMode(GraphicsMode.Mode.NATIVE)`** — default
  Robolectric graphics return fake bitmaps, making decode correctness/perf
  assertions meaningless.
- **Frames are committed CC0 test fixtures** (`src/test/resources/mjpeg/`, a
  vintage cat illustration at 640×480 / 320×200 / 1000×600 + the CC0 license
  text) — no in-memory JPEG generation anymore.
- **Pace the server (~50 ms) and keep seeded JPEGs comparable in size** when
  cycling multiple frames: the parser's `available() < 2*contentLength` gate
  drops small frames first when the client lags.

### Asserting haptics in Robolectric

`shadowOf(view).lastHapticFeedbackPerformed()` returns the constant passed to
`view.performHapticFeedback(...)` or \*\*`-1** when none was performed (so `KEYBOARD_TAP`= 3,`LONG_PRESS`= 0, and -1 = "no haptic"). The`ShadowView` hook records the call regardless of attach state. Call it as a **method** (`()\` in Kotlin — it is a Java getter).

Two layers of assertion:

- **Exact mapping** lives in `HapticsTest`: `Haptics.constant(level)` pins the
  generic-constant trio (TICK→`KEYBOARD_TAP`, CLICK→`VIRTUAL_KEY`,
  HEAVY→`LONG_PRESS`) and the reject sequence shape (two HEAVY, 200 ms).
- **Interaction tests** (pad/buttons/connection) assert the **semantic level**:
  `Haptics.constant(Haptics.Level.X) == shadowOf(view).lastHapticFeedbackPerformed()`.
  Never assert a raw API-30+ constant (e.g. `CONTEXT_CLICK`) here — the unit
  suite runs under **SDK 23** (the `[23]` qualifier), so the app maps to the
  legacy constant and the assertion would fail for the wrong reason.

Pad pattern (`SquareTouchPadLayoutTest`): `ACTION_DOWN` → `TICK`,
`ACTION_UP` → `CLICK`, and `-1` after `ACTION_CANCEL`/`ACTION_MOVE`/plain
`send()` — this distinguishes "the wrong level fired" from "nothing fired".
Buttons/gear/chip → `HEAVY`; connect → `CLICK`; unexpected disconnect → the
reject sequence ends on `HEAVY` (drain the delayed `RejectHaptic` runnables
with `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()`); pause disconnect
→ no sequence (last haptic stays the connect `CLICK`).

### HUD theme screenshots (`HudThemeTest`)

`HudThemeTest` renders the **real** `MainActivity` view hierarchy over a real
cat-video frame for each connection state, analyzes the frame in-process for the
theme's main features, and saves one PNG per state to the always-on
`screenshots.dir` build-output property (`app/build/outputs/screenshots/`, wired
in `app/build.gradle` `testOptions`). `hud_connected.png` /
`hud_connecting.png` / `hud_standby.png` / `hud_error.png`. Rendering is
`@GraphicsMode(NATIVE)` + `@Config(sdk=[TARGET_SDK])` so Skia rasterization is
real and deterministic; glyph-text fidelity under Robolectric can be imperfect,
so the in-process assertions are structural (visibility/text/bounds/alpha) plus
a source-pixel-mapping check that the video fills the screen edge-to-edge
(CROP mode, the version used by the screenshot tests), and a pixel-probe test
(`videoZonesShouldBeCleanAndOverlaysShouldOnlyDarken`) that verifies the
PRINCIPLE P7/P8 video fidelity contract: 5 clean-zone probes match the source
cat (within 1-bit Skia tolerance), pad/scrim overlay probes differ from the
source. Pixel bounds are computed at **mdpi** (Robolectric's default — emulator 440dpi
bounds do NOT match the in-test render), and the cat fixture has black vignette
borders, so edge assertions must map pixels to the source, never expect
"bright". Set `SK_SHOW_PADS=255` to make the connected-vs-dimmed alpha contrast
visible (the default 100/255 ≈ 0.39 hides it).

### Why awaits are required

The server thread accepts and reads asynchronously. Asserting immediately after
`mExecutor.runAll()` + `shadowOf(getMainLooper()).idle()` races the server.
Always await the latch (`awaitConnection()` / `awaitCount(n)`) before
asserting connected/command state.

**Bounded polls for server-received data, never bare asserts.** A test that
asserts `server.receivedContains("two")` right after `runAll()` + `idle()`
passes locally but can fail on CI under load — the server read thread simply
hasn't drained the socket yet (a `sendWhileConnectedShouldSkipReconnect`
regression caught only by CI, 3 red runs). Any assert on *server-received
content* must poll in a bounded loop (the
`keepaliveShouldBeSentWhileConnected` pattern: `while (!seen && attempts < N) { Thread.sleep(...); runAll(); idle(); }`), or use the latch/`anyMessageWithin`
helpers. Rule: **local green ≠ CI green for async-server tests** — write the
bounded poll from the start, don't rely on the "run twice" rule to catch it.

### tap→command wiring — direct `performClick()` over touch injection

For instrumented tests whose point is "view N fires command N" (e.g. the magic
buttons sending `btn N down`), drive the click with a `ViewAction` that calls
`view.performClick()` directly instead of Espresso's touch `click()` **when the
app does async work around the taps** (a control connect, video reload). A
touch-injected tap that lands while the main thread is mid-transition is
**silently dropped** — the button's DOWN/UP never fires its listener and no
Espresso error is raised.

- **Signature:** the outer buttons of a row fire (`[btn 1 down, btn 5 down]`)
  while the middle/second taps never send. Proven not a layout issue
  (uiautomator bounds: buttons y 945-1077, pill y 486-594, overlay ends y 942 —
  no overlap), not a socket issue (one connect, no disconnect, no `NotSent`).
- **Not an app bug:** real-device input is queued and processed in order; this
  is an injection-vs-main-thread race. Longer dwell and Espresso `click()`
  reduce but do not eliminate it; `performClick()` is deterministic.
- **When to still use touch injection:** `SquareButtonTest` (pad touch
  precision, multi-segment moves) genuinely exercises touch dispatch — keep raw
  `MotionEvents` there, and serialize taps behind bounded `awaitMessage` so a
  tap never lands mid-transition.
- **Exact-text matchers (`withText`/`withContentDescription`) break silently on
  rewording** — a UI string change (ellipsis titles, glyph-suffixed
  descriptions, C15) fails instrumented tests with a `NoMatchingViewException`
  and no hint. Grep `androidTest` for exact-text matchers before changing any
  user-visible string and update them in the **same commit** (AGENTS.md
  "Exact-text UI matchers").

### Robolectric determinism

`@LooperMode(PAUSED)` + a `PausedExecutorService` injected via the
`SenderService(mExecutor)` constructor make background task execution
deterministic: `mExecutor.runAll()` runs queued AsyncTask work; then idle the
main looper to run `onPostExecute`. Do not rely on real threads for the
`SenderService` executor.

### Robolectric shadow traps

- **Sensor tests need a `SensorEvent`, not a `Sensor`.** To feed the
  accelerometer path, build the event via the modern `SensorEventBuilder` API:
  `SensorEventBuilder.newBuilder().setSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)).setValues(floatArrayOf(...)).build()`
  (MainActivityTest has a `sensorEvent(type)` helper). The deprecated
  `ShadowSensorManager.createSensorEvent(3)` (1-arg form) defaults the sensor
  to **TYPE_GRAVITY (9)**, so `onSensorChanged` never matches the accelerometer
  branch and the wheel path silently stays uncovered even though the test
  passes. Set the values via `setValues`/`event.values`, and stub the sensor
  lookup. Trying `shadowOf(Class<Sensor>)` or the wrong constructor is a
  compile error (`no suitable method found for shadowOf`).
- **`ShadowLog.isLoggable` defaults to `level >= INFO`** (Robolectric 4.16),
  so `Log.isLoggable(TAG, Log.DEBUG)` is **false** unless a test raises the
  tag with `ShadowLog.setLoggable(TAG, Log.DEBUG)` — the 2-arg form (there is
  no boolean overload). The false side is covered by every ordinary test; to
  cover the true side of a `DEBUG`-gate, raise the tag in that test.
- **After adding a coverage test, confirm it moved the needle.** A passing
  test can still cover nothing (the sensor wheel path was "covered" by a
  passing test that used the wrong sensor type). Diff the JaCoCo per-class
  branch numbers for the target class after adding the test.
- **Covering null-guard branches on layout-populated fields needs explicit
  nulling.** `MainActivity.onPause`/`onResume` guard `video`/`sensorManager`
  with null checks, but the activity layout always provides those views in
  Robolectric — so a "no-op" test that just calls `onPause()` covers nothing
  (the JaCoCo needle did not move). To cover the null branch, explicitly
  `setField(activity, "video", null)` (and `sensorManager`) via reflection
  **before** invoking the method.
- **3 identical failures → stop and read the shadow source.** If the *same*
  test fails identically N≥3 consecutive runs (same exception, same line, log
  sizes near-identical), stop retrying and read the shadow's real API from the
  Robolectric jar/source instead of tweaking-and-rerunning.
- **`FileProvider.getUriForFile` throws under Robolectric** ("Failed to find
  configured root that contains ...") — path-XML resolution is unsupported in
  the sandbox. Keep the FileProvider call out of the testable path: inject a
  `Uri` into the sharing logic and leave the FileProvider line device-only
  (that is what `ReportSharer` does).
- **Robolectric has no `buildApplication`/`setupApplication`** (4.16.1). The
  runner creates the Application from the manifest, so
  `RuntimeEnvironment.getApplication()` *is* your declared `App`; to observe
  `App.onCreate` wiring, clear the pref and call `onCreate()` again on it.
- **`android.app.Dialog.getButton` is absent from the compile-SDK stub** (API
  36\) — `dialog.getButton(...)` will not compile. Cast the shown dialog to
  `androidx.appcompat.app.AlertDialog` (whose `getButton` is public) to reach
  the buttons. Also: appcompat button clicks post a `ButtonHandler` message to
  the main looper, so `performClick()` alone "does nothing" — run
  `shadowOf(getMainLooper()).idle()` before asserting the effect.
- **A shared singleton (like the `AppLog` buffer) is written by background
  threads from other test classes** (e.g. leaked keepalive schedulers), so
  exact-count assertions flake. Assert *presence* (`any { ... }`), and move
  exact capacity/order semantics into a pure class (`LogRingBuffer`) with
  hermetic tests.
- **`@string/copy` (and friends) collide with private androidx.preference
  strings** — lint `PrivateResource`. Prefix app-specific button strings
  (`copy_button`) or reference the string from your own XML.
- **Network capability fixtures cannot be fabricated in Robolectric.**
  `NetworkCapabilities$Builder` is absent from every installed SDK stub
  (android-23/30/35/36/36.1) and `Network(int)` is package-private. Build
  networks with `ShadowNetwork.newInstance(id)` and assert binding via
  `shadowOf(network).isSocketBound(socket)`. When the code under test needs to
  *choose* a network, inject a provider seam (e.g. `WifiSocketBinder`'s
  `wifiNetworkProvider: () -> Network?` or `WifiConnectionOpener`'s
  `networkOpen`/`defaultOpen`) instead of faking capabilities.
- **Verify overload API levels against the OLDEST_SDK variant, not
  compileSdk.** `ConnectivityManager.registerNetworkCallback(request, cb, Handler)` is API 26+ — the 2-arg form is API 21+. Under the OLDEST_SDK (23)
  Robolectric config the 3-arg form throws `NoSuchMethodError` at runtime
  (hit 2026-08-17, C23). Some overloads are newer than their base method;
  confirm against the minSdk stub jar (`javap` on the platform android.jar).
  (`Network.bindSocket(Socket)` is minSdk-23-safe, verified in android-23.)
- **`javaClass` inside `apply { }` binds to the receiver, not the enclosing
  class.** `KeyStore.getInstance(...).apply { javaClass.getResourceAsStream(...) }`
  resolves `javaClass` to `KeyStore`, so the resource is looked up via the
  bootstrap loader and a test-classpath file is missed ("missing test
  keystore", hit 2026-08-19). Use an explicit class literal
  (`HttpsMjpegServer::class.java`) inside `apply` blocks. Diagnose resource
  misses with a probe that prints `getResource(...)` URLs — if the probe passes
  while the code path fails, the difference (receiver-bound `javaClass` vs
  explicit literal) is the root cause, not stale build state.
- **Self-signed `https` integration tests need no network.** `com.sun.net.httpserver.HttpsServer`
  (JDK `jdk.httpserver` module) + a committed throwaway self-signed PKCS12
  (`app/src/test/resources/https/trik-https-test.p12`, CN=localhost, password
  `changeit`) serve real MJPEG frames over TLS; the client's trust-all path is
  proven end-to-end (`HttpsVideoStreamTest`, mutation-checked red when the
  trust-all is removed).
- **Verify static-analysis rule placement against executable sources before
  configuring.** detekt 1.23.8's `EmptyFunctionBlock` lives under the
  `empty-blocks` ruleset (not `style`/`empty`) with property `ignoreOverridden`
  (not `ignoreOverriddenFunction`) — two wrong guesses cost two failed runs.
  The bundled `default-detekt-config.yml` inside the `detekt-core`/
  `detekt-rules-*` jars is the source of truth (same discipline as verifying
  toolchain names against build files).

### WCAG & accessibility regression tests

- **Contrast and touch targets are code-enforced, not eyeballed.**
  `WcagContrastTest` reads the *live* color resources and asserts WCAG AA
  relative-luminance ratios (≥4.5:1 normal text, ≥3.0:1 large text / UI
  components); `TouchTargetSizeTest` asserts the 48dp minimum on the gear, the
  status pill and the magic-button row. A palette or layout change that drops
  below the thresholds fails the suite.
- **Accessibility announcements are logic-tested via the pure
  `ConnectionAnnouncer`** (state→text mapping, dedup, target gating). The thin
  `announceForAccessibility` call in `ConnectionFeedback` is exercised by
  ordinary tests; Robolectric cannot reliably capture the emitted events via
  `ShadowAccessibilityManager`.
- **Do not rely on `android.R.color/darker_gray`'s value in Robolectric** — it
  resolves differently than on device. Own such colors as app resources
  (`magic_button_fill_default`) so the WCAG test and the drawable always agree.
- **Real TalkBack is not on the aosp emulator images** (not installable in CI
  locally); live-region/announce logic is unit-tested instead.

### Translation sync guard

- `scripts/check_translations.py --sync` (key parity + format-specifier parity
  across `values-*/strings.xml`) runs inside the canonical gate — Android lint
  has no such check. `--back-translate` (MyMemory API) is a one-off semantic
  review aid, never a gate (rate-limited, network-dependent).

## Edge-case audit

Every batch of changes touching `SenderService` or the tests should consider:

- Empty commands (`send("")` must still connect).
- Keepalive boundaries: `MINIMAL_KEEPALIVE` (1000) vs default (5000) vs large
  values (keepalive effectively disabled in tests via `10000000`).
- Target change while connected → disconnect, then reconnect on next send.
- Port/reuse failure modes (bind conflicts, closed listener in `close()`).
- Instrumented: orientation (landscape-only activity), fullscreen/immersive UI,
  settings-driven host/port/video-URI changes.

## Test quality discipline

Tests are code: keep logical SLOC low and re-use what is similar. Two metrics
(rationale + alternatives: `DECISIONS.md` "[2026-08-09] Test logical SLOC
metric"):

- **Logical SLOC = summed per-class `token_count`** from `lizard` (a
  Halstead-N proxy: operators + operands, comments/blanks excluded); the run +
  the trend report live in `scripts/gate.py`. Reported as a **trend** against
  the A0 baseline below; re-measure at the end of each campaign and record the
  result in that campaign's record.
- **Duplication hard gate** — configuration + thresholds live in `.jscpd.json`;
  it runs inside `scripts/gate.py` and the CI build job (invocation: see
  AGENTS.md Commands). Fails on any new duplicated block ≥ the configured
  threshold. jscpd's `paths` config key does not restrict the scan, so the
  source dirs stay positional args in the script; the residual import-header
  clones are language boilerplate, never logic duplication.

> **detekt `TooManyFunctions` is `>=`, not `>`**: a class at exactly the
> threshold still fails ("31 detected, threshold 31" → violation). Keep at
> least one function of headroom; when a class keeps growing thin helpers,
> prefer merging them over bumping the threshold again. Thresholds:
> `app/config/detekt/detekt.yml`.

**What moves the token number:** dedup (shared helpers) — not table-ization.
Data-driven tables buy clearer intent, not fewer tokens; the `cases` literals
are the test. Expect dedup to cut tokens; don't chase the number by adding
rows.

**Shared test helpers live in `RobolectricTestBase`** (the single source of
truth for the 3-SDK `@Config`, `runBounded`, `dialogViews`, and the
reflection/pref helpers `field` / `setField` / `method` / `setPref`). A new
test that needs any of these must inherit them — never re-implement a private
copy (three per-class copies were consolidated in Campaign 32).

### Tests must be able to fail

A test that cannot fail asserts nothing — it only burns time and gives false
confidence (hit 2026-08-11: the `SquareButtonTest` diagonal gesture injected
its DOWN 1px above the pad, so the pad never received any touch, the received
list stayed empty, and `assertPadCommands`' `while (hasNext())` loop ran zero
times — green on every emulator until a bounded await exposed the missing
`up`). The three concrete never-fail modes, all seen in this repo:

- **Tautological assertion** — `assertTrue(button.isPressed || !button.isPressed)`
  (MagicButtonPanelTest) and `assertTrue(sender !== null)` on a non-null
  `lateinit` (SquareTouchPadLayoutTest) are always true. An assertion must have
  a value that can go wrong.
- **Asserting setup, not the effect** — `onSensorChangedAccelerometerShouldProcessWheel`
  asserted `hostAddr != null` (a value set in `setUp`) while the comment
  claimed "a wheel command was sent". Assert the code-under-test's output, not
  a value the fixture already set.
- **Empty-list iteration** — an assertion nested in `while (hasNext())` passes
  vacuously on `[]`. Assert the list is non-empty (or bounded-await the expected
  content) before iterating.

**Guards:** after writing an assertion, break the code under test (comment the
line / invert the condition) and confirm the test goes **red**; if it stays
green it asserts nothing. Write new behavior tests **red-first (TDD)**: a test
you have never seen fail may not be testing anything. The JaCoCo gate proves a
line *executed*, never that an assertion is meaningful — coverage green is not
test green.

**Stateful-table trap:** a table row's expected value must not depend on state
an earlier row set (e.g. a non-numeric wheel step *keeps* the current step, so
after a `"42"` row the expected default became 42). Reset the fixture per row
where a row's outcome depends on prior state (`ui.step = 7` before each case).

A0 baseline (2026-08-09; `main` sources excluded) — the fixed trend anchor:

| File | tokens |
|------|-------:|
| MainActivityTest | 2009 |
| MainWindowTests (androidTest) | 1266 |
| SquareTouchPadLayoutTest | 1021 |
| SenderServiceAdvancedTest | 807 |
| mjpeg/MjpegInputStreamTest | 801 |
| mjpeg/SyntheticMjpegServerTest | 656 |
| SettingsTests (androidTest) | 634 |
| RawSocketHttpStreamTest | 626 |
| MainActivitySettingsControllerTest | 616 |
| mjpeg/SyntheticMjpegServer | 547 |
| mjpeg/MjpegFrameRendererTest | 540 |
| SenderServiceTest | 441 |
| mjpeg/MjpegViewTest | 414 |
| SettingsActivityTest | 357 |
| MagicButtonPanelTest | 331 |
| TouchPadControllerTest | 294 |
| WheelControllerTest | 291 |
| KeepAliveTests (androidTest) | 273 |
| DummyServer (androidTest) | 231 |
| SystemUiControllerTest | 163 |
| FocusAwareActivityTestRule (androidTest) | 159 |
| VideoStreamLoaderTest | 113 |
| SenderViewModelTest | 69 |
| **Total** | **12,659** |

### Measuring and driving coverage (JaCoCo report)

- The report is at
  `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` (note the
  extra `jacocoTestReport/` directory), and it measures only the debug
  unit-test run (`app/build/jacoco/testDebugUnitTest.exec`).
- The XML carries **per-class** `<counter type="BRANCH">`/`LINE` totals AND
  **per-line branch detail**: each `<line>` under `<sourcefile>` has `mb`
  (missed branches) and `cb` (covered branches) attributes. To plan a coverage
  campaign, list the lines with `mb > 0` sorted by `mb` desc — that is the
  exact set of hittable missed branches, class by class. The HTML report adds
  per-method tables.
- **Kotlin-synthetic branches cap the achievable ratio.** `?.`/`?:`/
  `isNullOrBlank` compile to null-check branches that are structurally
  unhittable when the receiver can never be null (a view always populated by
  `findViewById`, a host address a settings `register()` always defaults, a
  thread created in `init()`). Measure the delta per targeted class and keep
  a ratchet with headroom over the gate — don't chase 100% or exclude more
  logic.
- **Confirm the needle moved** after each added test (see "Robolectric shadow
  traps") before moving to the next target.
- PowerShell: parse with `[xml](Get-Content -Raw …)`; `XmlDocument.Load()`
  throws on the `report.dtd` reference.

### Compiler warnings as errors (K2 `-Wextra`)

- `app/build.gradle` sets `extraWarnings` **and** `allWarningsAsErrors` on the
  `kotlin { compilerOptions {} }` extension (AGP 9 built-in Kotlin wires the real
  KGP `KotlinAndroidProjectExtension`; AGP 9.2.1 bundles KGP 2.2.10). This covers
  **every** Kotlin compilation in the module — main, unit test, androidTest — so
  a new warning (deprecation, redundant code, unreachable code, `-Wextra` extra
  checks) fails the build. Enablement history: DECISIONS.md "K2 -Wextra
  warnings-as-errors".
- The Kotlin compiler does **not** do boolean algebra, so it cannot flag
  tautologies like `X || !X` as function arguments — that class is covered by the
  "Tests must be able to fail" discipline above, not by compiler warnings.
- **Accepted suppressions** (each carries a rationale comment in code; keep this
  registry in sync):
  - `@Suppress("DEPRECATION")`:
    - The `getParcelableExtra(String)` calls in `ReportSharerTest` (report
      stream and ZIP-forward tests) — the typed replacement overload is API
      33+, and these tests run at minSdk 23 (Robolectric `[23]`).
    - `MainActivityTest` `updateConfiguration(config, metrics)` (2-arg) — the
      1-arg form was removed in SDK 36.
    - `MainActivityTest` `dispatchKeyEventWithUnknownActionShouldFallThrough` —
      `ACTION_MULTIPLE` is deprecated but is the only KeyEvent action that is
      neither DOWN nor UP, which is exactly what the test needs.
    - `ConnectionFeedbackTest` `TYPE_ANNOUNCEMENT` — the only way to recognize an
      announcement in a sent-event list.
    - `FocusAwareActivityTestRule` (file-level) — deliberately extends the
      deprecated `ActivityTestRule` to add focus-wait/back-dismiss logic that
      `ActivityScenarioRule` does not expose.
    - `ShareReceiverActivity` `streamUris()` — the typed `getParcelableExtra`
      overloads are API 33+; at minSdk 23 (and in Robolectric) only the legacy
      single-arg forms exist for reading `EXTRA_STREAM`.
  - `@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")`:
    - `DummyServer`'s monitor `Object()` — `kotlin.Any` has no `wait`/`notifyAll`
      monitor methods.

## Known gaps

- Coverage is enforced as a JaCoCo ratchet gate — the current thresholds live
  in `app/build.gradle` (`jacocoTestCoverageVerification`); `jacocoTestReport`
  always produces the full report. MjpegView's render-thread plumbing
  (`MjpegView$MjpegRenderThread`/`MjpegViewThread`) is excluded with a
  recorded rationale (untestable thread lifecycle; the render logic lives in
  the covered `MjpegFrameRenderer`); `MediaPlayerVideoPlayer*` (the RTSP
  adapter) is excluded for the same reason — its `MediaPlayer` + `TextureView`
  surface lifecycle needs a hardware-accelerated Surface + MediaCodec and
  Robolectric has no `ShadowTextureView` surface simulation (the class still
  carries unit tests for its null/lifecycle logic). Also excluded are
  Kotlin-inline synthetics (`**/*$special$$inlined$*.class` — the
  `by viewModels()` delegate boilerplate). Rationales: MEMORY "Coverage"
  design decisions.
- **Video URI flows as an opaque `String`** (since Campaign 32): a
  `java.net.URL` cannot parse `rtsp://`, so `setVideoUrl`/`VideoPlayer.play`
  take the raw string and each player validates at open time. Tests must pass
  plain strings (`"rtsp://…"`, `"http://…"`) — never `URL(...)` — and use a
  live `SyntheticMjpegServer` to cover the MJPEG open/play success path.
- Instrumented tests exercise only what runs on the emulator; real TRIK robot
  interaction is never in CI.
- Espresso tests drive the settings UI through extracted helpers
  (`openSettings()`/`editPreference()`) with no `Thread.sleep` — Espresso's
  `onView(...).perform()` idles until views are shown. Keepalive negative
  assertions use `DummyServer.anyMessageWithin()` bounded windows, never
  `Thread.sleep`.
- `MainActivity` forces landscape + immersive; instrumented tests run against
  that configuration only.
- The aosp API-36 emulator images cannot switch the UI language: `cmd locale set-system-locale` and `cmd app locale` are missing, and `settings put system system_locales` does not propagate to activities without a reboot — so
  a locale-rendering screenshot is not capturable there. Locale parity is
  covered by `scripts/check_translations.py --sync` + the one-off
  `--back-translate` review instead.

## Scenario→test mapping (DESIGN.md "Scenarios & use-cases")

Each scenario in the DESIGN.md contract is pinned by a test (or a documented,
testable-through-adjacent test where a real robot/device is required):

| Scenario | Proof |
|----------|-------|
| S1 default WAP, robot alive | instrumented smoke (`MainWindowTests`), e2e `MjpegServerTest` decode/play |
| S2 WAP out of range → tap reconnect | `VideoRetryControllerTest` edge tests + `MainActivityTest` `connection*` suite (control reconnect edge) |
| S3 same-host video | `shouldReloadVideoWithUrlShouldBeControlAgnostic` gate test (URL + not-playing, control-agnostic) |
| S4 video from a different IP | same gate test — the control-agnostic gate reloads regardless of control (video independence is one code path) |
| S5 video-only (empty host) | `shouldReloadVideoWithUrlShouldBeControlAgnostic` + `MainActivityTest` empty-host UI tests (pads/buttons/pill hidden) |
| S6 two controllers / two robots | same gate rule as S4 (video independence is one code path) |
| S7 same-host watch-only | `deadStreamShouldSelfHealWithControlPermanentlyDisconnected` e2e (dead stream reloads even with control `Disconnected`) |
| S8 hostname vs IP mismatch | covered by the control-agnostic gate path (no string coupling to gate video) |
| S9 robot reboot | `VideoRetryControllerTest` tick tests + the control-`Connected` edge reload |
| S10 half-open control | `VideoRetryControllerTest` (reload allowed on `!isPlaying` alone) |
| S11 re-host, old video URL | same control-agnostic gate tests (an old URL keeps retrying) |
| S12 multiple gamepads | no shared state: each `SenderService`/`VideoRetryController` is per-activity (unit tests run independent instances) |
| S13 WAP ↔ cellular routing | `SocketBinderTest` (bind/fallback seams) + `RawSocketHttpStreamTest` (video socket binds) + `WifiConnectionOpenerTest` + `HttpsVideoStreamTest` (https routed over the Wi-Fi network, trust-all TLS) |
| S14 control off, video open | `deadStreamShouldSelfHealWithControlPermanentlyDisconnected` e2e (video recovers without control `Connected`) |

The video-retry scenarios were consolidated in 2026-08-18 (Option B): the gate
is control-agnostic, so S3/S4/S5/S6/S7/S8/S11/S14 all reduce to one code path
("URL configured ∧ not playing → reload") pinned by the
`shouldReloadVideoWithUrlShouldBeControlAgnostic` gate test + the e2e
`deadStreamShouldSelfHealWithControlPermanentlyDisconnected` test.
