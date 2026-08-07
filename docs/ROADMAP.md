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

Explicit decision: **no macOS/GPU runner.** Run each experiment as one
bounded CI run; triage by failure class (boot vs render vs test);
document in MEMORY.md; stop as soon as one works.

1. Research the exact `Failed to find ColorBuffer: <id>` root cause and
   known swiftshader workarounds (emulator flags, GLES toggles, emulator
   version pinning) before spending runs.
1. Retry `aosp_atd` on CI — the old "never grants focus (8/8)" finding
   predates the pre-empt fix; the lighter image reduces system rendering
   pressure.
1. Lower rendering load: smaller device profile / reduced `hw.lcd`
   resolution.
1. `-gpu guest` (guest-side software rendering — a different code path
   than the host-side `swiftshader_indirect` buffer cache).
1. Split the instrumented suite into stable batches (KeepAlive
   standalone; view tests separately) with per-batch retry, so one
   batch's hang cannot sink the run.
1. Fallback (acceptable, documented): keep instrumented best-effort; the
   build gate is the authoritative gate.

## Phase 2 — Legacy API + biggest code smell (local, low risk)

- **D.** ✅ Migrate `android.preference.PreferenceManager` →
  `androidx.preference.PreferenceManager` (5 files: main + tests/androidTest;
  import-only, verified same default file via javap). Landed `ac0a406`.
- **E.** 🔶 Extract `MainActivity`'s ~150-line pref listener into a
  `MainActivitySettingsController` (or focused private methods). Attacks
  the `LongMethod=200` / `CyclomaticComplexMethod=25` detekt relaxations
  and enables non-reflective tests. (Working tree in flight at session close
  2026-08-08 — see .PLAN.md.)
- **F.** Extract the sensor-wheel math into a pure `WheelController`
  (`processSensor` is private + reflection-invoked today; a pure function
  gets direct tests and drops more reflection).
- **J (partial).** Tighten detekt after E/F: `LongMethod` 200→150,
  `CyclomaticComplexMethod` 25→20, `NestedBlockDepth` 5→4 if the code
  allows.

## Phase 3 — SenderService statics → constructor injection

Replace the `@JvmField` statics (`mExecutor`, `keepaliveTimeout`,
`mConnectTask`) and the `java.util.Timer` keepalive with constructor
injection (executor + keepalive period, defaults preserved). Update the
three test classes (SenderServiceTest, SenderServiceAdvancedTest,
SquareTouchPadLayoutTest) to inject instead of reflect-reset; drop the
`mConnectTask` reflection. This removes the most-documented hazard
(MEMORY.md/TESTING.md dedicate sections to it). Verify: full suite ×2 +
docs updated.

## Phase 4 — Test quality (compounds the CI issue)

- **I.** `SettingsTests`: replace ~35 `Thread.sleep(3000)` with
  `waitForIdleSync`/`retryUntil` helpers and extract
  `openSettings()`/`setPrefValue()`/`confirm()` helpers. Cuts ~900
  lines, reduces flakiness and render load (also helps Phase 1).

## Phase 5 — Polish + close-out

- **H.** Rename `com.demo.mjpeg` → `com.trikset.gamepad.mjpeg` (pure
  rename; first-party now). Keep `docs/architecture.md` in sync (it documents
  the current module map / TCP protocol / MJPEG pipeline / test layering).
- Final retrospective → AGENTS.md / MEMORY.md / TESTING.md / `.PLAN.md`;
  update the last-known-good CI run id.
