# ROADMAP — trik-gamepad improvement plan (post pure-Kotlin migration)

<!-- encoding: utf-8 -->

Scope: maintainability and quality improvements after the pure-Kotlin
migration. Baseline (verified 2026-08-08): `app/` is 0 `.java`; coverage gate
92% line / 71% branch (measured 93% / 72%); CI **fully green** (build + all 9
instrumented on `aosp_atd`).

Each item: commit-per-concern, gated by the full local suite (test lint
detekt spotbugsDebug jacocoTestReport jacocoTestCoverageVerification
spotlessCheck) before each push, `git status --short` clean before push.

## Campaign 2 (post-ROADMAP — in progress 2026-08-08)

Scope set by user decision. Execution order B/C → A/D → E. Full detail +
in-flight crash-safety state: `.PLAN.md` "Campaign 2".

- **B1** ✅ `MagicButtonPanel` extracted (buttons + haptic; direct tests). `ee4a91b`.
- **B2** 🔶 `SystemUiController` extracted (immersive toggle + auto-hide);
  in flight, spotless-pending.
- **B3** ⬜ SenderService inner classes → separate files.
- **B4** ⬜ SquareTouchPadLayout pure touch-math.
- **C** ⬜ Coverage → ~95 line / ~80 branch (targets: MjpegFrameRendererKt 0%,
  VideoStreamLoader 88%, MjpegInputStream 91.5%, SenderService 93.1%,
  MainActivitySettingsController 96.4%); ratchet gate as measured.
- **A** ⬜ CI hardening (concurrency guard; aosp_atd flake-rate probe ×3;
  batch-split if flaky).
- **D** ⬜ CI cache tuning (measure; parallel/jvmargs/CC-strict; one CI run each).
- **E** ⬜ AGP9/Gradle9 (research → settings.gradle restructure → plugins DSL →
  upgrade; full gate each step).
- **F** ⬜ Cleanup (emulator snapshot discrepancy, DummyServer note) +
  final retrospective.

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
