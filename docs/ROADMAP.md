# ROADMAP — trik-gamepad improvement plan (post pure-Kotlin migration)

<!-- encoding: utf-8 -->

Scope: maintainability and quality improvements after the pure-Kotlin
migration. Baseline (verified 2026-08-07): `app/` is 0 `.java`; 170 unit
tests; coverage gate 90% line / 70% branch (measured 96.9% / 71.2%); CI
build gate green; instrumented CI is best-effort (swiftshader
`Failed to find ColorBuffer` hangs — not focus, which is fixed).

Each item: commit-per-concern, gated by the full local suite (test lint
detekt spotbugsDebug jacocoTestReport jacocoTestCoverageVerification
spotlessCheck) before each push, `git status --short` clean before push.

## Phase 1 — Instrumented CI without macOS

Explicit decision: **no macOS/GPU runner.** ✅ **DONE — experiment 2 won.**
`target: aosp_atd` + `-gpu swiftshader_indirect` + the immersive pre-empt +
`FocusAwareActivityTestRule` passes all 9 instrumented tests (first fully-green
run `31232406163`, 2026-08-08). The old "aosp_atd never grants focus (8/8)"
finding predated the pre-empt/focus-wait fixes. The last red run was a CI
script trap: `android-emulator-runner` runs each `script:` line as its own
`sh -c`, so the multi-line `if/fi` retry never parsed — fixed as a single-line
`cmd || { ...; }` (AGENTS.md rule updated). No remaining experiments needed.

## Phase 2 — Legacy API + biggest code smell (local, low risk)

- **D.** ✅ Migrate `android.preference.PreferenceManager` →
  `androidx.preference.PreferenceManager` (5 files: main + tests/androidTest;
  import-only, verified same default file via javap). Landed `ac0a406`.
- **E.** ✅ Extract `MainActivity`'s ~150-line pref listener into a
  `MainActivitySettingsController`. Landed `962f4d9` + `67e4c22`.
- **F.** ✅ Extract the sensor-wheel math into a pure `WheelController`.
  Landed `bbd0bbb` + `67e4c22` (WheelControllerTest, 100% JaCoCo).
- **J (partial).** ✅ Tighten detekt: `LongMethod` 200→150,
  `CyclomaticComplexMethod` 25→20; `NestedBlockDepth` stays 5 (MJPEG
  byte-parsing loops; rationale in detekt.yml). Landed `5b7dcdf`.

## Phase 3 — SenderService statics → constructor injection

✅ **DONE** (`9d055fd` + `a59d688`). Statics (`mExecutor`, `keepaliveTimeout`,
`mConnectTask`) and `java.util.Timer` → constructor-injected executor +
daemon `ScheduledExecutorService` keepalive (defaults preserved). Tests inject
via `SenderService(mExecutor)`; the `mConnectTask` reflection and the
"Shared state (known hazard)" docs are gone.

## Phase 4 — Test quality (compounds the CI issue)

- **I.** ✅ `SettingsTests` de-slept: `openSettings()`/`editPreference()`
  helpers, zero `Thread.sleep` (585 lines removed); KeepAliveTests use a
  bounded negative-await (`DummyServer.anyMessageWithin`). Landed `e8cb3d8` +
  `ab18bdd`; verified 9/9 on both Atd_API36 and Swiftshader_API36.

## Phase 5 — Polish + close-out

- **H.** ✅ Rename `com.demo.mjpeg` → `com.trikset.gamepad.mjpeg` (git mv +
  imports + layout + spotbugs `onlyAnalyze`). Landed `e56a9af`.
- ✅ Final retrospective → AGENTS.md / MEMORY.md / TESTING.md / `.PLAN.md`;
  last-known-good CI run id updated (`31232406163`, first fully-green run).
