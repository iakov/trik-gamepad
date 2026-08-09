# Testing

<!-- encoding: utf-8 -->

Scope: Test strategy, how to run tests, mocking/synchronization patterns, and
known gaps for the canonical `app/` layout.
Aim: Document how tests work, what is covered, and hard-won lessons about
Robolectric timing, emulator prerequisites, and the two `DummyServer` classes.
Structure: Overview → Running tests → Diagnostic discipline → Patterns →
Edge-case audit → Known gaps.

## Overview

Two layers:

- **Robolectric unit tests** (JVM, no device): `SenderServiceTest` runs under
  three SDK configs per build type and covers the TCP `SenderService`.
- **Espresso instrumented tests** (`androidTest`): `KeepAliveTests`,
  `MainWindowTests`, `SettingsTests`, run with AndroidX Test Orchestrator.
  CI runs them on an API 36 emulator via GitHub Actions; locally they need a
  running emulator.

## Running tests

```sh
./gradlew test                      # all unit-test variants (debug/release/releaseDebug)
./gradlew testDebugUnitTest --tests "com.trikset.gamepad.SenderServiceTest.senderServiceShouldSendSingleCommandCorrectly"   # single test
./gradlew connectedDebugAndroidTest # instrumented; needs emulator/device
```

> **Known local flake: Robolectric download lock.** `./gradlew test` runs the
> three variants in parallel JVMs, which race for the user-level
> `~/.robolectric-download-lock` file. One variant can fail its whole class
> with `Couldn't create lock file C:\Users\...\.robolectric-download-lock`
> (`IllegalStateException` at `MavenDependencyResolver`). Transient infra —
> re-run the failed variant (e.g. `testReleaseUnitTest`) and it passes. Never
> treat this as a code regression.

Local instrumented run:

1. Verify acceleration: `emulator -accel-check` (needs AEHD → returns `0`).
1. Boot the **`aosp_atd` image** AVD `Atd_API36` — the lightweight, headless,
   CI-oriented image (adopted after it passed 9/9 locally with `-gpu host`;
   lighter/faster boot than the `default` image; rationale + alternatives:
   `DECISIONS.md` "Local instrumented: adopt aosp_atd"). Correct launch (~instant
   via snapshot, suite ~5 min):
   `emulator -avd Atd_API36 -no-window -no-audio -no-boot-anim -gpu host`
   (drop `-no-window` to see the UI; keep snapshots enabled for fast reboots).
1. Wait for `adb shell getprop sys.boot_completed` → `1`.
1. **Disable the immersive-mode confirmation overlay** (required on API 35+):
   the gamepad runs immersive (system bars hidden), and the first time it enters
   immersive mode the system pops an `ImmersiveModeConfirmation` window that
   keeps focus away from the app. Espresso then fails every interaction with
   `RootViewWithoutFocusException`. Pre-empt it once per AVD:
   `adb shell settings put secure immersive_mode_confirmations confirmed`.
1. `./gradlew connectedDebugAndroidTest`.

> **Do NOT use `-gpu swiftshader_indirect` locally.** Verified: with the
> software GPU the app window never receives focus and the same suite fails
> 8/8 with `RootViewWithoutFocusException` — the exact failure CI saw (decision
> and verification in `DECISIONS.md` "Local instrumented: adopt aosp_atd").
> Local runs must use `-gpu host`.

> **CI focus flake — root-caused and fixed (2026-08-07).** The
> `RootViewWithoutFocusException` storm on CI was traced to the immersive
> pre-empt racing the settings provider: `sys.boot_completed` reports `1`
> while the provider is still starting, so a single `settings put` during a
> slow headless boot was silently lost, and the `ImmersiveModeConfirmation`
> overlay then stole focus for the whole suite. The ci.yml pre-empt now
> **retries the settings write until `settings get` confirms it** (up to 60 s),
> and `FocusAwareActivityTestRule` waits (and bounded-BACK-dismisses) for
> window focus, skipping the wait for non-view tests. Validated on CI: the
> first ~5 tests pass and no focus assertions fire (previously 0/9).
> **Remaining CI instrumented instability:** swiftshader rendering errors
> (`Failed to find ColorBuffer`) can still hang Espresso interactions under
> load on 2-4-core runners — a software-GPU resource issue, not a code
> regression. Treat those (not focus) as the known CI flake.

## Diagnostic discipline

- **Baseline first**: unexpected errors? Stash changes, run the same command.
  If it still fails → pre-existing, don't chase ghosts.
- **Batch before re-run**: found one failure? Grep for siblings and fix all
  before re-running — each re-run costs the full suite.
- **Full output while debugging**, quiet mode only for the final green check.

## Patterns

### Test TCP servers — ephemeral unit server vs fixed-port instrumented server

- `app/src/test/.../TestTcpServer.kt` is the **shared unit** server (Campaign 6
  B1, merging the old inner `DummyServer` + `ReadUntilStopServer`). It binds an
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

### Synthetic MJPEG server (Campaign 4) — the third test server

`app/src/test/.../mjpeg/SyntheticMjpegServer.kt` is a real HTTP
`multipart/x-mixed-replace` MJPEG server (ephemeral port) that streams seeded
JPEG frames to the app's real `VideoStreamLoader`/`MjpegInputStream` decode path
and can **drop the connection after N frames** to exercise R12 reconnect.
Companion `SyntheticMjpegServerTest.kt`:

- **Must run under `@GraphicsMode(GraphicsMode.Mode.NATIVE)`** — default
  Robolectric graphics return fake bitmaps, making decode correctness/perf
  assertions meaningless.
- **Color-asserting tests must pin `@Config(sdk=[Config.TARGET_SDK])`**: on
  API 23, native `BitmapFactory` decodes pixels near-black (red → r=1,g=0,b=0) —
  byte-equality checks still prove correctness on the full SDK triple.
- **Pace the server (~50 ms) and keep seeded JPEGs comparable in size**: the
  parser's `available() < 2*contentLength` gate drops small frames first when
  the client lags.

### Why awaits are required

The server thread accepts and reads asynchronously. Asserting immediately after
`mExecutor.runAll()` + `shadowOf(getMainLooper()).idle()` races the server.
Always await the latch (`awaitConnection()` / `awaitCommands()`) before
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

### Robolectric determinism

`@LooperMode(PAUSED)` + a `PausedExecutorService` injected via the
`SenderService(mExecutor)` constructor make background task execution
deterministic: `mExecutor.runAll()` runs queued AsyncTask work; then idle the
main looper to run `onPostExecute`. Do not rely on real threads for the
`SenderService` executor.

### Robolectric shadow traps

- **Sensor tests need a `SensorEvent`, not a `Sensor`.** To feed the
  accelerometer path, build the event via
  `ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)` — the
  2-arg form. The 1-arg `createSensorEvent(3)` defaults the sensor to
  **TYPE_GRAVITY (9)**, so `onSensorChanged` never matches the accelerometer
  branch and the wheel path silently stays uncovered even though the test
  passes. Set `event.values`, and stub the sensor lookup. Trying
  `shadowOf(Class<Sensor>)` or the wrong constructor is a compile error
  (`no suitable method found for shadowOf`) that the sensor-test saga hit
  twice (see MEMORY.md "Coverage drive to 85%": MainActivityTest).
- **After adding a coverage test, confirm it moved the needle.** A passing
  test can still cover nothing (the sensor wheel path was "covered" by a
  passing test that used the wrong sensor type). Diff the JaCoCo per-class
  branch numbers for the target class after adding the test.
- **Covering null-guard branches on layout-populated fields needs explicit
  nulling.** `MainActivity.onPause`/`onResume` guard `mVideo`/`mSensorManager`
  with null checks, but the activity layout always provides those views in
  Robolectric — so a "no-op" test that just calls `onPause()` covers nothing
  (the JaCoCo needle did not move). To cover the null branch, explicitly
  `setField(activity, "mVideo", null)` (and `mSensorManager`) via reflection
  **before** invoking the method.
- **3 identical failures → stop and read the shadow source.** If the *same*
  test fails identically N≥3 consecutive runs (same exception, same line, log
  sizes near-identical), stop retrying and read the shadow's real API from the
  Robolectric jar/source instead of tweaking-and-rerunning. The sensor saga
  burned ~15 local runs this way before the `createSensorEvent(3)` fix.

## Edge-case audit

Every batch of changes touching `SenderService` or the tests should consider:

- Empty commands (`send("")` must still connect).
- Keepalive boundaries: `MINIMAL_KEEPALIVE` (1000) vs default (5000) vs large
  values (keepalive effectively disabled in tests via `10000000`).
- Target change while connected → disconnect, then reconnect on next send.
- Port/reuse failure modes (bind conflicts, closed listener in `close()`).
- Instrumented: orientation (landscape-only activity), fullscreen/immersive UI,
  settings-driven host/port/video-URI changes.

## Test quality metrics (Campaign 6)

Tests are code: keep logical SLOC low and re-use what is similar. Two metrics
(Campaign 6; rationale + alternatives: `DECISIONS.md` "[2026-08-09] Test logical
SLOC metric"):

- **Logical SLOC = summed per-class `token_count`** from
  `lizard -l kotlin app/src/test app/src/androidTest` (a Halstead-N proxy:
  operators + operands, comments/blanks excluded). Reported as a **trend**
  against the A0 baseline below; re-measured at the end of each campaign.
- **Duplication hard gate**: `npx jscpd app/src/test app/src/androidTest --config .jscpd.json` (min-tokens 50, threshold 0) — fails the gate on any
  new duplicated block ≥ 50 tokens. jscpd's `paths` config key does not
  restrict the scan, so the two source dirs are always positional args.
  Import lines are excluded (`ignorePattern: ["import.*"]`); the residual
  import-header clones (52–76 tokens) are language boilerplate, not logic
  duplication. Wired into `scripts/gate.ps1` + the CI build job (Campaign 6 D1).

A0 baseline (2026-08-09; `main` sources excluded):

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

jscpd baseline: 11 clones, 754 duplicated tokens (3.36%) at min-tokens 50 —
dominated by the 17-class `@Config` triple, the two nested TCP test servers,
and the androidTest tap/`initNetworkSettings` duplication (Campaign 6 B1/B4/B8).

## Known gaps

- Coverage gate is a JaCoCo ratchet at **95% line / 80% branch**
  (`jacocoTestCoverageVerification`, raised from 85/60 through the Phase 3
  post-migration coverage push, then to 92/71 after the ROADMAP Phase 2-E/2-F/3
  extractions, then to 95/80 after Campaign 2's coverage push);
  `jacocoTestReport` always produces the full
  report. Measured 97.3% line / 81.9% branch. MjpegView's render-thread
  plumbing (`MjpegView$MjpegRenderThread`/`MjpegViewThread`) is excluded from
  the gate with a recorded rationale (untestable thread lifecycle; the render
  logic lives in the covered `MjpegFrameRenderer`).
- Instrumented tests exercise only what runs on the emulator; real TRIK robot
  interaction is never in CI.
- Espresso tests drive the settings UI through extracted helpers
  (`openSettings()`/`editPreference()`) with no `Thread.sleep` — Espresso's
  `onView(...).perform()` idles until views are shown (de-slept in Phase 4-I;
  previously the suite was ~2 minutes of fixed sleeps). Keepalive negative
  assertions use `DummyServer.anyMessageWithin()` bounded windows, never
  `Thread.sleep`.
- `MainActivity` forces landscape + immersive; instrumented tests run against
  that configuration only.
