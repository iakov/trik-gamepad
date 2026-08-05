# Testing

<!-- encoding: utf-8 -->

Scope: Test strategy, how to run tests, mocking/synchronization patterns, and
known gaps for `as/`.
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
  CI runs them on Firebase Test Lab; locally they need a running emulator.

## Running tests

```sh
cd as
./gradlew test                      # all unit-test variants (debug/release/releaseDebug)
./gradlew testDebugUnitTest --tests "com.trikset.gamepad.SenderServiceTest.senderServiceShouldSendSingleCommandCorrectly"   # single test
./gradlew connectedDebugAndroidTest # instrumented; needs emulator/device
```

Local instrumented run:

1. Verify acceleration: `emulator -accel-check` (needs AEHD → returns `0`).
1. Boot an AVD: `emulator -avd Simple_Phone_API35 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect`.
1. Wait for `adb shell getprop sys.boot_completed` → `1`.
1. `./gradlew connectedDebugAndroidTest`.

## Diagnostic discipline

- **Baseline first**: unexpected errors? Stash changes, run the same command.
  If it still fails → pre-existing, don't chase ghosts.
- **Batch before re-run**: found one failure? Grep for siblings and fix all
  before re-running — each re-run costs the full suite.
- **Full output while debugging**, quiet mode only for the final green check.

## Patterns

### Two DummyServers — do not confuse them

- `as/src/test/.../SenderServiceTest` defines its **own inner `DummyServer`**.
  It binds an **ephemeral port** (`ServerSocket(0)`), exposes `getPort()`,
  `awaitConnection()`, `awaitCommands()` (5 s timeouts), and closes the
  listening socket in `close()`.
- `as/src/androidTest/.../DummyServer.java` is a separate class binding
  `localhost:12345`, used by the instrumented tests.
- **Never reintroduce fixed ports in the unit test**: `./gradlew test` runs
  three variants in parallel JVMs; fixed ports caused `BindException` cascades
  (see MEMORY.md Testing).

### Why awaits are required

The server thread accepts and reads asynchronously. Asserting immediately after
`mExecutor.runAll()` + `shadowOf(getMainLooper()).idle()` races the server.
Always await the latch (`awaitConnection()` / `awaitCommands()`) before
asserting connected/command state.

### Robolectric determinism

`@LooperMode(PAUSED)` + a `PausedExecutorService` injected via
`client.setExecutor(...)` make background task execution deterministic:
`mExecutor.runAll()` runs queued AsyncTask work; then idle the main looper to
run `onPostExecute`. Do not rely on real threads for the `SenderService`
executor.

## Edge-case audit

Every batch of changes touching `SenderService` or the tests should consider:

- Empty commands (`send("")` must still connect).
- Keepalive boundaries: `MINIMAL_KEEPALIVE` (1000) vs default (5000) vs large
  values (keepalive effectively disabled in tests via `10000000`).
- Target change while connected → disconnect, then reconnect on next send.
- Port/reuse failure modes (bind conflicts, closed listener in `close()`).
- Instrumented: orientation (landscape-only activity), fullscreen/immersive UI,
  settings-driven host/port/video-URI changes.

## Known gaps

- No coverage gate yet (JaCoCo planned but postponed).
- Instrumented tests exercise only what runs on the emulator; real TRIK robot
  interaction is never in CI.
- Espresso tests note "idling resources would be the recommended way" — the
  tests currently rely on `ActivityTestRule` + sleeps; be aware of flakiness
  potential on slow emulators.
- `MainActivity` forces landscape + fullscreen; instrumented tests run against
  that configuration only.
