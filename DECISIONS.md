# DECISIONS.md — trik-gamepad decision log

<!-- encoding: utf-8 -->

Scope: every "what problem arose → what alternatives were considered → why this
solution was chosen → what is out of scope" decision made in this repo.
AGENTS.md rules and other docs point here for the rationale behind a decision.
Structure: Index → decisions grouped by area → dated entries, newest first.

This file holds *decisions only*. Architecture facts and quirks live in
`docs/architecture.md`; project facts, CI details and retrospectives live in
`MEMORY.md`; test strategy lives in `TESTING.md`.

Each note follows the same shape:

- **Type:** `design-clarifying` (restates/refines what the product does — the
  DESIGN.md scenario contract; changes only with the design) or
  `problem-avoiding` (prevents a specific pitfall; may become obsolete when the
  problem disappears or the design changes). Mixed entries declare a primary
  type and a `Part:` note for the secondary one.
- **Problem:** what arose / what needed deciding.
- **Alternatives considered:** the other options (and what was rejected).
- **Chosen solution:** what was decided.
- **Why:** the rationale (measured/observed where possible).
- **Out of scope / consequences:** what was explicitly not done, and what the
  decision affected.

**Design sits above decisions.** The product truth (features, use-cases,
scenarios) lives in `DESIGN.md` "Scenarios & use-cases"; this log holds only
the "why this choice" records. No decision may violate a scenario — a decision
that does is a design regression and the design is re-discussed interactively
(never silently in auto mode). Decisions cite scenario IDs (S1–S14) where they
touch one.

## Index

| Area | Covers | Newest decision |
|------|--------|-----------------|
| Build & toolchain | AGP/Gradle, config-cache, versioning, keystore, lint baseline, coverage gate, cross-platform dev tooling | [2026-08-15] Idiomatic Kotlin pass (Java→Kotlin leftovers) |
| Testing | Robolectric determinism, emulator prerequisites, coverage strategy | [2026-08-14] CC0 test images replace in-memory JPEG fixtures + theme screenshot test |
| CI & emulator | aosp_atd image, focus pre-empt, no-macOS runner, publish job | [2026-08-08] Phase 1 experiment 2: aosp_atd PASSES |
| Architecture | MJPEG reconnect, NSC scoping, raw-socket client, ViewModel, bounded retry, video-only mode, diagnostics & crash reporting, GPU-backed MJPEG render | [2026-08-19] https video binds to the Wi-Fi network (Network.openConnection + trust-all TLS) |
| Workflows | fork-only, releases | [2026-08-05] Fork-only workflow (no upstream PRs) |
| Process | docs culture, auto-mode contract, operational rules, plan-file design | [2026-08-15] Docs-discipline rules (scoped storage, per-doc drift audit, plan-trim-after-push) |
| UX & accessibility & i18n | design conventions, a11y, WCAG, localization, theme, HUD error pill, inset-aware HUD, magic-button glyph centering, haptics | [2026-08-18] Haptic schema: generic legacy constants, strong pulses, no VIBRATE |
| Repo hygiene | device identifiers never enter repo content, fork-only | [2026-08-20] Device-identifier scrub on push-prep |
| Tooling & process | timeout-bound commands, process-tree kill, host adb shim, dependency drops, chip extraction, emulator launch | [2026-08-17] Emulator launch: run_bounded-wrapped detached Start-Process |

______________________________________________________________________

## Build & toolchain

### [2026-08-13] AGP 9.2.1 — Android Studio 3-release compatibility floor

- **Type:** problem-avoiding (keeps the toolchain openable; prevents a Studio-support regression).

- **Problem:** the project ran AGP 9.3.1 (Gradle 9.5.0), which was only
  supported by the newest Android Studio (Quail 2 | 2026.1.2, AGP 7.1–9.3).
  The two previous releases (Quail 1 | 2026.1.1 and Panda 4 | 2025.3.4) cap
  AGP at 9.2, so a contributor on either Studio could not open the project.

- **Alternatives considered:** stay on AGP 9.3.1 (narrowest Studio support);
  drop to AGP 9.2.x (supported by all three latest Studio releases); drop
  lower (loses nothing for Studio support but forfeits 9.x fixes).

- **Chosen solution:** pin **AGP 9.2.1** (`gradle/libs.versions.toml` +
  `settings.gradle`), keeping Gradle 9.5.0 (AGP 9.2 minimum is 9.4.1).
  AGP 9.2.1 bundles KGP 2.2.10 — identical to 9.3.1 — so built-in Kotlin,
  `extraWarnings`, and the `built_in_kotlinc` jacoco path are unchanged.

- **Why:** the maintainability rule adopted the same day requires the
  toolchain to be openable by at least the 3 latest Android Studio releases
  (verified against the official Studio↔AGP matrix, developer.android.com
  "about-agp", 2026-07-16). The intersection of the three latest Studio AGP
  ranges is **≤ 9.2**, so 9.2.1 is the freshest compliant AGP.

- **Out of scope / consequences:** Gradle stays 9.5.0 (no wrapper change);
  the downgrade is verified (gate green, jacoco still measures live
  `built_in_kotlinc` output, instrumented 9/9). Future AGP bumps must re-check
  the Studio↔AGP matrix first (rule: AGENTS.md).

- **Update (2026-08-14):** Gradle was later bumped 9.5.0 → **9.6.1** (the AS
  Quail 1 bundled Gradle, `7585873`) — the AGP decision is unchanged, the
  wrapper is current. Toolchain pair today: **AGP 9.2.1 / Gradle 9.6.1**.

### [2026-08-12] K2 -Wextra warnings-as-errors

- **Type:** problem-avoiding (silent compiler warnings were hidden debt; makes them build failures).

- **Problem:** the Kotlin compiler ran with default warnings only, so
  deprecated-API use and K2's `-Wextra` extra checks (redundant conversions,
  redundant initializers, platform-class misuse, written-once `lateinit`)
  accumulated silently — 30 Java-deprecation warnings and 8 `-Wextra` findings
  were present at enable time, invisible to every gate (lint/detekt/spotbugs do
  not see Kotlin compiler warnings).

- **Alternatives considered:** leave warnings at default (warnings accumulate
  silently — rejected); enable `-Wextra` only as warnings (no enforcement —
  rejected); enable `-Wextra` + `allWarningsAsErrors` with per-site `@Suppress`
  downgrades for genuinely-unfixable usage (chosen). A Kotlin lint-like
  tautology checker was researched (detekt 1.23.8 has no always-true/
  tautology rule; the K2 compiler does no boolean algebra, so `X || !X` as a
  function argument is invisible to it) — out of scope, the "tests must be able
  to fail" discipline covers that class instead.

- **Chosen solution:** `kotlin { compilerOptions { extraWarnings.set(true); allWarningsAsErrors.set(true) } }` in `app/build.gradle` — the extension level
  covers main, unit-test AND androidTest compilations. All 38 findings were
  resolved: fixable ones migrated to modern APIs (commons-io 2.22
  `BoundedInputStream.builder()`, Robolectric `SensorEventBuilder` +
  `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()`, `resources.displayMetrics`),
  genuinely-unfixable ones suppressed per-site with a rationale comment
  (registry: TESTING.md "Compiler warnings as errors").

- **Why:** "all warnings are a hidden error" (technical debt) — a warning that
  is tolerated today silently stays, and `allWarningsAsErrors` makes the next
  warning a build failure with the exact file:line to fix. The pre-existing 30
  deprecation warnings were a real signal (3 were test bugs hiding behind
  deprecated-but-working APIs; 4 were commons-io/Android API misuse).

- **Out of scope / consequences:** Kotlin has NO per-warning `-Wno-error`
  flag — a "downgrade" is a full suppression (the warning stops showing even as
  a warning); the accepted-suppression set is small (7 sites) and each carries a
  rationale. The JVM `javac -Xlint:all` safety net (main, D8) remains warnings-
  only (Error Prone still deferred). Coverage unchanged (LINE 0.9753, BRANCH
  0.8661). Kotlin cannot catch boolean tautologies — that stays a test-discipline
  concern. The AGP 9 built-in Kotlin DSL was verified empirically against the
  bundled KGP 2.2.10 (`KotlinAndroidProjectExtension.compilerOptions`), not
  guessed.

### [2026-08-05] local.properties lint escape

- **Type:** problem-avoiding (a machine-local path was failing the lint build).

- **Problem:** `sdk.dir=C:/Users/...` (and `C\:\\...`) made `./gradlew lint`
  fail with `PropertyEscape` (3 errors → 2 errors). The detector wants only the
  drive-letter colon escaped.

- **Alternatives considered:** backslash-escaping the whole path (still fails);
  `C\:/...` with only the drive-letter colon escaped.

- **Chosen solution:** `sdk.dir=C\:/Users/<user>/Android/Sdk` (generic pattern;
  never commit a concrete machine path).

- **Why:** the `PropertyEscape` detector is satisfied by escaping only the
  drive-letter colon.

- **Out of scope / consequences:** lint green (0 errors). The file is
  gitignored; only this machine is affected.

### [2026-08-05] Keystore path correction

- **Type:** problem-avoiding (a docs correction; the wrong path would point at a non-existent keystore).

- **Problem:** AGENTS.md claimed the keystore lives at "repo root". The
  relative path `../android-keystorage.jks` from `app/` resolves one level
  **above** the repo root.

- **Alternatives considered:** naming the concrete machine path (rejected —
  machine-specific, and the keystore must never leak); documenting the
  resolution generically.

- **Chosen solution:** document that the keystore resolves to the parent of
  the repo root, without naming a concrete machine path; AGENTS.md now just
  says "see MEMORY.md".

- **Why:** the keystore deliberately lives *outside* the workdir (no secrets
  in the repo); only the *relative resolution* matters, not the absolute path.

- **Out of scope / consequences:** no code change — a docs correction. The
  gitignore keeps `**/*.jks` out of the repo.

### [2026-08-05] Toolchain + quality gates approved (single main flavor)

- **Type:** problem-avoiding (build/dependency floor; prevents incompatible-toolchain drift).

- **Problem:** the maintainer reviewed the lobe-style plan again and granted
  freedom to upgrade tooling/deps. Constraints: backward-compatible with 99% of
  Androids; tests-first (TDD) so features keep working; keep Java sources this
  release (pure-Kotlin later); single main flavor.

- **Alternatives considered:** minSdk 21 (initially kept — AndroidX floor for
  libs released before June 2025, 99.8% device coverage) vs **minSdk 23**
  (98.0% coverage, but Robolectric 4.16 already drops API 21/22 and the AndroidX
  floor moved to 23); toolchain AGP 8.13.2 + Gradle 8.14.5 vs AGP 9.3.1 +
  Gradle 9.5.0 (the latter breaking, so staged); a legacy flavor (postponed).

- **Chosen solution (D14–D19):** minSdk 21 kept
  initially — **REVERSED 2026-08-08 to minSdk 23** ("forget obsolete",
  `4753c45`); toolchain went to AGP 9.3.1 + Gradle 9.5.0 + built-in Kotlin
  (`78aace4`); `compileSdk/targetSdk/maxSdk 36` (Play requires targetSdk 36 from
  2026-08-31); deps pinned to minSdk-23-compatible freshest (core 1.16.0 /
  appcompat 1.7.1; core 1.17+ needs minSdk 23, 1.19.0 additionally needs
  compileSdk 37 — locked at 36). Version 1.41.

- **Why:** Robolectric 4.16's `OLDEST_SDK = 23` means the app's declared min
  never matched what tests ran; the new AndroidX floor is minSdk 23. Single
  main flavor keeps the build simple and the gates green.

- **Out of scope / consequences:** legacy flavor postponed; targetSdk 34→36
  edge-to-edge enforcement on the fullscreen gamepad UI is the biggest risk and
  needs an API 36 emulator smoke test. The minSdk reversal is itself recorded
  as a decision (below).

### [2026-08-05] lint.xml MissingTranslation relaxation

- **Type:** problem-avoiding (the English-only convention tripped a lint check).

- **Problem:** resources are English-only (`resourceConfigurations += ['en']`),
  so the absence of other languages triggers `MissingTranslation`.

- **Alternatives considered:** adding translations (rejected — out of scope,
  English-only is the convention); failing the build (rejected).

- **Chosen solution:** `lint.xml` downgrades `MissingTranslation` to a warning.

- **Why:** missing translations are the intended convention, not a defect.
  Recorded here per the suppression policy (every relaxation carries a
  rationale).

- **Out of scope / consequences:** no translation work; new lint issues still
  fail the build (baseline ratchet).

### [2026-08-06] Edge-to-edge and Robolectric 4.16.1 (SDK 36 migration)

- **Type:** problem-avoiding (targetSdk 36 enforcement broke the fullscreen UI and the test runner).

- **Problem:** targetSdk 36 enforces edge-to-edge. `MainActivity` used the
  removed `FLAG_FULLSCREEN` + deprecated `View.SYSTEM_UI_FLAG_*` set, which is a
  no-op on API 36 and leaves the window focus-less — Espresso then fails every
  interaction with `RootViewWithoutFocusException`. Separately, Robolectric
  4.15.1 (plan R8) does not know SDK 36 and throws `UnknownSdk` on
  `Config.TARGET_SDK`.

- **Alternatives considered:** keeping the legacy fullscreen flags (no-op,
  rejected); Robolectric 4.15.1 (does not support SDK 36, rejected); 4.16.1.

- **Chosen solution:** fullscreen → `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)` + `WindowInsetsControllerCompat`
  (`show`/`hide(WindowInsetsCompat.Type.systemBars())`,
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). Robolectric → 4.16.1 (needs JDK 21,
  which both local and CI provide). Also pre-empt the first-immersive-entry
  `ImmersiveModeConfirmation` overlay with
  `adb shell settings put secure immersive_mode_confirmations confirmed` before
  `connectedDebugAndroidTest`.

- **Why:** the modern `WindowInsetsControllerCompat` API is the only path that
  works under API 36's enforced edge-to-edge; 4.16.1 is the first Robolectric
  that supports SDK 36.

- **Out of scope / consequences:** all 9 instrumented tests pass on the local
  API 36 emulator (2 of 3 runs; one flake was an activity-launch timeout under
  load, not a code issue). The immersive pre-empt is a CI/emulator prerequisite,
  not app code.

### [2026-08-08] Lint baseline cleanup: 11 → 2 (ROADMAP Phase 6)

- **Type:** problem-avoiding (a stale baseline hid real issues).

- **Problem:** the strict-lint baseline had 11 entries (AGP/deps/resources from
  the Kotlin-migration head). Each entry is either fixable or a recorded,
  intentional decision.

- **Alternatives considered:** keep all 11 baselined (hides real issues);
  fix/delete each and keep only genuine locks.

- **Chosen solution:** drove the baseline to 2. Fixed the annotation bump;
  `UnsupportedChromeOsHardware` → `required="false"` (the pads track a single
  pointer each — no multi-pointer math); deleted 2 unused resources;
  consolidated 2 duplicate strings; moved icons to density buckets; converted
  PNGs to WebP. Kept baselined only: `AndroidGradlePluginVersion` + the
  core-ktx 1.19.0 `GradleDependency` lock (both version-availability decisions).

- **Why:** the baseline is a ratchet — new warnings must fail the build, so
  only genuine version locks belong in it.

- **Out of scope / consequences:** under AGP 9 the `AndroidGradlePluginVersion`
  entry's baseline location became a machine-specific absolute path (wrapper
  file) — per the env-dependent rule it moved to `lint.xml` as
  `severity="ignore"`, leaving the baseline at two `GradleDependency` locks
  (compileSdk 36 + core-ktx 1.16.0). Stale baseline entries are never
  auto-pruned by AGP — prune by hand after a build-file refactor.

### [2026-08-08] Configuration cache disabled — external keystore defeats it (then REVERSED by AGP 9)

- **Type:** problem-avoiding (config-cache was silently invalidating every run).

- **Problem:** every single Gradle invocation printed `configuration cache cannot be reused because an input to unknown location has changed` — config-cache was silently invalidated on every run, delivering zero benefit while re-deriving the task graph each time.

- **Alternatives considered:** leave it on (zero benefit, constant invalidation);
  disable it (the chosen path at the time); fix the keystore input (alone
  insufficient — the AGP `https.proxyHost` sys-prop read was unfixable from our
  build files).

- **Chosen solution (user, 2026-08-08):** `org.gradle.configuration-cache=false`.
  Deferred re-evaluation to the AGP 9 migration (Gradle 9 makes config-cache the
  norm). The keystore detection logic itself is unchanged.

- **Why:** config-cache delivered zero benefit (invalidated every run); the AGP
  sys-prop read is unfixable from our side, so even fixing the keystore might
  not restore reuse.

- **Out of scope / consequences:** **REVERSED the same day** — the AGP 9
  migration fixed the `https.proxyHost` read, so config-cache reuses again
  (`=true`). Lesson: a toolchain migration can nullify a previously-correct
  workaround — re-probe locked-inhibited features after a bump.

### [2026-08-08] AGP 9.3.1 / Gradle 9.5.0 migration LANDED

- **Type:** problem-avoiding (superseded toolchain + a config-cache blocker).

- **Problem:** AGP 8.13.2 + Gradle 8.14.5 were superseded; AGP 9 brings
  built-in Kotlin and fixes the `https.proxyHost` config-cache blocker, and
  Gradle 10 deprecations (space-assignment syntax) are coming.

- **Alternatives considered:** stay on AGP 8 (miss the config-cache fix, accrue
  deprecation debt); migrate directly on the main branch (risky); probe on a
  scratch branch first.

- **Chosen solution:** migrate to **AGP 9.3.1 + Gradle 9.5.0** with built-in
  Kotlin, run first on a scratch branch (`feat/agp9-probe`), then
  fast-forwarded onto `feat/global-refresh` (`78aace4`). Removed the
  `org.jetbrains.kotlin.android` plugin (built-in Kotlin; `kotlinOptions {}`
  gone, `jvmTarget` defaults to `compileOptions.targetCompatibility` = Java 11).
  Re-enabled `org.gradle.configuration-cache=true`. Regenerated the wrapper to
  9.5.0. Re-scoped the lint baseline.

- **Why:** green first try on the probe proved the migration safe; no kapt /
  `kotlin.sourceSets` used, so the switch was trivial here. Config-cache reuse
  is confirmed ("Reusing configuration cache", two consecutive runs).

- **Out of scope / consequences:** verified `assembleDebug` +
  `assembleDebugAndroidTest`; full gate green (JaCoCo LINE 697/717 = 97.2%,
  BRANCH 176/215 = 81.9%); CI run `31264372367` green (build 3m41s + 9/9
  instrumented on aosp_atd). Remaining deprecation: `ReportingExtension.file(String)`
  from a third-party plugin (scheduled Gradle 10 removal) — not ours. Scratch-
  branch probing is the pattern for any future toolchain bump.

### [2026-08-09] JaCoCo class dir for AGP 9 built-in Kotlin (coverage gate was under-measuring)

- **Type:** problem-avoiding (the coverage gate was silently measuring a stale class set).

- **Problem:** adding the first new app class since the AGP 9 migration
  (`RawSocketHttpStream`) exposed that the jacoco report/verification read a
  **stale** `tmp/kotlin-classes/debug` dir — AGP 9's built-in Kotlin now outputs
  to `intermediates/built_in_kotlinc/...`, so the 95 line / 80 branch gate was
  silently measuring an outdated class set and **new app classes were invisible
  to it**. The corrected measurement was 93.0% line / 69.7% branch — below the
  gate, yet CI had been green.

- **Alternatives considered:** keep the stale path (gate stays false-green —
  rejected); lower the gate to the measured level (hides the real state —
  rejected); point jacoco at the live built-in Kotlin output and drive coverage
  back above the gate.

- **Chosen solution:** `debugKotlinClasses` now points at
  `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes` (verified
  against the compile output; if AGP relocates it the report shows ~0% and the
  gate fails loudly rather than silently passing). Coverage restored to
  **97.2% line / 80.85% branch** with targeted tests (chunked decoder,
  SenderViewModel, https fallback, read/skip edges) (`abdbcd3`).

- **Why:** the gate is only worth having if it measures the code that actually
  compiles; a silent false-green is worse than a loud red.

- **Out of scope / consequences:** pre-existing gaps (MjpegInputStream recovery
  edges, MainActivity branches) remain uncovered but the gate now holds at the
  corrected measurement. Any future toolchain move should re-verify the jacoco
  class-dir path.

### [2026-08-09] Dev tooling is cross-platform via uv (Python gate)

- **Type:** problem-avoiding (Windows-first scripts blocked switching dev machines).

- **Problem:** the dev scripts and docs were Windows-first (PowerShell
  `scripts/gate.ps1`, pre-commit hook `cmd /c gradlew.bat`, `.venv\Scripts\...`
  paths, AEHD-only emulator notes), which blocks switching dev machines to
  Linux (Ubuntu → Arch) and macOS. After Campaign 7 the project must be ready
  for cross-platform development.

- **Alternatives considered:** keep PowerShell + add a bash twin (double
  maintenance, drift-prone); a Makefile (poor Windows story); Python/uv
  (already the dev-tooling manager, present on all three platforms).

- **Chosen solution:** dev tooling is declared in `pyproject.toml`
  (`dependency-groups`: lizard, pre-commit, mdformat) and locked in the
  committed `uv.lock`; `uv sync` (re)creates `.venv` on any platform.
  `scripts/gate.py` + `scripts/spotless_apply.py` (stdlib, subprocess) replace
  `gate.ps1`, and the pre-commit entry is now `language: system` +
  `python scripts/spotless_apply.py` (cross-platform; subprocess runs
  `gradlew.bat` directly on Windows — probed). PowerShell-specific rules were
  consolidated into AGENTS.md "Windows/PowerShell quirks" (not generalized —
  re-audited on the first POSIX box). Emulator docs now carry a platform table
  (Windows AEHD / Linux KVM / macOS Hypervisor.framework, macOS unverified).

- **Why:** uv is already in use and runs unchanged on Windows/Linux/macOS;
  stdlib Python runs wherever Python 3.9+ does; CI needs no change (already
  Linux), so the Linux/macOS paths are exercised by the first POSIX dev box.

- **Out of scope / consequences:** no generalization of the PowerShell quirks
  into universal rules (deferred to a POSIX-box re-audit); `ci.yml` untouched
  (gate.py Linux validation deferred); macOS emulator verification pending a
  macOS machine; `scripts/gate.ps1` deleted (git history retains it).

______________________________________________________________________

## Testing

### [2026-08-05] Deterministic unit tests (ephemeral ports + latches)

- **Type:** problem-avoiding (flaky parallel-JVM tests).

- **Problem:** `./gradlew test` intermittently failed. `DummyServer` bound its
  `ServerSocket` on a background thread and asserted client/server state
  immediately after `runAll()`+`idle()`. On fast machines the client connected
  before the server bound → `ConnectException`, failed `isConnected()` assert,
  and a leaked accept-thread kept the port bound → cascading `BindException`.
  The three test variants (`debug`/`release`/`releaseDebug`) additionally ran
  in parallel JVMs over the same fixed ports (`localhost:12345` + shifts) →
  cross-JVM `BindException`s.

- **Alternatives considered:** fixed ports with shifts (rejected — parallel
  JVMs still collide); binding the socket on a background thread and polling
  (rejected — bind-late race); bind synchronously + ephemeral port + latch
  awaits.

- **Chosen solution:** bind synchronously in the constructor; assert only after
  `CountDownLatch` awaits (`awaitConnection`/`awaitCount`, 5 s timeout); bind
  **ephemeral ports** (`ServerSocket(0)`) and target `server.port`.

- **Why:** fixed ports cannot survive parallel JVMs; latch awaits remove the
  accept/read race; constructor-bind removes the bind-late race.

- **Out of scope / consequences:** 3× consecutive full `test` runs green.
  `DummyServer.DEFAULT_PORT` no longer exists in the unit test (the androidTest
  `DummyServer.kt` still has it — different class, don't merge).

### [2026-08-05] AEHD for local emulator

- **Type:** problem-avoiding (no working hypervisor driver).

- **Problem:** `emulator -accel-check` returned code 6 ("hypervisor driver not
  installed"); x86_64 images unusable.

- **Alternatives considered:** Intel HAXM (superseded); AEHD.

- **Chosen solution:** installed AEHD 2.2 via
  `silent_install.bat` (UAC). Now `accel-check` = 0.

- **Why:** HypervisorPresent=False, VBS off — no conflict with AEHD; it is the
  current supported hypervisor driver for Windows.

- **Out of scope / consequences:** local acceleration only; CI uses KVM on
  ubuntu-latest. Windows-specific: Linux uses KVM, macOS uses
  Hypervisor.framework (Campaign 7 — per-platform table in TESTING.md).

### [2026-08-06] Emulator: AVD config is the source of truth (slow-boot lesson)

- **Type:** problem-avoiding (CLI flags fought the AVD config and caused a 2.7 h boot).

- **Problem:** an ad-hoc emulator launch used `-gpu swiftshader_indirect -no-snapshot`, overriding the AVD's declared `hw.gpu.mode=host` + quickboot. The
  AVD cold-booted under software rendering at 1080×2340@440dpi and stayed
  `offline` for ~2.7 h.

- **Alternatives considered:** fighting the AVD config with CLI flags (rejected —
  caused the 2.7 h boot); aligning the launch with the AVD's config.

- **Chosen solution:** the AVD's `config.ini` is the source of truth; CLI flags
  must not fight it. The `config.ini` was fixed to `hw.gpu.mode=host`,
  `fastboot.forceFastBoot=yes`, `firstboot.bootFromDownloadableSnapshot=yes`,
  6 cores, 4 GB, and two `emu-launch*.bat` launchers were created to teach the
  correct invocation (then deleted as training material). Correct local launch:
  `emulator -avd Simple_Phone_API36 -no-window -no-audio -no-boot-anim -gpu host`
  (snapshots stay enabled).

- **Why:** relaunch booted in ~39 s (`Boot completed in 38877 ms`, NVIDIA GPU
  translator) vs ~2.7 h.

- **Out of scope / consequences:** the `default_boot` snapshot
  save-on-exit discrepancy was later **closed as a local cosmetic quirk**
  (2026-08-08): snapshot save/load follows the emulator's own config
  resolution; aosp_atd is CI-oriented (snapshot writing inert), loading still
  works. Never pass `-no-snapshot` for local iteration; only for pristine
  CI-style cold boots.

### [2026-08-06] Coverage drive to 85%: static-state hazard + network-free pad tests

- **Type:** problem-avoiding (test determinism under a rising coverage gate).

- **Problem:** raising the JaCoCo gate from 10% to 85% line required unit tests
  for previously-untested classes (MainActivity, pads, settings, mjpeg parsing).
  Several Robolectric-specific traps emerged.

- **Alternatives considered:** test the pad logic over a live TCP connection
  (rejected — flaked on CI under load); test the pad's command-string/touch
  math directly (chosen).

- **Chosen solution:** test UI logic **without a network dependency** —
  `SquareTouchPadLayoutTest` asserts `mExecutor.runAll() > 0` (a `send()` was
  forwarded to the injected `PausedExecutorService`) instead of awaiting a live
  connection. **Poll instead of fixed sleeps** — `keepaliveShouldBeSentWhileConnected`
  polls up to 10 s (sleep 500 ms + `runAll()` + check each iteration).
  Robolectric HTTP is unreliable for real sockets, so the `StartReadMjpegAsync`
  success path is covered via null/error branches.

- **Why:** deterministic, no sockets; polls absorb busy-CI JVM timing.

- **Out of scope / consequences:** 11.3% → **85.3% line / 60.6% branch**
  (604/708); gate raised to `0.85 LINE / 0.60 BRANCH`. The `SenderService`
  static-state hazard (`keepaliveTimeout`/`mConnectTask`) that flaked the `[23]`
  variant was **removed 2026-08-08 (ROADMAP Phase 3)** — constructor-injected
  instance fields replace the reflection reset. MjpegView render-thread plumbing
  stays ~0% (untestable) and is excluded from the gates.

### [2026-08-08] Campaign 2 strategy: coverage-first, then refactor (and test-quality policy)

- **Type:** problem-avoiding (refactor safety without a coverage net).

- **Problem:** the ROADMAP campaign needed an order for refactoring (B),
  coverage (C), CI hardening (A), cache tuning (D), AGP 9 (E) — and the suite
  had a policy question: which refactors are safe first.

- **Alternatives considered:** refactor-first (riskier — no coverage safety net
  under the 95/80 ratchet); coverage-first with one exception.

- **Chosen solution (user decision 2026-08-08):** **coverage-first** — increase
  test coverage before refactoring, maintain it through refactoring. Order
  B2 → B4 → C → B3 → A → D → E. B4 is the one granted exception (pure
  touch-math extraction reduces test-writing complexity).

- **Why:** a higher coverage floor makes every subsequent refactor verifiable;
  the ratchet gate (95 line / 80 branch) protects against silent regressions.

- **Out of scope / consequences:** release 1.42, Dependabot auto-merge and GPG
  were explicitly deferred. Test-quality policy: the full 3-variant suite runs
  twice before pushing test changes; async-server asserts use bounded polls.

### [2026-08-08] Orchestrator kept for the 9-test suite

- **Type:** problem-avoiding (crash isolation for the instrumented suite).

- **Problem:** AndroidX Test Orchestrator adds per-test restarts (crash
  isolation + clean package data) at a runtime cost; with only 9 instrumented
  tests it is worth re-weighing.

- **Alternatives considered:** drop Orchestrator (faster, but a crashing test
  can poison the next test's state); keep it.

- **Chosen solution:** keep `ANDROIDX_TEST_ORCHESTRATOR` for now.

- **Why:** crash isolation and clean package data outweigh the runtime cost at
  this size; re-weigh if the suite grows.

- **Out of scope / consequences:** a fixed `localhost:12345` DummyServer port
  would collide if the suite is ever sharded — not a current concern.

### [2026-08-09] Test logical SLOC metric: per-class token total + duplication gate (Campaign 6)

- **Type:** problem-avoiding (test copy-paste growth had no measurable guard).

- **Problem:** tests are code and must stay high quality — copy-paste growth
  (three hand-rolled TCP test servers, repeated `prefs.edit().putString(...)`
  boilerplate, a 17-class `@Config` triple, per-test frame-assembly in
  MjpegInputStreamTest) inflates logical SLOC and hides the real intent of each
  test. We needed a *measureable* target to keep it low, not a vibes rule.

- **Alternatives considered:** (1) physical line count — gameable by formatting
  and ignores comment/blank weight; (2) a per-function `token_count`/NLOC cap
  (`lizard -T ...`) — punishes data-driven tables whose `cases` literals are
  legitimate test data; (3) **per-class summed `token_count` (lizard) + a hard
  duplication gate (jscpd)** — the chosen one.

- **Chosen solution:** metric = summed per-class `token_count` from
  `lizard -l kotlin` over `app/src/test` + `app/src/androidTest` (a Halstead-N
  proxy: operators + operands, comments/blanks excluded), reported as a trend
  against a committed baseline (A0 = 12,659 tokens). Enforcement = **jscpd hard
  gate**: `npx jscpd app/src/test app/src/androidTest --config .jscpd.json`
  (min-tokens 50, threshold 0) fails the gate on ANY new duplication block ≥ 50
  tokens — this directly encodes "re-use what is similar".

- **Why:** the coverage gate (95 line / 80 branch) measures app classes only
  (`**/*Test*.*` excluded), so shrinking tests cannot lower it; the only rule is
  preserving the set of exercised branches (each table row must keep hitting its
  distinct branch). Token totals reward the table-izing pattern (one class total
  collapses) that a naive per-function cap would fight. The duplication gate is
  the "reuse" enforcement and is not brittle to legitimate new tests (new tests
  add little duplication).

- **Out of scope / consequences:** JUnit 5 (would change the test toolchain for
  parameterization we get free from `listOf(...).forEach {}`); AGP `testFixtures`
  (no unit↔androidTest server sharing yet); androidTest `DummyServer` keeps its
  fixed `localhost:12345` by design; per-function caps; comment removal
  (rationale comments are docs, not logical SLOC). jscpd's `paths` config key
  does not restrict the scan (scans cwd) — the gate always passes the two source
  dirs as positional arguments (verified 2026-08-09).

### [2026-08-09] jscpd import-ignore calibration is cross-platform (CI red 31303791390)

- **Type:** problem-avoiding (a line-ending-dependent gate config).

- **Problem:** the jscpd hard gate passed locally (Windows, CRLF working tree)
  but failed on CI's Linux/LF checkout: `ignorePattern: ["import"]` (bare token)
  suppressed the residual import-header clones locally, but CI re-found 2 of
  them (67/54 tokens) — a red run + debugging.

- **Alternatives considered:** raise `minTokens` to 80 (robust but lets real
  50–79-token logic duplication through the gate); keep the bare-token pattern
  (proven LF-fragile); use a whole-line pattern `["import.*"]`.

- **Chosen solution:** `ignorePattern: ["import.*"]` in `.jscpd.json` — skips
  whole import lines, verified on both the CRLF working tree and an LF export
  of the committed blobs (the technique for reproducing CI line endings locally).

- **Why:** the bare-token match only strips the `import` keyword (~8 tokens per
  import block), leaving the block ≥ 50 tokens; the local CRLF pass was an
  artifact of the trailing `\r` breaking the clone token match. `import.*` is
  the only pattern verified line-ending-independent. `minTokens=80` would
  meaningfully weaken the gate (the removed `@Config`/frame-assembly clones were
  50–88 tokens).

- **Out of scope / consequences:** the residual import-header clones (52–76
  tokens) are language boilerplate, never logic duplication; the gate still
  fails on any real logic clone ≥ 50 tokens.

### [2026-08-11] Campaign 13: branch-coverage ratchet 80% → 85%

- **Type:** problem-avoiding (silent branch-coverage drift).

- **Problem:** the BRANCH gate sat at 0.80 with measured 0.834 (509/610
  branches) after Campaign 12; a 3.4 pt headroom invites silent regressions,
  and branch coverage is the metric the C-push historically had to grind.

- **Alternatives considered:** leave the gate at 0.80 (no enforcement — the
  number would drift without consequence); raise the gate to 0.85 without new
  tests (fails the gate); add targeted tests AND raise the gate (ratchet).

- **Chosen solution:** targeted branch tests (HardwareGamepadController D-pad
  all four directions + `SOURCE_GAMEPAD`-only motion; MainActivity
  `ACTION_MULTIPLE` dispatch, `setSenderService(null)`, onDestroy/onPause/
  onResume with nulled collaborators, Connected edge with a null retry
  controller; SenderService `connect()` on a blank host + `ShadowLog`
  `setLoggable("TCP", DEBUG)` for the DEBUG-gate true branches; MjpegInputStream
  EOF-in-header / empty-line / colon-less-line fixtures) then BRANCH `minimum`
  → `0.85` in `app/build.gradle`.

- **Why:** 529/610 = **0.867** measured after the tests — a 1.7 pt margin over
  the new gate (the Kotlin-synthetic `?.`/`?:`/`isNullOrBlank` branches in
  MainActivity/SettingsFragment stay structurally unhittable, so ~85% is a
  realistic ceiling for the current code shape without excluding more logic).

- **Out of scope / consequences:** the LINE gate stays at 0.95 (measured 0.971);
  the remaining 81 missed branches are dominated by SettingsFragment (21) and
  framework-coupled MainActivity/SettingsFragment null branches. `ShadowLog`
  `isLoggable` defaults to `level >= INFO`, so the DEBUG-gate false branches
  were already covered — the test forces the true side.

______________________________________________________________________

## CI & emulator

### [2026-08-06] Immersive mode confirmation steals focus on API 35+ (instrumented tests)

- **Type:** problem-avoiding (an OS overlay broke every instrumented interaction).

- **Problem:** after the SDK 36 / targetSdk 36 upgrade, every Espresso
  interaction on the API 36 emulator failed with `RootViewWithoutFocusException`
  (`has-window-focus=false` even though the DecorView reports `has-focus=true`).
  The gamepad runs immersive (system bars hidden), and the first time an app
  enters immersive mode on API 35+ the system pops an
  `ImmersiveModeConfirmation` overlay ("swipe to exit fullscreen") that keeps
  window focus.

- **Alternatives considered:** editing app code (rejected — not an app bug);
  disabling the confirmation overlay once per AVD.

- **Chosen solution:** disable the confirmation before running instrumented
  tests: `adb shell settings put secure immersive_mode_confirmations confirmed`.
  This is a CI/emulator prerequisite, not app code. Also migrated
  `MainActivity`'s fullscreen handling to
  `WindowCompat.setDecorFitsSystemWindows(false)` + `WindowInsetsControllerCompat`
  (the legacy set is a no-op under API 36).

- **Why:** the overlay is an OS behavior on API 35+, not an app defect; the
  settings write pre-empts it.

- **Out of scope / consequences:** a "focus" symptom after a targetSdk bump is
  often an OS overlay, not app code — check `dumpsys window` `mCurrentFocus`
  for system windows before touching the app.

### [2026-08-06] CI emulator image: google_apis broken-pipe → aosp_atd

- **Type:** problem-avoiding (a heavy image failed under resource pressure).

- **Problem:** the instrumented job failed twice with `Failed to commit install session ... package install-commit ... Broken pipe (32)` while installing the debug APK on an API 36 `google_apis` image under swiftshader. The emulator also spent minutes `offline` during boot, and the `google_apis` image is heavy (Google services the tests don't need).

- **Alternatives considered:** keep `google_apis` (rejected — heavy, boots
  slowly under software rendering, install-commit pipe dies under resource
  pressure on 2-core runners); `aosp_atd` (Android Test Device — lightweight,
  headless, CI-oriented).

- **Chosen solution:** switch to `target: aosp_atd`, `cores: 4`,
  `ram-size: 4096M`; bumped actions to Node-24 majors (`actions/checkout@v7`,
  `actions/setup-java@v5`, `actions/upload-artifact@v7`) and pinned
  `gradle/actions/setup-gradle@v5.0.2` (v6 ships a proprietary caching
  component; v5 is MIT).

- **Why:** aosp_atd is smaller and faster than google_apis; the extra resources
  (cores/ram) relieve the install-commit pipe.

- **Out of scope / consequences:** at the time the `build` job was green but
  instrumented still failed 8/8 with `RootViewWithoutFocusException` under
  `swiftshader_indirect` — root-caused next (below) to the software GPU, then
  finally fixed by the focus pre-empt retry + focus-wait.

### [2026-08-06] Local instrumented: adopt aosp_atd, root cause isolated

- **Type:** problem-avoiding (two changed variables masked the real root cause).

- **Problem:** CI instrumented failed 8/8 (`RootViewWithoutFocusException`) on
  `aosp_atd` + `-gpu swiftshader_indirect`, while the local `default`-image AVD
  passed 9/9 with `-gpu host` — two variables (image and GPU) changed at once.

- **Alternatives considered:** blame the aosp_atd image (wrong); test aosp_atd
  locally with `-gpu host` to isolate the variable.

- **Chosen solution (experiment):** installed `system-images;android-36;aosp_atd;x86_64`, created AVD `Atd_API36`, booted with `-gpu host` + immersive
  pre-empt, ran `connectedDebugAndroidTest`.

- **Why / result:** **9/9 green** (boot ~0 s via snapshot, suite 4m50s) — the
  root cause is the `-gpu swiftshader_indirect` headless combo (under the
  software GPU the app window never receives focus), NOT the image. Keep
  `Atd_API36` (aosp_atd) as the local AVD; local runs must use `-gpu host`.

- **Out of scope / consequences:** TESTING.md local recipe now names
  `Atd_API36`/aosp_atd; CI added the missing KVM-enable step. Superseded on CI
  by the focus pre-empt retry + `FocusAwareActivityTestRule` (below).

### [2026-08-07] CI focus flake: root-caused and fixed (pre-empt race)

- **Type:** problem-avoiding (a lost settings write raced the boot).

- **Problem:** three consecutive CI instrumented runs failed with
  `AssertionError: App window never gained focus` from the focus-wait rule.
  Timestamps proved the pre-empt ran at 17:49:49 while "Boot completed" was not
  logged until 17:51:30 — `sys.boot_completed` reports `1` before the settings
  provider is ready, so a **single** `settings put` was silently lost.

- **Alternatives considered:** a single pre-empt write (proven lost); a
  GPU-capable/macOS runner (untried, expensive); retrying the settings write
  until `settings get` confirms it.

- **Chosen solution:** the ci.yml pre-empt **retries the settings write until
  `settings get` confirms it** (up to 60 s), both before the suite and in the
  retry branch; `FocusAwareActivityTestRule` waits for window focus and sends
  bounded BACK presses to dismiss a lingering overlay, with a `waitForFocus`
  flag so KeepAliveTests skip the wait.

- **Why:** the race is the settings provider coming up after `sys.boot_completed`;
  an acknowledged write cannot be silently lost.

- **Out of scope / consequences:** validated on CI (`31206742960`): the first
  ~5 tests pass with zero focus assertions (previously 0/9). Remaining
  instrumented failures are a *separate* swiftshader issue — `Failed to find ColorBuffer` rendering errors that hang Espresso under load on small runners
  (infra, not code).

### [2026-08-08] Phase 1 experiment 2: aosp_atd + swiftshader PASSES instrumented (no-macOS runner)

- **Type:** problem-avoiding (proved the focus fixes obsolete an earlier conclusion).

- **Problem:** every CI instrumented run had failed (build gate green
  throughout). The original "aosp_atd + swiftshader NEVER grants focus (8/8)"
  finding (08-06) predated the immersive pre-empt (retry-until-confirmed) and
  the `FocusAwareActivityTestRule` focus-wait.

- **Alternatives considered:** a GPU-capable/macOS runner with `-gpu host`
  (expensive, ROADMAP fallback); re-testing `aosp_atd` + swiftshader with the
  focus fixes in place (experiment 2).

- **Chosen solution:** **no macOS/GPU runner — experiment 2 won.** Keep
  `target: aosp_atd` + `-gpu swiftshader_indirect` + the immersive pre-empt +
  `FocusAwareActivityTestRule`; all 9 instrumented tests PASSED (first
  fully-green run `31232406163`, 2026-08-08). `profile: pixel_5` is dropped for
  aosp_atd (atd needs no device profile).

- **Why:** the 08-06 "never grants focus" conclusion was obsolete once the
  focus-wait + pre-empt fixes landed; aosp_atd boots faster and renders less
  than the default image.

- **Out of scope / consequences:** a CI script trap was fixed during this —
  `android-emulator-runner` runs each `script:` line as its own `sh -c`, so
  multi-line `if/fi` retries never parsed; any conditional must be a single
  line (`cmd || { ...; }`), validated with `sh -n`. No further experiments
  needed.

### [2026-08-08] CI publish job (dormant until master merge)

- **Type:** problem-avoiding (early adopters needed an installable APK without a release keystore).

- **Problem:** early adopters need an installable APK without a release build.

- **Alternatives considered:** publishing release (keystore never enters CI —
  rejected); a debug-signed `releaseDebug` artifact.

- **Chosen solution:** a CI `publish` job (master-only) that uploads a
  debug-signed `releaseDebug` APK artifact on green runs (`a4a39b8`).

- **Why:** the debug keystore is auto-generated per machine/CI, so the artifact
  stays installable while the real release keystore never leaves local.

- **Out of scope / consequences:** **dormant** during the single-branch no-PR
  workflow (nothing pushes to master); activates when a master merge lands.

______________________________________________________________________

## Architecture

### [2026-08-19] https video binds to the Wi-Fi network (Network.openConnection + trust-all TLS)

- **Type:** design-clarifying (completes S13 for the https path — A.2 only
  bound sockets, leaving the https fallback on the default network).

- **Problem:** A.2 bound control and raw-socket video sockets to the robot
  Wi-Fi AP, but the https video branch of `VideoStreamLoader` still used
  `url.openConnection()` — that opens on the system *default* network, which on
  a phone with cellular data enabled is cellular while the robot AP is the
  connected Wi-Fi. An https camera URL therefore violated S13 (route over the
  Wi-Fi AP whenever one exists). The robot's camera is also typically a
  self-signed TLS endpoint, which a stock `HttpsURLConnection` rejects.

- **Alternatives considered:** (a) wrap the default-network
  `HttpsURLConnection` socket in TLS manually via `SSLSocketFactory` +
  `Network.bindSocket` (more code, duplicates the https protocol handling);
  (b) route only http and declare https out of scope (rejected — the user asked
  for bullet-proof https support); (c) trust policy strict system-CA (rejected
  by user 2026-08-19: option 1 trust-all for now, TOFU maybe later).

- **Chosen solution:** `Network.openConnection(URL)` (API 21+, minSdk-23-safe,
  verified in the android-23 stub) bound to the tracked Wi-Fi network,
  mirroring `SocketBinder` for sockets:

  - New `ConnectionOpener` fun interface (`open(url): URLConnection` +
    `identity`), mirroring `SocketBinder`.
  - `WifiConnectionOpener(context, wifiNetworkProvider = WifiNetworkTracker(context)::current, networkOpen, defaultOpen)` — Wi-Fi present →
    `network.openConnection(url)`, else / on `IOException` → `url.openConnection()`.
    `WifiNetworkTracker` was extracted from `WifiSocketBinder` to a top-level
    class shared by both binders.
  - For https the connection gets a trust-all `SSLSocketFactory` +
    allow-all `HostnameVerifier`, isolated in `WifiConnectionOpener`'s companion
    so the policy can be swapped later (user decision 2026-08-19: trust-all now,
    TOFU later). Lint suppression: `TrustAllX509TrustManager` +
    `AllowAllHostnameVerifier` in `app/lint.xml` with rationale (the robot
    camera is a self-signed private endpoint, not a general-purpose TLS site).
  - `VideoStreamLoader` takes a `connectionOpener` (default
    `WifiConnectionOpener(view.context)`); the http raw-socket branch is
    unchanged (it must stay raw to bypass the NSC cleartext whitelist).

- **Why:** same rationale as A.2 — the robot AP has no internet, so the system
  keeps cellular default; routing the https connection over the tracked Wi-Fi
  network preserves S13. `Network.openConnection` keeps the standard
  `HttpsURLConnection` protocol handling (redirects, TLS, timeouts), so the
  feature is a routing/trust change only. The seams (`wifiNetworkProvider`,
  `networkOpen`, `defaultOpen`) unit-test the decision deterministically —
  Robolectric's `ShadowNetwork` shadows only `bindSocket`, not
  `openConnection`, so the real call is a thin framework-facing line (same
  category as the documented uncovered `bindSocket` catch in A.2).

- **Out of scope / consequences:** trust-all applies to the *video* https
  connection only (control stays plain TCP; no other https surface exists).
  The trust policy is deliberately isolated for a later TOFU swap. On-robot
  verification (cellular + robot Wi-Fi with an https camera) remains a manual
  user step. Coverage: `WifiConnectionOpenerTest` (5 seam/routing tests,
  mutation-checked red) + `HttpsVideoStreamTest` (end-to-end frames over a
  self-signed local `HttpsServer`, mutation-checked red).

### [2026-08-18] Scenario-driven video retry — control gate removed (Option B)

- **Type:** design-clarifying (restates the DESIGN.md scenario contract; see
  also "Design sits above decisions").
- **Problem:** Campaign 8 gated the bounded video retry on the control
  `Connected` state. The scenario contract (DESIGN.md "Scenarios & use-cases")
  made that gate wrong on multiple axes: the reported freeze (video stayed
  frozen through the `Connecting` reconnect window, S2/S3/S9), a dead
  different-host video stream that never recovered while control stayed
  disconnected (S4/S6), and same-host watch-only phones (spectator, competition
  with control off) whose dead stream never recovered (S7/S14). Campaign 8's
  "idle recovery is bounded by user interaction" limitation also conflicts with
  S5/S7 (video must always self-heal).
- **Alternatives considered:**
  - *Keep the proxy, add `Connecting` + same-target detection (Option A)* —
    narrower change, but still leaves same-host watch-only (S7/S14) frozen and
    keeps the control↔video coupling the scenarios reject.
  - *Drop the control gate entirely (Option B — chosen)* — video reloads on
    `URL configured ∧ !playing ∧ activity resumed` alone.
- **Chosen solution:** `shouldReloadVideo()` becomes `videoUrl != null && video?.isPlaying == false`; the loading spinner is URL-gated (shown while a URL is configured and hidden when it is not — the reverse half matters because `restartVideoStream` runs on every `onResume`, so a now-empty URL must also *hide* a spinner a previous state showed); the control-`Connected` edge
  keeps the immediate-reload sugar (`onControlConnected`) so a pad touch still
  returns the video instantly instead of after a ≤5 s tick. The `SenderService`,
  `WifiSocketBinder` and haptics are untouched. Empty host = video-only (S5),
  empty video URI = control-only — both self-heal their one stream.
- **Why:** `isPlaying == false` already guarantees a healthy stream is never
  touched (P2), so the control gate was pure anti-hammering; a failed reload is
  a cheap TCP connect that closes its socket (P6 — the 30 s-restart disease was
  a socket *leak*, not hammering), so gating on control bought nothing real.
  Option B makes S3/S4/S5/S6/S7/S14/S11/S8 one uniform rule and **eliminates**
  the Campaign 8 interaction-bounded limitation (an idle gamepad now recovers
  video after a robot reboot). Cost: a genuinely-down same-host robot triggers
  one failed connect per 5 s tick — negligible (and in WAP-loss the route is
  gone, so it fails in milliseconds).
- **Out of scope / consequences:** no stall watchdog (a video-disabled robot
  legitimately keeps the spinner cycling — Campaign 15); no per-target retry
  tuning; supersedes Campaign 8's gate, Campaign 10's `Connected`-gated spinner
  and Campaign 12's empty-host-only gate relaxation (each carries a banner).
  Verified: control-agnostic gate test + "dead stream self-heals with control
  permanently disconnected" end-to-end test (TESTING.md scenario mapping).

### [2026-08-18] A.5 GPU-backed MJPEG rendering: plain View + HWUI onDraw

- **Type:** mixed — design-clarifying (the video surface is a product surface; GPU-backed onDraw is how it presents) with a problem-avoiding part (the render thread busy-spin).

- **Problem:** on-device profiling (60 s simpleperf callgraph) showed
  the MJPEG pipeline burning ~91% of the app's CPU and saturating the input
  path: the render thread **busy-spun** on `running`/`surfaceDone` property
  accessors (~36% of all samples) and the video was drawn via
  `SurfaceView`+`lockCanvas()` — a **software** canvas the Skia CPU rasterizer
  (the `lowp` `gather_8888`/`store_565` pipeline, ~29%). gfxinfo flagged
  **High input latency 1222/2689** (the video threads starved main-thread input
  dispatch).

- **Alternatives considered:**

  1. **TextureView subclass drawing in `onDraw`** — rejected: `TextureView.onDraw`
     is **final** (compile error). `TextureView.lockCanvas()` is also a software
     canvas, so it wouldn't fix the raster cost.
  1. **SurfaceView + GLES** (EGL context, textured quad, shaders) — full GPU,
     but a large renderer rewrite that is impractical to test under Robolectric.
  1. **Plain `View` drawing the decoded frame in `onDraw`** — the HWUI canvas is
     hardware-accelerated, so the center-crop scale is done by the GPU; the
     decode stays in `BitmapFactory`/libjpeg (only ~3% of CPU). Minimal rewrite:
     the render thread decodes + `postInvalidate()`, `onDraw` calls the tested
     `MjpegFrameRenderer.drawFrame`.
  1. Keeping `SurfaceView` and only de-spinning the loop — rejected: the ~29%
     software raster would remain.

- **Chosen solution:** option 3 + a de-spun render loop (blocks on the socket
  read; `Thread.sleep(5)` after a dropped frame so a full socket buffer cannot
  spin; no surface-monitor poll — the activity lifecycle drives
  `startPlayback`/`stopPlayback`).

- **Why:** measured — after the change, total CPU samples dropped 510k→146k
  (−71%), the spin + software-raster symbols disappeared from the profile, and
  gfxinfo High input latency went 1222→2 (frames 2689→3970, janky 0.05%).
  Robolectric keeps testing the decode/rect/FPS logic (`MjpegFrameRenderer`)
  and the renderer tests stayed green; the thread itself remains untestable in
  Robolectric (documented).

- **Out of scope / consequences:** `inSampleSize` decode to the display size
  (would cut the GL texture upload + the measured Graphics 68→99 MB bump — a
  `.PLAN.md` follow-up); the main-thread socket-I/O spike (~30% — follow-up);
  real-robot re-verify (synthetic source only); emulator gfxinfo is not
  comparable (Swiftshader software GPU). TextureView is ruled out for any
  future video surface unless the draw moves off `onDraw`.

### [2026-08-17] A.2 dual-network socket binding: injectable Wi-Fi provider seam

- **Type:** mixed — design-clarifying (S13: control and video sockets route over the Wi-Fi AP) with a problem-avoiding part (the cellular-default-network route loss).

- **Problem:** phones on the robot Wi-Fi with cellular data enabled lost the
  TCP/MJPEG connection — a bare `Socket()` routes over the *default* network
  (cellular; the robot AP has no internet, so the system keeps cellular
  default). Fix: bind sockets to `TRANSPORT_WIFI` before connect. Two
  sub-problems surfaced while implementing: (1) `NetworkCapabilities.Builder`
  is absent from every installed SDK stub jar
  (android-23/30/35/36/36.1), so Robolectric cannot fabricate capability
  fixtures; (2) the synchronous scan `ConnectivityManager.getAllNetworks()` is
  deprecated since API 33 — and the user rejected suppressing it even though
  the repo has a documented suppression registry (TESTING.md).

- **Alternatives considered:** (a) `allNetworks()` scan +
  `@Suppress("DEPRECATION")` (rejected by user — avoid the deprecated API, do
  not suppress); (b) fabricate `NetworkCapabilities` in tests to assert
  Wi-Fi-preference (impossible — Builder absent from SDK stubs, verified by
  javap on all installed android.jar); (c) non-deprecated async
  `requestNetwork`/`registerNetworkCallback` tracker with an injectable seam.

- **Chosen solution:** a `SocketBinder` fun interface (`identity` = no binding)

  - `WifiSocketBinder(context, wifiNetworkProvider: () -> Network? = WifiNetworkTracker(context)::current)`. The provider is the injectable seam:
    tests supply fixed `ShadowNetwork`s (bind path) or `null` (fallback). The
    production provider is a `ConnectivityManager.registerNetworkCallback`
    (`NetworkRequest` with `TRANSPORT_WIFI`; the 2-arg form — the `Handler`
    overload is API 26+) tracker caching the network in a `@Volatile` field,
    updated `onAvailable`/`onLost`. `bind()` calls `Network.bindSocket(socket)`
    (present in android-23.jar → minSdk-23-safe, verified by javap) inside a
    catch → default-network fallback. Applied at both `Socket()` sites
    (`SenderService.connectToTRIK`, `RawSocketHttpStream.open`); the https path
    stays on `HttpURLConnection` (default network — it cannot be bound) —
    superseded 2026-08-19 by "[2026-08-19] https video binds to the Wi-Fi
    network": the https video branch now routes via `WifiConnectionOpener`
    (`Network.openConnection` on the tracked Wi-Fi network + trust-all TLS).
    Manifest gains `ACCESS_NETWORK_STATE`. When no Wi-Fi network exists, fall
    back to the default network (user decision — preserves hotspot/cellular
    setups).

- **Why:** the seam unit-tests bind/fallback without `NetworkCapabilities`;
  `registerNetworkCallback` is the non-deprecated flow; default-network
  fallback keeps the app usable off the robot AP; `bindSocket` is minSdk-safe.

- **Out of scope / consequences:** the `IOException` catch branch stays
  uncovered (Robolectric's `bindSocket` never throws) — within the 0.95/0.85
  gate headroom (LINE 96.7% / BRANCH 85.1% measured). The first connect before
  the tracker's first `onAvailable` falls back; the next reconnect binds.
  On-robot verification (cellular + robot Wi-Fi) remains a manual user step.
  Lesson generalized: prefer avoiding a deprecated API over suppressing it,
  even when suppression is permitted.

### [2026-08-06] MJPEG: reconnect-on-error replaces the 30 s forced restart

- **Type:** problem-avoiding (the unconditional 30 s restart leaked sockets).

- **Problem:** the video loop force-restarted the HTTP stream every 30 s via a
  `postDelayed` runnable (`mRestartCallback`) regardless of whether the stream
  was healthy — a magic number that wasted bandwidth and reconnected a perfectly
  fine connection. The render thread already stopped itself on `IOException`,
  but the app never acted on that.

- **Alternatives considered:** keep the forced 30 s timer (wasteful, rejected);
  act on the render thread's `IOException` via an error listener.

- **Chosen solution:** `MjpegView` exposes an `OnStreamErrorListener` invoked
  from the render thread when `readMjpegFrame()` throws; `MainActivity`
  registers it in `onResume` and calls `restartVideoStream()` (marshalled to the
  main thread via `runOnUiThread`). The forced 30 s timer and `mRestartCallback`
  are gone — the stream restarts only when it breaks. `VideoStreamLoader` sets
  5 s connect/read timeouts so a dead robot surfaces as an error quickly.

- **Why:** the stream should restart only when it actually breaks; error-driven
  reconnect is simpler and bandwidth-free.

- **Out of scope / consequences:** no forced periodic restart. The render-thread
  unblock pattern (close the stream from another thread — `InputStream.read` is
  not interruptible) is a reusable leak fix (Campaign 3 P1).

### [2026-08-08] NSC cleartext scoping to the default robot host (Campaign 3 P3)

- **Type:** problem-avoiding (global cleartext was Play-flagged).

- **Problem:** `android:usesCleartextTraffic="true"` is global; targetSdk 36
  defaults cleartext OFF and Play flags global cleartext. The app's HTTP video
  stream needs cleartext to the robot.

- **Alternatives considered:** keep global cleartext (Play-flagged, rejected);
  scope cleartext via Network Security Config (chosen); bypass NSC entirely with
  a raw-socket client (the Campaign 5 follow-up).

- **Chosen solution:** `res/xml/network_security_config.xml` scoping cleartext
  to the default robot hotspot `192.168.77.1` (base-config off).
  `android:networkSecurityConfig` needs API 24+ → `tools:targetApi="n"` on the
  `<application>` tag (a `"m"`/23 value fails lint `UnusedAttribute`).

- **Why:** NSC is the sanctioned scoping mechanism; it whitelists only fixed
  hostnames/IPs, which fits the default hotspot.

- **Out of scope / consequences:** a user-configured **non-default** HTTP host
  is now blocked by NSC (TCP commands still work — `SenderService` uses raw
  sockets). The chosen fix for that is the raw-socket client (next decision).

### [2026-08-08] Campaign 5: raw-socket MJPEG HTTP client (chosen fix)

- **Type:** problem-avoiding (NSC scoping blocked non-default HTTP hosts).

- **Problem:** NSC cleartext scoping (above) only whitelists `192.168.77.1`.
  `VideoStreamLoader` uses `HttpURLConnection`, which honors NSC — so a user who
  configures a robot at a **non-`192.168.77.1` host over plain HTTP loses the
  video stream** (TCP commands still work).

- **Alternatives considered:** add every user host to the NSC domain-config
  (static XML, cannot cover arbitrary hosts); a toast on failure (interim only);
  a **raw-socket HTTP client** parallel to `SenderService` (chosen).

- **Chosen solution (user decision 2026-08-08):** open a `Socket`, write the
  GET request, parse the HTTP response head, and hand the body stream to
  `MjpegInputStream`. Bypasses NSC entirely, works for any user host, no
  manifest/config change. ~40 lines; re-implements the minimal HTTP framing
  (request line + headers, response `200 OK` + `Content-Length`/chunked).
  `MjpegInputStream` already parses `Content-Length` frames, so the client only
  needs the response head.

- **Why:** NSC is a static resource that can only whitelist fixed hosts; the
  raw-socket path is NSC-transparent (as `SenderService` already proves) and
  requires no per-host config.

- **Out of scope / consequences:** **Interim (before the fix):** on a
  stream-open failure for a non-default host, toast pointing at the host/NSC
  restriction instead of failing silently; record a known-limitation note in
  README only if it becomes user-visible. **Verification:** reuse
  `SyntheticMjpegServer` as the server side; assert the raw-socket client
  decodes frames from a custom host the NSC would block.

### [2026-08-08] ViewModel ownership + StateFlow connection state (Campaign 3 P2)

- **Type:** problem-avoiding (Activity-owned sockets crashed on config changes).

- **Problem:** `SenderService` was Activity-owned — a stray callback after
  `onDestroy` (`mSender!!`) could crash, and an Activity-owned socket is
  closed/reopened on every config change (rotation).

- **Alternatives considered:** a foreground Service (rejected — needs a declared
  type + permission and Play scrutiny; a gamepad connection has no natural FGS
  type); a ViewModel-owned connection.

- **Chosen solution:** `SenderViewModel` (Activity-scoped, `by viewModels()`)
  owns the `SenderService`; `onCleared()` closes the socket. `ConnectionState`
  sealed interface + `StateFlow` fed from the service's connect/disconnect
  paths; MainActivity collects with `repeatOnLifecycle(STARTED)` for the
  disconnect toast. Pref-listener `register`/`unregister` made idempotent +
  `private`.

- **Why:** a ViewModel survives rotation and is cleared deterministically via
  `onCleared()`; `StateFlow` + `repeatOnLifecycle` is the sanctioned,
  non-deprecated state collection pattern.

- **Out of scope / consequences:** **lint trap:** `repeatOnLifecycle` must be
  called from `onCreate`, not a lifecycle callback like `onStart`
  (`RepeatOnLifecycleWrongUsage` fails the build). Process death still kills the
  socket — settings live in SharedPreferences so re-derivation is free.

### [2026-08-10] Campaign 8 — bounded, control-gated video retry (MJPEG self-healing)

> **SUPERSEDED 2026-08-18** by "[2026-08-18] Scenario-driven video retry —
> control gate removed (Option B)". The control gate contradicted scenarios
> S2/S4/S6/S7/S14; the bounded retry itself (5 s tick, `!isPlaying` gate,
> socket-close on failure) survives as Option B. Kept as a dated record.

- **Type:** problem-avoiding (the anti-hammering gate; its "never touch a
  healthy stream" property survives, the control coupling does not).
- **Problem:** the original app's unconditional 30 s MJPEG restart was removed
  as the socket-leak driver (Campaign 3 P1) and replaced with
  `reconnect-on-error`, which only fires on a real `IOException`. The user-facing
  regression (found 2026-08-10 by comparing against upstream): a silently
  stalled / not-yet-reachable / surface-recreated stream leaves the video black
  until the user leaves and re-enters (robot out of wifi range and back, robot
  booting later, foldable hinge).
- **Alternatives considered:** (a) restore the unconditional 30 s restart —
  rejected: it was the socket-leak driver and blips healthy streams; (b) a
  video-side stall watchdog on the render thread — rejected: the thread blocks
  in `read()`; the socket `SO_TIMEOUT` already covers pure stalls, so the real
  gap is a failed open/reconnect that nobody retries; (c) **bounded retry gated
  on the TCP control-connection keepalive** (chosen).
- **Chosen solution:** `VideoRetryController` (injectable, main-thread
  `Handler` tick, 5 s interval) reloads the stream while activity resumed ∧
  control `connectionState is Connected` (the keepalive proxy — same
  robot/wifi) ∧ video configured ∧ `!view.isPlaying()`; immediate reload on the
  control-`Connected` edge (a pad touch reconnects control → instant video);
  cancelled on pause. `VideoStreamLoader.load(url, onResult)` makes a failed
  open observable; `MjpegView.isPlaying()` gates the ticks. Plus the video
  loading indicator (spinner shown on load/reconnect, hidden on the first
  decoded frame via `MjpegView.OnFirstFrameListener`).
- **Why:** `Connected` is a free reachability probe (keepalive already flows to
  the same robot), so retries never hammer a robot that is clearly down; the
  5 s tick + ≤5 s connect keeps the ≤10 s recovery budget; a healthy stream is
  never touched. TDD: failing tests reproduced the regression first.
- **Out of scope / consequences:** idle recovery is bounded by user interaction
  (control reconnects on the next pad touch) — accepted by design. The retry is
  control-gated, so it cannot recover an idle gamepad after a robot reboot until
  the user interacts. Coverage gate restored after the new branches (see
  TESTING.md measured numbers).

### [2026-08-10] Connection status pill + spinner gating (Campaign 10 follow-up — LANDED 2026-08-11)

> **PARTIALLY SUPERSEDED 2026-08-18** by "[2026-08-18] Scenario-driven video
> retry — control gate removed (Option B)": the spinner is now URL-gated
> (shown whenever a URL is configured), not `Connected`-gated. The pill
> behavior (tap-to-connect, hide-on-connected, orange) is unchanged.

- **Type:** mixed — design-clarifying (pill text/colors/z-order) with a
  problem-avoiding part (spinner honesty gating).
- **Problem:** the Campaign 10 status line is a small white-on-dark pill pinned
  above the gear, **always visible** in every connection state. Reviewing a
  screenshot, the maintainer found it cluttered: the state is already carried by
  the gear border (green/amber/red) and the live video, so a permanent pill on
  the pad area is redundant noise. Separately, the video-loading spinner can
  show even when nothing is loading (no stream URL, or control not yet
  connected), which is misleading.
- **Alternatives considered:** hide-on-connected only (text for Connecting +
  Disconnected, gone on Connected) vs always-visible (status quo); spinner gated
  on `Connected ∧ URL configured` vs hidden by timer vs left as-is; a text
  color reusing the existing `amber` border color vs a dedicated alert orange.
- **Chosen solution (user decision 2026-08-10):** a **centered orange pill**
  with the text `Tap to connect…` (text ≈5% of landscape height; orange on a
  dark rounded pill for contrast). `Connecting` → `Connecting…`, `Connected` →
  pill hidden, `Disconnected` → `Tap to connect…` and still tappable
  (`SenderService.connect()`). The loading spinner grows to ≈30% of landscape
  height and is shown **only while `Connected` and a stream URL is configured**
  (no spinner when not connected or no URL). The pill is drawn over the spinner
  center (z-order) but the two never co-display because of the gating.
- **Why:** hiding on `Connected` removes the redundant overlay the moment the
  state is obvious (gear + video); a centered orange pill is the clearest
  call-to-action for the one case that needs a tap; gating the spinner keeps it
  honest — a spinner implies loading, which the control-gated retry only does
  while connected and configured.
- **Out of scope / consequences:** implemented 2026-08-11 (commits, gate green
  ×2, instrumented 9/9 both emulators). Cleanups shipped as decided: dropped
  `ConnectionFeedback.addressProvider`, dropped the dead
  `SenderService.getHostPort()` (+ its test), deleted the unused
  `connection_status_connected` string, gated `showVideoLoading()` on
  `mVideoURL != null && connectionState is Connected`. `status_orange`
  (`#FF9800`) added; `connection_status_text_size` = 20sp;
  `video_loading_size` = 120dp. `Tap to connect` uses the real ellipsis
  character (`…`) — lint `TypographyEllipsis` rejects `...`.

### [2026-08-11] Empty-host video-only mode + connect-UX hardening (Campaign 12)

> **PARTIALLY SUPERSEDED 2026-08-18** by "[2026-08-18] Scenario-driven video
> retry — control gate removed (Option B)": the empty-host *gate relaxation*
> ("the retry gate relaxes for an empty host") is subsumed — Option B relaxes
> the gate for *every* host. The empty-host **mode** (pill/controls hide,
> connect no-op, placeholder default) is unchanged.

- **Type:** mixed — design-clarifying (video-only mode is a first-class
  scenario, S5) with a problem-avoiding part (stuck-Connecting state fix).
- **Problem:** the status pill and the pads/buttons made no sense with an empty
  robot IP: a "tap to connect" affordance with nothing to connect to, and touch
  surfaces for a robot that isn't configured. Reviewing the Campaign 10/11
  behavior, the maintainer flagged "it is stupid to show it if ip is empty" and
  asked what a video-only device (empty host + a video URL) should look like.
  Separately, a failed connect left the pill stuck at "Connecting…" forever
  (`connectToTRIK` never returned to `Disconnected` on `IOException`).
- **Alternatives considered:** reject an empty host in Settings (blocks the
  video-only use case — rejected for that reason); validate host format
  (over-validation — DNS names may work); hide the pill only vs also hide the
  controls; notify on video-stream failure per retry tick vs throttled.
- **Chosen solution (user decision 2026-08-11):** empty host is a **valid
  configuration = video streaming only**. The pill hides entirely
  (`ConnectionFeedback.targetConfiguredProvider`), the pads + magic-button row
  auto-hide regardless of the "Hide pads & buttons" toggle
  (`setControlsVisible(!hideControls && addr.isNotBlank())`), `connect()`
  no-ops on a blank host, and the empty-host video-URI default is `""`
  (placeholder) instead of the malformed `http://:8080/...`. A failed connect
  returns to `Disconnected("")` (empty reason → the existing "Connection to X
  error." Snackbar is the single notification). Video-stream failures surface a
  throttled (~15 s) "Video stream unavailable" Snackbar via a pure
  `VideoStreamErrorNotifier` while bounded retries continue — and the retry
  gate relaxes for an empty host (`shouldReloadVideo` allows retry without a
  control connection) so video-only still auto-recovers; the spinner stays
  `Connected`-gated.
- **Why:** an empty IP is a legitimate end-user configuration (watch-only
  device), so it must not be blocked or force a bogus connect; hiding the
  tappable affordances removes noise; the state machine fix makes the pill
  honest after a failed tap; the throttled Snackbar informs without spamming
  the 5 s retry loop.
- **Out of scope / consequences:** no new settings toggle (auto-hide is
  automatic); no host-format validation; the touch-injection race where a tap
  during the connect→Connected transition is dropped is a test-environment
  artifact (Espresso `click()` vs direct `performClick()`) — documented in
  MEMORY "Campaign 12 execution run", not fixed in the app.

### [2026-08-11] User-facing diagnostics & crash reporting (offline-first, Campaign 14)

- **Type:** mixed — design-clarifying (an in-app diagnostics feature) with a problem-avoiding part (privacy/egress scrutiny).

- **Problem:** when users hit an issue (UI renders wrong, a crash, a
  network/stream problem) they have to describe it to developers, and the
  developers have to reproduce it to fix it. The app's only diagnostic
  affordance was "About system", copying `Version; Android; SDK; Resolution; PPI` to the clipboard — no device model, no app settings snapshot, no
  connection state, no event trace, and no crash capture at all (a crash was
  only visible via adb on the device). The app targets **all stores** (Play,
  F-Droid, Galaxy, Amazon, AppGallery), which rules out Google-Play-only
  telemetry and requires F-Droid (source-built, privacy-sensitive,
  no-proprietary-SDK) compliance.

- **Alternatives considered:** **remote crash reporting (ACRA/Sentry)** —
  automatic off-device upload, but needs a server or SaaS, opt-in consent +
  a privacy policy (GDPR), is scrutinized by F-Droid for data egress, and
  adds ops load to a zero-infra indie app → **deferred**, not rejected
  (a configurable self-hosted endpoint could be added later); **direct
  `ACTION_SEND` share** — no review/edit step, users send raw dumps with no
  chance to redact or annotate → rejected for the review flow; **reading
  `logcat`** for the event trace — `READ_LOGS` is signature-granted, a normal
  app cannot read its own logcat on any modern Android → impossible; **an
  in-app copy-only report** — keeps the existing UX, but forces paste-into-
  mail and never sees the report file → extended with the editor flow.

- **Chosen solution (user decisions 2026-08-11):** **offline-first
  diagnostics**. (1) An `AppLog` facade mirrors every log call to logcat and
  into a **500-line thread-safe ring buffer**; the **buffer floor defaults to
  INFO** (WARN/ERROR always captured, VERBOSE never) and is user-tunable via
  a "Diagnostics verbosity" list — Errors only / Info (default) / Debug /
  Verbose — so the default report is clean (the per-command DEBUG trace is
  excluded) and a dev can ask a user to raise verbosity to capture the full
  command flow. (2) A markdown **diagnostic report** (one file = one data
  block) carries app version/versionCode/build type, device
  manufacturer/model/product, Android release/API, display resolution/density/
  font scale, locale, the live connection state, the full settings snapshot
  (non-defaults marked), robot presets, the log tail and the last crash trace.
  (3) The report is written to `cacheDir/diagnostics` and exposed via a
  **FileProvider** (`exported=false`, cache-path only); **"Report an issue"**
  opens it in a **text editor** for review/edit before the user shares from
  the editor's own share menu (mail/IM) — the chooser title is exactly
  "Choose a text editor to review and edit before sharing to developers", and
  a default-off **"Share logs without editing"** switch (with state-aware
  descriptions) skips the editor with a direct share sheet. "Copy report" and
  an in-app "View log" dialog complete the About rows; the About-system tap
  now copies the full report. (4) **Crash capture**: a chaining
  `UncaughtExceptionHandler` (installed by a new `App` Application) persists
  bounded crash stack traces; the next MainActivity launch shows a
  once-per-crash dialog (Review & share / Copy / Dismiss) honoring the
  share-without-editing switch.

- **Why:** zero new permissions (FileProvider + internal/cache storage need
  none; INTERNET already exists for the app's core function), **no silent data
  egress** — the user reviews and explicitly shares, so there is no consent
  layer and no privacy-policy burden, and F-Droid builds from source with
  nothing proprietary. The stacktrace + spec + settings + event trace is the
  standard reproduction payload (the app is a TCP client + MJPEG player, so
  the connect/keepalive/stream-error trace is what makes network bugs
  reproducible without a device). The editor step is the key UX decision:
  users review (redact a host, add "what I was doing") before sending, which
  improves report quality and trust. 500 lines at INFO with the keepalive
  heartbeat (~1 line / 4.7 s) ≈ 30–40 min of history; under a failure storm
  (~1–2 lines/s) it still retains ~4–8 min — enough context, small enough to
  mail/IM as an attachment.

- **Out of scope / consequences:** remote auto-reporting is deliberately
  deferred (server/consent/F-Droid scrutiny); the connection state is not
  reachable from SettingsFragment (the SenderService is activity-scoped), so
  the Settings-sourced report shows "not running (open the gamepad to capture
  the live state)" while the crash dialog carries the live state; Robolectric
  cannot resolve FileProvider path XML or query intent activities, so the
  sharer takes an injectable report URI and the FileProvider call is
  device-only (16 report lines stay uncovered, documented in MEMORY C14).

______________________________________________________________________

## Workflows

### [2026-08-05] Fork-only workflow (no upstream PRs)

- **Type:** design-clarifying (defines the collaboration model: the fork is the product home).

- **Problem:** the repo has two remotes — `origin` = the personal fork,
  `upstream` = `trikset/trik-gamepad` (canonical). A planned branch+PR flow
  needed a policy for upstream.

- **Alternatives considered:** create cross-repo PRs against upstream (rejected);
  sync/push to upstream (rejected).

- **Chosen solution:** all work lives on the fork only; PRs are created within
  the fork (`gh pr create --base master`, base = fork master). Never create
  cross-repo PRs against `trikset/trik-gamepad`; never sync/push to upstream.

- **Why:** the maintainer develops and merges on their own fork; upstream is a
  distribution point, not a collaboration target for this project.

- **Out of scope / consequences:** AGENTS.md "Before push — publishing gate"
  and MEMORY "Workflows" state the fork-only rule; future sessions must not
  propose upstream PRs.

______________________________________________________________________

## Process

### [2026-08-05] Docs culture adoption (from trik-lobe-server)

- **Type:** design-clarifying (defines the AGENTS/MEMORY/TESTING doc structure).

- **Problem:** the project lacked a documentation convention that scales across
  sessions and agents.

- **Alternatives considered:** keep a single combined docs file (rejected);
  adopt the trik-lobe-server split.

- **Chosen solution:** adopt the **AGENTS/MEMORY/TESTING split** (AGENTS.md =
  rules only, MEMORY.md = rationale, TESTING.md = strategy), the release-notes
  opencode skill, and uv-managed Python tooling. Culture rules imported:
  strict branch+PR discipline, Conventional Commits, Root cause/Profit/Trade-offs/Verification PR bodies, repo-root `.tmp/`, suppression policy,
  error-leaves-a-trace.

- **Why:** the split keeps the front door (AGENTS) small and points into the
  detail stores on demand (progressive disclosure); proven in the sibling repo.

- **Out of scope / consequences:** quality gates and the toolchain upgrade were
  initially postponed, then approved (see the Toolchain decision). Decisions
  live in this log; `.PLAN.md` (see AGENTS.md "Current work") holds only
  unfinished session tasks by design (see "[2026-08-09] Why .PLAN.md exists").

### [2026-08-06] Gradle pipeline hang: daemon inherits pipe handles

- **Type:** problem-avoiding (a Gradle daemon hung the caller through inherited pipe handles).

- **Problem:** a gradle run "finished in 12 s" but the agent command blocked
  until the 10-min timeout. Root cause: the invocation was a PowerShell pipeline
  `gradlew ... 2>&1 | Tee-Object ... | Select-Object -Last 20`. Gradle spawns a
  **daemon** (a long-lived JVM) that **inherits the parent shell's stdout/stderr
  pipe handles**. PowerShell pipelines wait for the whole pipeline to complete
  (EOF), and because the daemon keeps those handles open the pipeline never saw
  EOF — even though `gradlew.bat` had long since returned.

- **Alternatives considered:** keep piping (hangs, rejected); redirect to a file.

- **Chosen solution:** never pipe a long-lived child (Gradle, emulator, servers)
  through `Select-Object`/`Tee-Object`. Redirect to a file instead:
  `& gradlew <args> *> <log>` (or `Start-Process -Wait -RedirectStandardOutput <log>`),
  read the file afterward, use short timeouts for probes, and stop the daemon
  (`gradlew --stop`) or use `--no-daemon` for one-shot probes. Rule recorded in
  AGENTS.md "Operational rules".

- **Why:** the daemon inheriting pipe handles is inherent to the Gradle daemon;
  file redirection breaks the EOF dependency.

- **Out of scope / consequences:** also mis-guessed the google-java-format
  plugin coordinates once (verify plugin coordinates read-only before
  committing to them — Tooling assumptions guardrail).

### [2026-08-06] Operational rules for command hygiene

- **Type:** problem-avoiding (an async-poll stall; codifies command-timeout discipline).

- **Problem:** the agent stalled "staring at the emulator" — launched an async
  process, then polled it instead of advancing the work queue. Root cause:
  serialized the pipeline on an async resource (needed only later), and treated
  polling as progress.

- **Alternatives considered:** rely on ad-hoc discipline (failed); codify
  command-hygiene rules in AGENTS.md.

- **Chosen solution:** every command gets a reasonable timeout + a log tee
  (`app/build/<task>.log` or `.tmp/`); expected-vs-actual time is compared;
  ≥1.5× → analyze the wrong guess; slow commands → research + tune repeatable
  tooling + document quirks. Async tools: capture `Start-Process -PassThru`,
  verify liveness immediately, wait for the readiness signal with timeout, then
  continue independent work — never stall on a poll.

- **Why:** the stall was a cadence break, not a tooling failure; explicit rules
  make autonomous execution non-blocking.

- **Out of scope / consequences:** the rules live in AGENTS.md; this rationale
  lives here.

### [2026-08-06] Revival restructure (canonical layout, delete garbage)

- **Type:** design-clarifying (the canonical Android repo layout is the product's structure).

- **Problem:** the maintainer approved a full revival: canonical Android layout,
  quality gates, coverage to 85%, then pure-Kotlin migration; no release, no PR.

- **Alternatives considered:** keep the legacy single-module `as/` layout
  (rejected); restructure to `settings.gradle` + `app/`.

- **Chosen solution (R1–R15):** `settings.gradle` +
  `app/` module at repo root; delete `xamarin/`, `as/import-summary.txt`,
  Eclipse junk, `.local_development.db`; `imgs/` → `docs/img/`; retire CircleCI
  → GitHub Actions; conditional signing (keystore stays outside the workdir);
  toolchain Gradle 9.5.0 / AGP 9.3.1 / built-in Kotlin / SDK 36 / minSdk 23;
  coverage gate starts 60% and ratchets to 85% before Kotlin migration; MJPEG
  reconnect-on-error replaces the 30 s forced restart.

- **Why:** the canonical layout is the maintainable, tooling-supported shape;
  the deleted trees were unfinished/dead.

- **Out of scope / consequences:** all gradle commands run from the repo root;
  the restructure was a pure `git mv` so history is preserved.

### [2026-08-07] Autonomous-run stall root cause (turn cadence)

- **Type:** problem-avoiding (a turn-cadence breach stalled an autonomous run).

- **Problem:** during the global-refresh execution a swiftshader emulator was
  launched with `Start-Process -PassThru`; the agent ended its turn on the
  liveness check ("PID alive") without issuing the readiness poll. In an
  autonomous run nothing re-pokes the agent, so the run sat idle indefinitely.
  Adjacent failures the same day: a push shipped a google-java-format violation
  (only compile + instrumented tests were run, not `spotlessCheck`), and a
  GitHub Partial System Outage silently dropped the push events for three
  commits (push events during an outage are not backfilled).

- **Alternatives considered:** rely on the pre-existing "capture handle, verify
  liveness, wait for readiness" rule (it lacked the turn-completion
  constraint); codify three explicit rules.

- **Chosen solution:** three new AGENTS.md operational rules — (1) *async
  turn-cadence invariant*: a turn that launches an async process is not complete
  until the readiness result is recorded; the next call after the liveness check
  must be a single bounded poll, or the process is logged as a `poll:`/`watch:`
  todo for the next turn; (2) *pre-push full gate*: every push runs the complete
  logged gate list, not just compile + tests; (3) *CI cadence*: one bounded run
  check per push, document-and-continue if absent, re-check at the next push.

- **Why:** the stall was a cadence break — the rule lacked the explicit *a turn
  is not complete until readiness is recorded* constraint that autonomous
  execution requires.

- **Out of scope / consequences:** a swiftshader AVD (`Swiftshader_API36`,
  config==CLI so it never fights the AVD config) was created to validate
  focus-sensitive changes locally without burning CI runs.

### [2026-08-07] Uncommitted-fix trap: local gates green, CI compile red

- **Type:** problem-avoiding (local gates validated an uncommitted working tree).

- **Problem:** after migrating MainActivity to Kotlin, a `fun interface` fix on
  `SenderService.OnEventListener` was made in the working tree but never staged —
  every commit used `git add <specific files>`, so the change rode along in the
  working tree while the commits lacked it. Local gates passed (they validate
  the *working tree*, which had the fix), but CI `compileDebugKotlin` failed on
  every pushed commit.

- **Alternatives considered:** rely on `git add <specific files>` discipline
  (failed); verify `git status --short` is clean before push.

- **Chosen solution:** new AGENTS.md rule — verify `git status --short` is clean
  before `git push`; review `git diff HEAD --stat` before push; prefer staging
  the whole intended set and reviewing the staged diff.

- **Why:** local gates validating an uncommitted working tree are meaningless
  for the pushed state; the working tree and the commit must be the same.

- **Out of scope / consequences:** the same class of issue had shipped a
  spotless violation earlier the same session — the rule closes it for good.

### [2026-08-08] Auto mode contract (rationale for the AGENTS.md rule)

- **Type:** design-clarifying (defines what autonomous execution is and is not).

- **Problem:** the maintainer asked what in their prompt drove high-quality
  autonomous execution, then instructed that "auto mode" be codified in AGENTS.md.

- **Alternatives considered:** leave auto mode implicit (rejected); codify a
  four-point contract.

- **Chosen solution:** AGENTS.md's "run auto" guardrail expands into a
  four-point auto-mode contract: (1) work inside the stated container without
  ceremony, committing/pushing per the gate rules as you go; (2) apply
  documented traps and hooks from AGENTS/MEMORY/TESTING before acting and probe
  tooling read-only — don't stall on a command call; (3) research the best
  solution (web/code) before deciding, and if still unsure after experiments,
  think hard and postpone rather than guess; (4) implement only proved,
  reasonable decisions.

- **Why:** (1) autonomy + a defined container removes decision overhead; (2) the
  maintainer pre-loads the exact failure modes — apply them, don't rediscover;
  (3) permission to research first and postpone honestly prevents forcing a
  result; (4) the evidence-over-guesses bar is what makes big-scope mandates
  safe to grant.

- **Out of scope / consequences:** future "go full auto mode" instructions carry
  this contract without re-explaining it; rationale lives here, rule text lives
  in AGENTS.md.

### [2026-08-09] Why .PLAN.md exists (by design)

- **Type:** design-clarifying (defines the plan-file design: unfinished tasks only).

- **Problem:** session execution state (what is in flight, what is left) is
  lost when a session crashes; committed docs (ROADMAP) hold *plans*, not
  transient session state, and committing that churn pollutes git history.

- **Alternatives considered:** commit session state to ROADMAP (history noise;
  completed work would linger); rely on chat memory (ephemeral — see AGENTS.md
  "Session context is ephemeral"); a gitignored working plan file.

- **Chosen solution:** a gitignored `.PLAN.md` at the repo root that holds
  **only unfinished/in-flight tasks** for crash-safety. Completed work is
  trimmed from it by design — its durable homes are the MEMORY.md retrospectives
  and git history. It stores no decisions and no rationale (those belong in
  this log) and is never committed.

- **Why:** a fresh session recovers from a short, accurate pending list instead
  of wading through completed-work history; nothing transient pollutes the
  committed docs; every completed item already has a durable home (MEMORY
  retrospectives, DECISIONS entries, git log).

- **Out of scope / consequences:** `.PLAN.md` is referenced only here (why/what
  it is for) and in AGENTS.md "Current work" (what is in it / when to read it);
  all other docs deliberately carry no `.PLAN.md` references.

### [2026-08-15] Docs-discipline rules (scoped storage, per-doc drift audit, plan-trim-after-push)

- **Type:** design-clarifying (defines where knowledge lives and how drift is audited).

- **Problem:** session instructions and drift fixes repeatedly landed in the
  first doc at hand, and completed campaign entries lingered in `.PLAN.md` even
  after push (the "Campaign 19 — COMPLETE" history section). Each is drift:
  knowledge in the wrong scope, or a local plan holding published work.

- **Alternatives considered:** keep the rules only in chat memory (ephemeral —
  rejected); add them all to AGENTS.md verbatim (bloats the agent-centric file);
  leave `.PLAN.md` history sections as-is (contradicts its "only unfinished
  tasks" design, see "Why .PLAN.md exists").

- **Chosen solution:** four compact AGENTS.md guardrails (light, agent-centric,
  no rationale) + one retrospective-hook line: (1) store knowledge in the doc
  whose scope matches it; (2) docs-drift audit checks each doc against its own
  scope; (3) missed knowledge is proposed at the next review, escalating to a
  better home if it doesn't stick; (4) `.PLAN.md` holds only unfinished tasks —
  completed/published work leaves it after push; plus "when something looks
  similar, generalize" for retrospectives. Rationale lives here and in
  DECISIONS/MEMORY, not AGENTS.

- **Why:** AGENTS.md stays a small agent rules/triggers surface; the per-doc
  scope check catches both stale claims and misplaced detail; trimming the plan
  after push keeps a crash-recovery file short and accurate (its original
  purpose); generalizing avoids re-recording each similar finding.

- **Out of scope:** moving existing campaign history out of MEMORY/ROADMAP
  (those are the durable homes); rewriting dated campaign records that quote
  then-current code names (history is kept as-is per "Docs store experience,
  not state").

## UX & accessibility & i18n

### [2026-08-18] Haptic schema: generic legacy constants, strong pulses, no VIBRATE

- **Type:** design-clarifying (the haptic schema is product behavior; the generic-constants rule is a design constraint).

- **Problem:** after C24/A.6 switched every haptic call site to `KEYBOARD_TAP`,
  the user reported "no haptic feedback on my Galaxy. At all" — a regression
  from the per-move buzzing they had felt before. The fix needed a deliberate
  schema, and an earlier claim that a missing `VIBRATE` permission was the
  cause turned out to be wrong (see Why).

- **Alternatives considered:**

  - *VIBRATE-permission fix* — add `android.permission.VIBRATE` to the manifest
    and call `Vibrator.vibrate`. Rejected: `performHapticFeedback` provably
    works without the permission (AOSP `VibratorManagerService.performHapticFeedback`
    → `vibrateWithoutPermissionCheck`; the S25's own vibration history showed
    the app's `performHapticFeedback(constant=3)` playing without it), and
    `Vibrator.vibrate` would bypass the system haptics toggle.
  - *API-30 constants* (`CONTEXT_CLICK`, `CONFIRM`, `REJECT`, `EFFECT_*`) —
    tried first (e.g. TICK→CONTEXT_CLICK, CLICK→VIRTUAL_KEY, CONNECT→CONFIRM,
    with an SDK-30 branch for the \<30 fallback). Rejected on the user's
    direction: code must use **widely supported generic constants** —
    `KEYBOARD_TAP` / `VIRTUAL_KEY` / `LONG_PRESS` — available on every API
    level and OEM HAL, with no SDK branching. (The `EFFECT_*` constants are
    also `@hide` in the public SDK stub.)
  - *Per-move / continuous feedback* — rejected (C24: "very annoying and
    laggy", queued after finger lift).
  - *Three-pulse disconnect* (`TICK,TICK,CLICK`) — rejected on user direction:
    **two strong pulses, 200 ms between starts** is enough.

- **Chosen solution:** one `Haptics` helper mapping semantic levels to the
  generic trio — TICK→`KEYBOARD_TAP` (pad thumb down), CLICK→`VIRTUAL_KEY`
  (pad up + connect), HEAVY→`LONG_PRESS` (magic buttons / gear / chip = one
  strong; disconnect = two strong pulses 200 ms apart, scheduled by
  `RejectHaptic`). All via `View.performHapticFeedback` (no flags → respects
  system + view settings). No manifest change.

- **Why:** `LONG_PRESS` resolves to `EFFECT_HEAVY_CLICK` — the strongest
  effect the S25 advertises (`supportedEffects = [CLICK, DOUBLE_CLICK, TICK, HEAVY_CLICK]`) — so "strong" needs no permission and no newer constants.
  The old "nothing felt" was not a dead motor: the A.6 build's single
  `KEYBOARD_TAP` on release *played* (`Prebaked=CLICK(MEDIUM)`) but one subtle
  tick at finger-lift read as silence after the prior per-move buzzing; the
  added thumb-down tick is what makes pads noticeable again.

- **Out of scope / consequences:** no amplitude control (framework intensity
  only — `TOUCH=MEDIUM` on the S25); no per-device tuning. Device-verified
  (S25, `-s <serial>`): pad TICK/CLICK play as `SemHaptic 50065/50038`,
  the two-pulse disconnect played as constants 6,6 with ~72/78 ms real gaps.
  Caveat: on the S25 `KEYBOARD_TAP` and `CONFIRM` both resolve to
  `SemHaptic 50025`, so distinctness comes from *when* the pulse fires, not a
  unique effect per event.

### [2026-08-11] Campaign 15: UX & accessibility scope (a11y, WCAG, i18n, theme)

- **Type:** design-clarifying (the UX & accessibility surface is the product).

- **Problem:** the settings/HUD/connection surfaces had known UX gaps — hardcoded
  user-visible strings, color-only connection state (invisible to screen readers),
  sub-48dp touch targets, no system theme/language support, and no WCAG
  guardrails — plus an approved settings-refinement plan that needed absorbing
  into a full UX & accessibility campaign.

- **Alternatives considered:** a11y depth (TalkBack-only vs +basic keyboard focus
  vs full Switch Access — chose TalkBack + basic focus; Switch Access out of
  scope); light-theme scope (HUD stays dark-over-video vs full-app light — chose
  dark HUD, light Settings/dialogs); campaign structure (keep the settings plan
  separate vs absorb — absorbed as one Campaign 15); feedback-driven review
  sweep (Play/F-Droid reviews + C14 reports — dropped: no access from the dev
  environment); translation sign-off (maintainer eyes all four locales vs
  back-translation only — RU gets a native-speaker human review, fr/de/vi are
  machine-drafted + machine back-translation verified); sync-guard placement
  (pre-commit vs canonical gate — gate only); back-translation enforcement
  (recurring pre-push gate vs one-off — one-off, literals change rarely);
  video stall detection ("no video signal" badge — included only if cheap, then
  skipped, see Why).

- **Chosen solution:** one commit-per-concern campaign: settings refinements
  (consolidated magic-symbols dialog with pre-filled defaults, current-value
  summaries, ellipsis on input rows, About copies the short spec only, string
  externalization); accessibility (deduped, target-gated
  announceForAccessibility state announcements, state-aware gear
  contentDescription, glyph-aware magic-button descriptions, 48dp touch
  targets, ripple, contrast fixes, importantForAccessibility hygiene);
  WCAG regression tests (contrast ratios + touch targets read the live
  resources); connection pill shows the target (Connecting to host:port:)
  and a reconnect badge distinguishes reloads of a previously-playing stream;
  localization (en+ru+fr+de+vi, @android:string/\* reuse for exact matches,
  Android 13+ localeConfig, gate-wired check_translations.py --sync parity
  guard, one-off MyMemory back-translation review); DayNight theme with a
  deliberately dark HUD. All conventions recorded in the new DESIGN.md.

- **Why:** Android/Material/WCAG guidelines plus the codebase inventory showed
  concrete, verifiable gaps; "every value-bearing setting shows its value" and
  "state is never color-only" fix real usability/a11y defects; the deterministic
  --sync guard (scripting) beats manual drift review; @android:string/\*
  reuse gives free per-device OS localization. Stall detection was skipped
  because a robot with video disabled legitimately keeps the spinner cycling —
  a "no video signal" badge would misreport that designed state.

- **Out of scope / consequences:** full Switch Access, keyboard/D-pad fallback
  for the raw-touch pads, first-run onboarding (ROADMAP deferred item F), a
  light-mode HUD, and video stall detection. Coverage gate re-verified
  (LINE 0.975 / BRANCH 0.866); instrumented suite 9/9 on both API-36 emulators;
  translations sync guard runs in the canonical gate.

### [2026-08-12] Type 1 HUD theme + app/robot settings split (Campaign 17)

- **Type:** design-clarifying (the HUD look and settings split are product design).

- **Problem:** the gamepad HUD still used the original 2016-era visuals (a
  persistent greendark **action bar** displaying the robot IP, square buttons,
  thin pad strokes), and one settings screen mixed robot-target config with
  app-behavior config. Users run the app on a phone as a Wi-Fi gamepad; the HUD
  is the whole product surface.

- **Alternatives considered:** three HUD directions (glass/arcade, cute/playful,
  minimal-modern) prototyped in v0.dev; the user's own mockup became the
  reference. Settings split: two activities (chosen) vs one activity with two
  roots. Chip content: host vs host:port (chosen host); tap-to-copy vs
  tap-to-open-robot-settings (chosen the latter). Tone semantics: sepia for
  idle/standby, red for real errors (chosen over "all disconnects red" — a clean
  pause is not a failure).

- **Chosen solution:** a themed, XML-first **Type 1 HUD** (`hud_*` drawables,
  `Hud.*` styles, one tintable pad-chrome vector) driven by a pure
  `ConnectionIndicator` accent mapper (green/amber/sepia/red); the action bar is
  removed and the IP moves to a glass chip (top-left) that opens the robot
  settings. Settings split into `SettingsActivity` (app) + `RobotSettingsActivity`
  (robot/target), both via one parameterized `SettingsFragment`, cross-linked.
  **XML-first rule: everything static goes to resources; Kotlin holds only the
  runtime accent tint.** Rationale for the code-vs-XML split: Android resources
  resolve at inflation and `ColorStateList` selectors key on a fixed framework
  state set — a four-value app-defined connection state has no XML hook, so the
  tint (Drawable.setTint / setTextColor / GradientDrawable.setStroke) must be
  imperative; a pure mapper keeps it testable and theme-switchable.

- **Out of scope / consequences:** real backdrop blur (RenderEffect, API 31+) —
  translucent "fake glass" chosen for minSdk 23 + cost; joystick spring-back for
  the left pad and left-handed pad swap (deferred → ROADMAP); the dual-network
  socket-binding fix for cellular+Wi-Fi connection loss (deferred → .PLAN.md);
  first-run onboarding still deferred. Verification: canonical gate green
  (LINE/BRANCH threshold intact, jscpd 0, translations sync 118 keys), 3-variant
  test suite green, screenshots verified by pixel-census + hash-match.

### [2026-08-12] Pad render + layout (Campaign 18)

- **Type:** design-clarifying (the pads are the primary control surface).

- **Problem:** after the C17 restyle the pads were "barely visible" — and the C17
  "verified" screenshot (byte-identical hash with a fresh capture) proved the
  pad chrome had **never** rendered: the pads showed only their glass border and
  a ~15%-opacity wash. Two independent bugs were hiding behind each other:
  (1) `SquareTouchPadLayout.onMeasure` computed a square `setMeasuredDimension`
  but never measured its children, so the C17 chrome/glyph ImageViews collapsed
  to 0×0; (2) `animatePadsAlpha` ran an `AlphaAnimation` with `fillAfter` on the
  same views `applyHudTone` set `.alpha` on directly, so the two alpha channels
  multiplied (~0.392² ≈ 0.154 effective opacity). A long-session-degraded
  emulator (everything black, gear included) initially masked both.

- **Alternatives considered:** emulator degradation as sole cause (ruled out:
  the C17 proof screenshot was byte-identical to a cold-boot capture, so the
  chrome was never rendered); SRC_IN colorFilter as culprit (tested and ruled
  out: the filter was not the problem, the 0×0 layout was).

- **Chosen solution:** fix `onMeasure` to measure children via
  `super.onMeasure(squareSpec, squareSpec)` after the square size (chrome/glyph
  render); make `applyHudTone` the **single alpha authority** (drop the
  competing `AlphaAnimation`), keeping the C17 "at most N%" dim rule; recenter
  the pads as ~260dp squares in two `weight=1` gravity-centered half containers
  (25%/75% width, vertically centered over the video), re-bringing the buttons/
  gear/chip to front so they stay tappable above the full-screen pads overlay;
  match the mockup visuals (dashed outer ring drawn in code — vector drawables
  cannot express dashes; solid inner ring, full crosshair lines and edge arrows
  in the tintable vector; a radial-gradient joystick knob with glow + center
  dot in `onDraw`; 2dp glass border + soft outer glow); compact the robot-target
  chip (28dp min-height, 8/4dp padding, 8dp margin) while **keeping 14sp text**.
  Also fixed the C17-introduced `SettingsTests` back-navigation regression (the
  tests still did two `pressBack()`s after the settings split flattened the
  flow to one level).

- **Why:** the pads are the primary control surface — an empty glass panel is
  not a usable gamepad; and the chrome is what signals the connection state.
  The compact chip keeps the HUD legible over the video.

- **Out of scope / consequences:** the sub-48dp chip touch target is an
  **accepted deviation** (the chip is a read-only status row opening the robot
  settings; a full 48dp target dominated the video corner) — documented in
  DESIGN.md "Robot-target chip"; the dash-pattern constraint (no dashes in
  vector drawables) pushed the outer ring into `onDraw`, so the dash effect is
  sized in `onSizeChanged` (lint `DrawAllocation` forbids per-frame allocation).
  Verification: canonical gate green, 3-variant `test --rerun-tasks` green,
  instrumented 9/9 on both emulators (after the SettingsTests fix), screenshots
  verified by pixel-census (chrome/knob/ring present in sepia idle; gear + chip
  render) and hash-matched against fresh captures.

### [2026-08-17] Emulator launch: run_bounded-wrapped detached Start-Process

- **Type:** problem-avoiding (a third shape of the caller-blocking trap).

- **Problem:** launching the emulator from the tool is a third shape of the
  same caller-blocking trap as Gradle daemons. A raw `Start-Process` emulator
  launch is unbounded, and a turn ending on its bare liveness check repeats the
  documented cadence breach (the readiness poll lands in a later turn and can
  block the user). Running the emulator as run_bounded's wrapped process is
  wrong too — its `taskkill /T /F` on timeout kills a *healthy* emulator.

- **Alternatives considered:** raw `Start-Process` (unbounded, cadence trap —
  rejected); run_bounded wrapping the emulator directly with a long timeout
  (kills a healthy emulator at timeout — rejected); wrap only a launcher that
  detaches the emulator, and do the readiness poll as a separate bounded call.

- **Chosen solution:** a `.tmp/launch_emulator.ps1` that runs
  `Start-Process -PassThru -RedirectStandardOutput/-RedirectStandardError`
  (fully detached), prints `PID=` + `HasExited=` after ~2 s, and exits; that
  launcher runs **through** `run_bounded --timeout 180`. The emulator itself
  stays detached and alive; run_bounded only bounds the launcher (returns in
  ~3 s, so its timeout never fires and its tree-kill only ever catches a hung
  launch). The readiness poll is a separate `wait_boot.ps1` (loop on
  `getprop sys.boot_completed`) also under run_bounded, in the same turn.

- **Why:** measured — launcher returned in 3.5 s / exit 0 (no boot block), and
  the boot wait completed in ~15 s in the same turn. Both constraints hold:
  the emulator survives past the launch command, and every native command is
  bounded + tree-killable. This is the same pattern as the `Start-Process`
  servers already documented, but the launch itself is now bounded too.

- **Out of scope / consequences:** a `.tmp/wait_boot.ps1` is needed (never
  inline a PowerShell `$var` loop through `run_bounded` — nested quoting
  mangles `$b`/`$i`); the emulator serial may drift after kills (was
  `emulator-5556`, is `emulator-5554` now — always read `adb devices`).
  **C23 addendum (2026-08-17):** the pattern's ~3.5 s-return promise did NOT
  hold once — the launcher's output never flushed, the bash-tool timeout killed
  the wrapper chain, and the detached emulator still booted (51 s cold). Root
  cause not pinned (hypothesis: `2>&1 | Out-String`-piped native output +
  detached-child handle inheritance under PS 5.1). Consequence: a hung wrapper
  must never be treated as a failed launch — verify liveness independently
  (`adb devices` + boot poll) before any kill/relaunch (AGENTS.md
  emulator-launch rule extended; re-audit the invocation before the next
  launch).

### [2026-08-14] Timeout-bound tooling: process-TREE kill + host adb shim

- **Type:** problem-avoiding (a hung adb call blocked the caller ~15 h).

- **Problem:** an `adb install` during an emulator offline blip hung the caller
  ~15 h. Killing only the DIRECT process leaves children (cmd wrappers, gradle
  daemons, adb clients) holding the inherited output pipe, so the tool
  "times out" but the caller's pipe never sees EOF and the turn blocks forever.

- **Alternatives:** rely on the bash-tool `timeout` alone (proved insufficient
  — it kills the shell parent, not the tree); PATH-shim gradle (impossible —
  repo-root `gradlew.bat` wins via cwd lookup); PATH-shim adb (works).

- **Chosen solution:** `scripts/run_bounded.py` (cross-platform, kills the
  process TREE: `taskkill /T /F` on Windows, `killpg(SIGKILL)` via
  `start_new_session` on POSIX; exit 124 + TIMEOUT marker). `_gradle.call_gradle`
  and `gate.py` non-gradle steps route through it. A host-local `adb.bat` shim
  (`~/.local/bin`, first on PATH) forwards every adb through it. All committed
  except the shim (machine-local per AGENTS.md).

- **Why:** a hang must fail the gate fast with a readable marker, never block
  the caller; adb interception is the only external force-multiplier.

- **Out of scope:** POSIX adb shim (re-audit on the first POSIX box).

### [2026-08-15] Inset-aware HUD container

- **Type:** design-clarifying (edge-pinned controls must clear OS chrome; the HUD layout is product design).

- **Problem:** on a physical phone (Samsung, landscape), `SettingsTests` failed
  with the Settings screen never opening: the tap on `targetChip` (top-left,
  7dp margin) was delivered to systemui's notification SHADE, not the app —
  the chip sat inside the phone's top `mandatorySystemGestures` strip
  (~26dp), which owns touches for the status-bar/shade swipe. The emulator
  has no such overlay, so it passed there; the same flake would hit a real
  user tapping the chip. The HUD's edge-pinned controls (chip top-left, gear
  bottom-left, magic buttons bottom-center) sat inside the OS chrome.

- **Alternatives:** change the test to direct `performClick()` (rejected — the
  test caught a real UI defect and should stay as the pass criterion); a
  full "show the system bars" redesign (rejected — loses full-bleed video +
  gamepad feel); inset-aware HUD container (chosen).

- **Chosen solution:** wrap the three edge-pinned controls in a full-screen
  `@+id/hudControls` RelativeLayout and pad it per edge from
  `WindowInsetsCompat` (`systemBars` | `displayCutout` | `systemGestures` |
  `mandatorySystemGestures`) in `MainActivity.onCreate`. RelativeLayout
  applies parent padding before `alignParent*`, so the child relations
  (buttons→btnSettings→targetChip) survive. The video stays full-bleed (a
  sibling below the container); center pills stay in `main`.

- **Why:** the standard edge-to-edge + gesture-nav recipe; clears the
  shade/status strip, cutout and nav zones on every device in both immersive
  and transient-bars states. Robolectric dispatches no insets → padding 0 →
  layout unchanged for unit tests.

- **Out of scope / consequences:** a Robolectric structural test
  (`hudControlsPaddingShouldFollowWindowInsets`) dispatches a known compat
  insets frame and asserts the container adopts it per edge. The unchanged
  `SettingsTests` becomes the on-device verification (deferred — the phone
  dropped off adb after the change; recorded in `.PLAN.md`).

### [2026-08-15] Magic-button glyph centering: asymmetric padding, not view translation

- **Type:** design-clarifying (how the magic-button glyphs render is product visual design).

- **Problem:** the round magic buttons were clipped at the top by the cluster
  pill, and the glyph read as centered to the pill, not the button circle.
  `MagicButtonPanel.centerGlyph()` applied `btn.translationX/Y` to center the
  glyph's ink box — but translation moves the WHOLE view, so each button's
  circular background shifted up (~6.5px) inside the cluster; the cluster's
  default `clipToPadding=true` then cut the circle's top arc flat (torn tops
  on phone + emulator screenshots, 2026-08-15).

- **Alternatives:** `clipToPadding=false` only (fixes the clip but the circle
  stays off-center — the glyph then reads as pill-centered, which the user
  reported); re-center via the row's padding (impossible — one row padding,
  but each glyph has a DIFFERENT ink offset); asymmetric padding per button
  (chosen).

- **Chosen solution:** `centerGlyph()` converts the ink offset into asymmetric
  `setPadding` on each button (`paddingTop/Bottom = max(0, ∓2·offsetY)`, same
  for start/end) and keeps `translationX/Y` at 0, so the circular background
  stays centered in the cluster while only the glyph ink moves. The cluster
  keeps `clipToPadding="false"` (defensive) and its vertical padding drops
  6dp→2dp so the pill hugs the 48dp buttons (pill 60dp→52dp). Button size is
  NOT changed — measured equal to the settings button (48dp) already.

- **Why:** padding changes only the content box (TextView gravity still centers
  the line box inside it), leaving the full-view background untouched; the
  padding math is the exact inverse of the old translation
  (`padTop − padBottom + 2·offsetY = 0`). Per-button padding centers each
  circle independently — which a single row padding cannot.

- **Out of scope / consequences:** the glyph can no longer overflow its button
  (irrelevant — ink ≈46px in a 132px circle). Regression tests: every magic
  button asserts `translationX/Y == 0`; the cluster test asserts the drawn
  circle top/bottom land on `row.paddingTop` / `row.height − paddingBottom`.

### [2026-08-15] Device identifiers never enter repo content

- **Type:** problem-avoiding (private identifiers must never leak into history).

- **Problem:** a physical phone connected for local instrumented verification
  has an adb serial, model name and IMEI that can identify the exact person
  or device. Committing any of them (or derived values like the
  "<model> - 16" test-result filenames) leaks a private identifier into
  history/CI.

- **Alternatives:** scrub at push time only (error-prone — the leak is already
  in history); a committed rule + pre-commit sweep (chosen).

- **Chosen solution:** AGENTS.md "Repo hygiene" guardrail — serial/model/IMEI
  are session-only (scoped via `ANDROID_SERIAL` on the command line), never
  in committed docs/config/tests; sweep `git diff`/`git add` output before
  committing (instrumented-test artifacts live under gitignored `app/build/`
  but still get swept). Serial stays out of `.PLAN.md` pushes too (it is
  gitignored but the rule is universal).

- **Why:** an exact-person/device identifier is private data; a leak is
  irreversible once pushed (this repo verified: 521 commits contain none).

- **Out of scope:** sanitizing vendor device logs or upstream data.

### [2026-08-20] Device-identifier scrub on push-prep

- **Type:** problem-avoiding (enforces the 2026-08-15 guardrail; prevents a
  private identifier from reaching a published remote).

- **Problem:** the guardrail existed since 2026-08-15, yet a physical device's
  serial and model code got committed into docs during
  the E2/E3 campaign (introduced by the oldest unpushed commit). A push-prep
  audit found them; a plain push would have published them — the second
  violation of the rule.

- **Alternatives considered:** (a) leave them (leaks into published history);
  (b) rewrite ALL branch history including already-published commits (heavy,
  rewrites ~80 published commits + force-push); (c) rewrite only the unpushed
  commits so the pushed history is clean + a scrub commit on top for the
  carried published line (chosen); (d) scrub bare `S25` references too
  (rejected by user 2026-08-20 — only serial + model-code-shaped strings are
  identifying).

- **Chosen solution:** (1) amend the introducing unpushed commit
  (`2c02f64` → `ce7365e`) to scrub the serial + model, then rebase the 7 newer
  unpushed commits on top — the pushed branch contains zero device
  identifiers in its entire history; (2) a separate scrub commit (`a8ebdf0`)
  removes the one carried model reference that came from already-published history;
  (3) push with `--force-with-lease` to the fork branch. Published history is
  NOT rewritten (user decision): the `20e0085` blob still carries one
  model reference; the tip is clean.

- **Why:** the guardrail is absolute (an exact-person/device identifier is
  private data); unpushed commits are freely rewritable, so the pushed
  history could be made clean at zero cost to anyone else; rewriting published
  history is heavy and was explicitly not wanted by the user.

- **Out of scope / consequences:** the 2026-08-15 decision's "pre-commit
  sweep" was not enforced mechanically — this entry records the resulting
  AGENTS.md pre-push scan step (manual, gaps-escalate step 2; a pre-commit
  hook is a future candidate). Bare `S25` stays in the haptics records per
  user decision.

### [2026-08-14] HUD error pill replaces the Material Snackbar

- **Type:** design-clarifying (the error surface is part of the HUD design).

- **Problem:** the connection-error Snackbar rendered the Material default grey
  bar OVER the magic-button row, and Material styling + WRAP_CONTENT sizing is
  an unsupported, version-fragile trick.

- **Alternatives:** keep Snackbar + anchor above buttons (look still clashes);
  Snackbar WRAP_CONTENT hack (fragile); custom glass pill (fits content by
  construction).

- **Chosen solution:** a `connectionError` TextView (`Hud.GlassPill`,
  wrap_content -> always fits content), fade-in + auto-dismiss (3.5 s),
  positioned at the pill\<->buttons vertical midpoint.

- **Why:** XML-first HUD-native element, no library dependency, always fits.

- **Out of scope:** animation beyond a simple alpha fade.

### [2026-08-14] Drop the `material` dependency

- **Type:** problem-avoiding (an unused dependency dragged a large transitive tree).

- **Problem:** `com.google.android.material` was pulled in ONLY for the
  Snackbar. With the error pill replacing it, material had zero consumers and
  dragged a large transitive tree (appcompat/activity/fragment/recyclerview).

- **Chosen solution:** remove `material` from `gradle/libs.versions.toml` +
  `app/build.gradle`; verified gone from `debugRuntimeClasspath`.

- **Out of scope:** full AppCompat drop — androidx.preference still requires it
  (see ROADMAP "Deferred — drop AppCompat").

### [2026-08-14] RobotChipController extraction (detekt TooManyFunctions)

- **Type:** problem-avoiding (a lint gate pushed MainActivity over the function-count threshold).

- **Problem:** the chip feature pushed MainActivity to 36 functions vs the
  detekt class threshold of 31.

- **Alternatives:** raise the threshold (hides growth); extract the chip logic.

- **Chosen solution:** new `RobotChipController` owns the host text, both status
  glyphs and the chip contentDescription; MainActivity ~28 functions, directly
  testable. Matches the repo pattern (MagicButtonPanel, ConnectionFeedback).

- **Out of scope:** moving the rest of the HUD wiring.

### [2026-08-14] Delete stale untracked layout-v26

- **Type:** problem-avoiding (a stale layout override shadowed the canonical one).

- **Problem:** an untracked `res/layout-v26/activity_main.xml` (an old copy)
  overrode the canonical base layout on API 26+ — the emulator rendered the old
  broken geometry and the new design appeared "not applied". It had no
  API-26-specific differences.

- **Chosen solution:** delete the directory; the base layout is the single
  source. Confirmed identical-by-hash before deletion.

- **Why:** a stale shadow copy is a recurring trap on every layout edit.

### [2026-08-14] Video smart-fit: center-crop cover instead of letterbox

- **Type:** design-clarifying (how the video fills the screen is product behavior).

- **Problem:** the MJPEG renderer letterboxed every frame ("fit within"), so a
  robot camera feed — typically a different aspect than the phone — left black
  bars and never filled the HUD screen like a real camera view.

- **Alternatives considered:** keep letterboxing (whole image visible, but
  bars); center-crop cover (fill the screen, cropping the aspect mismatch from
  the center); fit-xy (distort).

- **Chosen solution:** `MjpegFrameRenderer.destRect` now center-crops to cover
  the display (`scale = max(dispW/bmw, dispH/bmh)`, overflow clipped by the
  canvas); the returned `Rect` may sit outside the display. KDoc + docs updated;
  `MjpegFrameRendererTest` asserts both crop axes.

- **Why:** a camera feed should look like a camera feed — edge-to-edge, no bars.
  The crop keeps the center of the frame (the robot's area of interest).

- **Out of scope:** no per-frame scaling modes (e.g. a toggle back to fit) —
  the Type 1 HUD's single smart-fit is the whole product behavior.

### [2026-08-14] CC0 test images replace in-memory JPEG fixtures + theme screenshot test

- **Type:** problem-avoiding (synthetic test frames hid the real JPEG path; no HUD-over-frame oracle).

- **Problem:** (1) the video tests generated JPEG frames in-memory (solid
  colors), so decode coverage used synthetic data and the suite carried
  `Bitmap.compress` code under `@GraphicsMode(NATIVE)`; (2) there was no way to
  see how the HUD actually looks over a real camera-like frame.

- **Alternatives considered:** keep generated frames; add a host-side MJPEG
  server + emulator screenshot (extra moving parts, hangs on Windows pipe
  inheritance); capture the render from inside a Robolectric test.

- **Chosen solution:**

  - Commit a **CC0 1.0 vintage-cat illustration** (verified via the Openverse CC
    index; `rawpixel.com/image/9405680`), downscaled/center-cropped to three
    test fixtures (640×480 / 320×200 / 1000×600) with a CC0 license note, in
    `app/src/test/resources/mjpeg/`.
  - `SyntheticMjpegServer`'s default frames are now those fixtures (no
    in-memory generation); the suite consolidates into one `MjpegServerTest`
    (byte-identical decode of all three sizes, cycling, drop/reconnect).
  - New `HudThemeTest` renders the **real** activity view hierarchy over a cat
    frame for each connection state, analyzes it in-process (structural +
    source-pixel mapping), and writes `hud_{connected,connecting,standby,error}.png`
    to the always-on `screenshots.dir` build-output property.

- **Why:** real photographic frames exercise the full JPEG path; the theme test
  doubles as a screenshot artifact with zero external infrastructure; CC0 needs
  no attribution and permits test use.

- **Out of scope:** glyph-text pixel fidelity under Robolectric (structural
  assertions cover it); shipping the images in the APK (test-only resources);
  the emulator-based screenshot workflow as the theme-test oracle.

### [2026-08-15] Idiomatic Kotlin pass (Java→Kotlin leftovers) — Campaign 20

- **Type:** problem-avoiding (Java-isms in migrated code had no repeatable gate).

- **Problem:** the post-migration code still carried Java-isms: `!!` on SDK
  `getString` results, AOSP `m`-prefixed fields, JavaBeans `getX()/setX()`
  accessors on our own classes, and no detekt rule to catch any of it going
  forward.

- **Alternatives considered:** leave the code as-is; run Android Studio
  Inspect Code and fix everything it flags (IDE-strength, not repeatable in
  CI); convert only own-code accessors (framework Java API calls are correct
  Kotlin interop and untouched).

- **Chosen solution:**

  - `!!` → elvis: `getString(key, default) ?: default` (the 2-arg SDK
    `getString` is nullable).
  - Drop `m` prefixes; convert our own accessors to Kotlin properties
    (`SenderService.hostAddr`/`hostPort`/`keepaliveTimeout` with a custom
    setter, `SquareTouchPadLayout.padName`/`sender`, `MjpegView.isPlaying`,
    `MainActivity.senderService`/`settingsController`, `SettingsUi` interface
    → `var wheelStep`/`var wheelEnabled`).
  - Listener-registration setters stay methods (Kotlin does not SAM-convert a
    lambda into a fun-interface property); `setSenderService` stays a method
    for its null-guard.
  - Enable syntax-only detekt idiom rules (`ExpressionBodySyntax`,
    `UseIfInsteadOfWhen`, `UseLet`) in `detekt.yml`; type-resolution rules
    (`CanBeNonNullable`, `UseDataClass`, `ObjectLiteralToLambda`) are gated on
    the detekt 2.0.0 bump (detekt 1.23.8 under AGP 9's built-in Kotlin
    generates no type-resolution tasks).

- **Why:** properties are the canonical Kotlin idiom for accessors; the rules
  make the canonicality check repeatable in the existing gate; deferring the
  type-resolution rules avoids wiring a half-broken toolchain combination that
  the planned detekt 2.0 bump would throw away.

- **Out of scope:** Android/JDK API call sites (correct interop, not
  Java-isms); the AS Inspect Code report (one-off, not gateable — the detekt
  rules are the CI mirror); rewriting parser/network port-fidelity relaxations
  already documented in `detekt.yml`.
