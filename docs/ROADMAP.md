# ROADMAP — trik-gamepad improvement plan (post pure-Kotlin migration)

<!-- encoding: utf-8 -->

Scope: maintainability and quality improvements after the pure-Kotlin
migration. Baseline (verified 2026-08-08): `app/` is 0 `.java`; coverage gate
95% line / 80% branch (measured 97.3% / 81.9%); CI **fully green** (build + all 9
instrumented on `aosp_atd`).

Each item: commit-per-concern, gated by the full local suite (test lint
detekt spotbugsDebug jacocoTestReport jacocoTestCoverageVerification
spotlessCheck) before each push, `git status --short` clean before push.

## Campaign 2 (post-ROADMAP — in progress 2026-08-08)

Scope set by user decision (2026-08-08). **Strategy: coverage-first** — increase
test coverage before refactoring, maintain it through refactoring (B4 is the one
granted exception: it reduces test-writing complexity). Revised order
B2 → B4 → C → B3 → A → D → E → F. Full detail + execution record:
`.PLAN.md` "Campaign 2".

- **B1** ✅ `MagicButtonPanel` extracted (buttons + haptic; direct tests). `ee4a91b`.
- **B2** ✅ `SystemUiController` extracted (immersive toggle + auto-hide;
  `lazy`-wired in MainActivity; direct tests). `3ac7c7e`.
- **B4** ✅ `TouchPadController` pure touch-math (owns prevX/prevY; 100%
  JaCoCo). `53c55f6`.
- **C** ✅ Coverage → **95 line / 80 branch floor** (measured **97.3% / 81.9%**,
  ratcheted `6f95589`); targets hit: MainActivity lifecycle null-branches,
  MainActivitySettingsController (11/12), SettingsFragment summary paths,
  SquareTouchPadLayout, SenderService reconnect, MjpegFrameRenderer bitmap
  reuse. Remaining gaps (MainActivity onCreate/onDestroy view-null paths,
  MjpegInputStream parser edges, SenderService `Log.isLoggable` branches) are
  not cleanly reachable under Robolectric.
- **B3** ✅ SenderService inner classes (`ConnectRunnable`, `KeepAliveTimer`)
  → own files. `13a6ad8`.
- **A** ✅ Concurrency guard landed (`8fe0d03`); aosp_atd flake probe **3/3
  green** on the final head — no batch-split needed.
- **D** ✅ Cache write-back + `org.gradle.parallel=true` landed (`55e32ba`);
  build gate measured **4m27s → 1m05s** (≈4×). Instrumented 3m10-3m33s.
- **E** ✅ AGP-9 PREP (settings.gradle + buildscript → plugins DSL) then the
  **actual AGP 9.3.1 / Gradle 9.5.0 migration landed** (built-in Kotlin, config-cache
  re-enabled; `78aace4`). See `.PLAN.md` / MEMORY "AGP 9.3.1 / Gradle 9.5.0 migration LANDED".
- **F** ✅ Cleanup (emulator snapshot discrepancy **closed as cosmetic**;
  DummyServer note already in TESTING.md) + MEMORY retrospective added.
  **Campaign 2 complete.**

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
