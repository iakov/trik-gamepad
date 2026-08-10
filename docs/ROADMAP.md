# ROADMAP — trik-gamepad improvement plan (post pure-Kotlin migration)

<!-- encoding: utf-8 -->

Scope: maintainability and quality improvements after the pure-Kotlin
migration. Baseline (verified 2026-08-08): `app/` is 0 `.java`; coverage gate
95% line / 80% branch (measured 97.3% / 81.9%); CI **fully green** (build + all 9
instrumented on `aosp_atd`).

Each item: commit-per-concern, gated by the full local suite (test lint
detekt spotbugsDebug jacocoTestReport jacocoTestCoverageVerification
spotlessCheck) before each push, `git status --short` clean before push.

## Campaign 2 (post-ROADMAP — DONE 2026-08-08)

Scope and the **coverage-first strategy** (order B2 → B4 → C → B3 → A → D → E,
with B4 as the one refactor-first exception) were set by user decision — see
`DECISIONS.md` "Campaign 2 strategy: coverage-first". Full execution record:
`MEMORY.md` "Campaign 2 execution run".

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
  re-enabled; `78aace4`). See MEMORY "AGP 9.3.1 / Gradle 9.5.0 migration LANDED".
- **F** ✅ Cleanup (emulator snapshot discrepancy **closed as cosmetic**;
  DummyServer note already in TESTING.md) + MEMORY retrospective added.
  **Campaign 2 complete.**

## Campaign 3 — strict full-project review (DONE 2026-08-08)

Full code audit + web best-practice research; durable reference in
`docs/architecture.md` (domain knowledge + pitfalls), findings in the "Campaign
3 execution run" MEMORY entry. Priorities: **P0 correctness → P1 leak fix → P2
architecture → P3 hygiene** (each item one commit, gate, push; detailed plan:
`MEMORY.md` "Campaign 3 execution run").

- **P0** ✅ `Integer.getInteger` wheel-step bug (setting never applies),
  `Sensor.TYPE_ALL` → `TYPE_ACCELEROMETER`, keepalive timer ordering,
  `mSender!!` guard. `e621f95`, `8eb6c3e`, `314b910`.
- **P1** ✅ MJPEG render-thread/socket leak — `stopPlayback` closes the stream
  to unblock the blocking read; structural test on a real socket pair.
  `75e09ca`.
- **P2** ✅ `SenderViewModel` hoist (`cccb05b`); `StateFlow<ConnectionState>` +
  `repeatOnLifecycle(STARTED)` (`10a63bf`); idempotent pref-listener pairing
  (`15550df`).
- **P3** ✅ NSC cleartext scoped to the robot host + CRLF header line-scanner
  (`4b7b869`); Kotlin idiom cleanup — `@JvmOverloads`, interop comments,
  `Locale.ROOT` (`b382774`). **Deferred:** detekt 2.0.0 until stable, version
  catalogs, AGP 9.3 lint report-DSL migration (not code-urgent).
- **Bonus** ✅ **minSdk 21 → 23** ("forget obsolete"; `4753c45`) and a
  **CI publish job** (`a4a39b8`) that uploads a debug-signed `releaseDebug`
  APK artifact on green **master** runs (dormant in the single-branch no-PR
  workflow). Two CI lint errors found mid-run and fixed (`5994128`):
  `repeatOnLifecycle` belongs in `onCreate` (not `onStart`), and the NSC
  attribute needs `tools:targetApi="n"` (API 24). Closing CI run `31271190323`
  fully green.
- **Next (Campaign 4):** synthetic MJPEG server test — see below.

## Campaign 4 — synthetic MJPEG server test (DONE 2026-08-08)

A real HTTP MJPEG server (test source) streams JPEG frames to the app pipeline
to verify **decoder correctness**, **decode performance**, and **connection
drop + restore** (R12 reconnect-on-error). User decisions: **Robolectric NATIVE
venue** (real `BitmapFactory` via `@GraphicsMode(NATIVE)`), **full app reconnect
path**, **cycle a few images** (solid color + gradient + second color). Landed
`e0dcc18`; execution record: MEMORY "Campaign 4
execution run".

## Deferred from Campaign 3/4

Parked items (rationale in MEMORY "Campaign 3"/"Campaign 4"): **core-ktx 1.19.0**
(needs compileSdk 37; R7 locks 36) · **detekt 2.0.0** until stable. Resolved
2026-08-09: **version catalogs** ✅ (gradle/libs.versions.toml) · **AGP 9.3 lint
report-DSL migration** — N/A, the build never used the deprecated
`htmlReport`/`textReport` DSL.

## Campaign 5 — raw-socket MJPEG HTTP client (DONE 2026-08-09)

NSC cleartext is scoped to the default robot hotspot `192.168.77.1`
(`res/xml/network_security_config.xml`, Campaign 3 P3). NSC is a static XML
resource — it can only whitelist fixed hostnames/IPs, so a user who configures
a robot at a **non-`192.168.77.1` host over plain HTTP loses the video stream**
(TCP commands still work: `SenderService` uses raw sockets, NSC-transparent).

**Chosen fix (user decision 2026-08-08): raw-socket HTTP client** — rationale
and rejected alternatives in `DECISIONS.md` "Campaign 5: raw-socket MJPEG HTTP
client". Landed `40587a3`: `RawSocketHttpStream` opens a `Socket`, writes the
GET request, parses the response head (`200 OK` + `Content-Length` / chunked /
until-close), and hands the body to `MjpegInputStream`; bypasses NSC entirely.
`VideoStreamLoader.openStream` uses it for all `http` URLs (https keeps
`HttpURLConnection`).

**Verification:** `SyntheticMjpegServer` reused as the server side — the client
decodes frames from a `127.0.0.1` (non-NSC-whitelisted) host, plus chunked /
bad-size / truncated-head / EOF unit cases (`RawSocketHttpStreamTest`).
**Bonus found during the run:** the jacoco class dir pointed at a stale
`tmp/kotlin-classes/debug` under AGP 9's built-in Kotlin, so the 95/80 gate was
silently under-measuring new app classes; fixed to the live
`intermediates/built_in_kotlinc` output (`abdbcd3`) and coverage restored to
97.2% line / 80.85% branch.

## Campaign 6 — test quality as code: reduce logical SLOC via reuse

Tests are code; keep them high-quality by re-using what is similar. Drive down
test **logical SLOC** (per-class summed `token_count` from `lizard -l kotlin`
over `app/src/test` + `app/src/androidTest`, a Halstead-N proxy) without
degrading coverage. The 95 line / 80 branch gate measures **app** classes only,
so shrinking tests cannot lower it — the rule is to preserve the **set of
exercised branches**, not the set of assertions (each table row keeps hitting
its distinct branch). User decisions 2026-08-09: scope = unit + androidTest ·
metric = per-class token total (not a per-function cap) · enforcement = **hard
duplication gate + token trend** · push cadence = commit per concern, push at
milestones · postponed items stay parked. Metric rationale: `DECISIONS.md`
"[2026-08-09] Test logical SLOC metric".

Baseline (measured 2026-08-09): **12,659 total tokens** across 23 files;
jscpd **11 clones, 754 duplicated tokens (3.36%)** at min-tokens 50. Result
after B+C: **11,234 tokens (-11.3%)**, **0 clones**, coverage flat at 97.2% line /
80.9% branch. Per-commit verification: full 3-variant suite ×2, jacoco 95/80 +
per-class branch diff, `spotlessApply` as a separate invocation, `detekt --rerun-tasks`, jscpd gate, clean tree before push.

- **A** ✅ Metric + tooling: `lizard` (`.venv`) + jscpd (npx) probed; baseline
  measured; `.jscpd.json` calibrated (min-tokens 50, threshold 0, `mild`,
  `ignorePattern: ["import.*"]`); metric recorded in `DECISIONS.md`; ROADMAP
  section; TESTING.md baseline table. `f336cf1`.
- **B** ✅ Test-support reuse (commit-per-concern): **B1** shared `TestTcpServer`
  merging `SenderServiceTest.DummyServer` + `SenderServiceAdvancedTest. ReadUntilStopServer` (ephemeral port + latch + bounded-poll `awaitReceived`;
  TESTING.md contracts preserved) · **B2** shared CRLF-CRLF request-head reader
  (`RawSocketHttpStreamTest` + `SyntheticMjpegServer`) · **B3** `setPref(key, value)` helper (`MainActivityTest` + `MainActivitySettingsControllerTest`) ·
  **B4** `RobolectricTestBase` for the `@Config` triple (15 classes; @Config/
  @LooperMode inheritance probed) · **B5** MjpegInputStreamTest `frameWithHeaders`
  builder · **B6** `measureAndLayout(w, h)` (`SquareTouchPadLayoutTest`) · **B7**
  SettingsActivityTest shared `@Before` · **B8** androidTest `initNetworkSettings`
  - gesture/command helpers (`DummyServer` stays separate by design).
    `145401b`..`b1962a0`, gate + CI green.
- **C** ✅ Data-driven tables (branch-preserving; plain `listOf(...).forEach {}`,
  assertion messages carry the input; per-row fixture reset where a row's
  expected value depends on prior state): **C1** `TouchPadControllerTest` +
  `WheelControllerTest` → one table each · **C2** `MainActivityTest` clusters
  (keepalive / wheel-step / video-URI) · **C3** `MainActivitySettingsControllerTest`
  clusters (pads-alpha clamp, wheel-step clamp). `80f33ce`..`a4f46ce`.
- **D** ✅ Gate + docs: **D1** jscpd hard gate wired into `scripts/gate.ps1` +
  the `ci.yml` quality step; `gate.ps1` prints the lizard token total · **D2**
  AGENTS.md "Tests are code" guardrail + Commands; TESTING.md "Test TCP servers"
  → section rename + metrics section; MEMORY retrospective · **D3** final
  re-measure vs baseline (11,234 tokens / 0 clones) + coverage diff (97.2 / 80.9,
  flat). `219ff3e` + `c43084c`; the cross-platform jscpd `ignorePattern`
  calibration (`5bd0b69`, see DECISIONS.md). **CAMPAIGN 6 COMPLETE — closing CI
  run `31304148201` fully green (build + jscpd + instrumented).** Retrospective:
  MEMORY.md "Campaign 6 execution run".

Out of scope: JUnit 5, AGP `testFixtures`, androidTest `DummyServer`
consolidation, comment removal, per-function token caps.

## Campaign 7 — Cross-platform dev readiness

User decision 2026-08-09: after this campaign the project must be **ready for
cross-platform development** (dev machines switching Ubuntu → Arch → macOS),
keeping Windows working. Approach: Python/uv (already present) everywhere the
docs/tooling were Windows-first. Rationale + alternatives: `DECISIONS.md`
"[2026-08-09] Dev tooling is cross-platform via uv (Python gate)".

- **A** ✅ uv tooling baseline: `pyproject.toml` (`package = false`,
  `requires-python >= 3.9`, dev group: `lizard==1.23.0`, `mdformat==0.7.21`,
  `pre-commit`) + `uv sync` → committed `uv.lock`; `.venv` now
  platform-agnostic (`uv run` resolves `.venv/bin` on POSIX,
  `.venv/Scripts` on Windows).
- **B** ✅ Python gate: `scripts/gate.py` (stdlib `subprocess`; picks
  `gradlew.bat` vs `./gradlew` by OS) replaces `scripts/gate.ps1`; same
  spotlessApply-first two-invocation order + jscpd hard gate + lizard token
  trend (A0 baseline 12,659); `scripts/spotless_apply.py` + shared
  `scripts/_gradle.py`. `gate.py` runs `gradlew.bat` via subprocess directly
  on Windows (probed — no `cmd /c` needed).
- **C** ✅ pre-commit cross-platform: spotless-apply hook →
  `language: system` + `entry: python scripts/spotless_apply.py` (no more
  `cmd /c gradlew.bat`).
- **D** ✅ Docs sweep: AGENTS.md uv-sync bullet + `uv run ...` commands +
  `gate.py` refs + Windows-only marks; **new "Windows/PowerShell quirks"
  section** consolidates the PowerShell/Windows traps (not generalized —
  re-audited on the first POSIX box); TESTING.md generic
  `~/.robolectric-download-lock` + emulator platform table (Windows AEHD /
  Linux KVM / macOS Hypervisor.framework, macOS unverified); MEMORY.md Python
  tooling → uv sync flow + platform-aware emulator/SDK notes; DECISIONS.md
  cross-platform entry; docs/architecture.md `gate.py` ref.
- **E** ✅ Cleanup: `scripts/gate.ps1` deleted (git history retains it).

`ci.yml` deliberately unchanged (already Linux); the Linux/macOS paths of the
new tooling are validated on the first POSIX dev box, not in CI.

## Campaign 8 — MJPEG video self-healing (bounded retry, control-gated)

User-visible regression, found 2026-08-10 while comparing against upstream: the
original app's **unconditional 30 s MJPEG restart loop** was removed as the
socket-leak driver (Campaign 3 P1) and replaced with `reconnect-on-error`, which
only fires on a real `IOException` from the render thread. The video can then
freeze until the user leaves and re-enters the app — the classic case is the
robot driving out of wifi range and back: the socket `SO_TIMEOUT` (5 s) fires
once, the reconnect attempt fails while the robot is gone, and **nothing
retries**. Same gap after a failed first open (robot not booted yet), and after
surface recreation (foldable hinge, `stopPlayback` joins the render thread with
no restarter).

**Goal / acceptance criteria:**

- **Recovery ≤ 10 s after the robot is reachable again** — gamepad open, no
  user action. The retry is **gated on the existing TCP control-connection
  keepalive** (`connectionState is Connected`): the keepalive loop sends
  `keepalive <ms>` every (timeout − 300) ms while connected, so `Connected` is
  a reachability proxy for the same robot/wifi — no new probe is added and
  retries never hammer a robot that is clearly down. Target worst case:
  5 s tick + ≤ 5 s connect ≈ 10 s.
- **No unconditional periodic reconnect** — a healthy stream keeps flowing
  (preserves the P1 leak fix, no 30 s blips).
- **No leaks** — retry timers cancelled on pause/destroy; `stopPlayback`
  close+join unchanged.
- Coverage gate 95/80 kept; retry logic unit-testable; reuses
  `SyntheticMjpegServer`.

**Decided design (2026-08-10, user):** new injectable `VideoRetryController`
(main-thread `Handler` postDelayed tick, interval 5000 ms). Reload the video
when *activity resumed ∧ control `Connected` ∧ video URL configured ∧
`!view.isPlaying()`*. Evaluated (a) on the bounded tick while those hold, and
(b) immediately on the control `Connected` edge transition (a pad touch
reconnects the control connection → instant reload instead of waiting for the
tick). `VideoStreamLoader.load` reports success/failure via an `onResult`
callback (today a failed open returns null silently) and `MjpegView` exposes
`isPlaying()` — one "not playing" predicate covers silent stall, failed first
open, and surface recreation.

Idle recovery is bounded by user interaction: the retry is gated on the control
connection, which only reconnects on the next pad touch — accepted by design
(user decision 2026-08-10).

**Process (user decision 2026-08-10): TDD** — failing tests first that simulate
the problem (server down → load fails → retry → server up → playing; mid-stream
drop → reconnect), then the implementation, then the full 3-variant suite ×2 +
jacoco 95/80 + instrumented suite.

**Verification:** `SyntheticMjpegServer` — server down at open → retries →
server up → stream starts (bounded poll); mid-stream socket kill → reconnect;
stall with the socket kept open → `SO_TIMEOUT` → reconnect; control-`Connected`
edge reloads immediately. `VideoRetryControllerTest` drives the tick
deterministically under the PAUSED looper.

**Addendum (2026-08-10, cleanup campaign): video loading indicator.** A centered
circular `ProgressBar` (`@+id/videoLoading`, subtle contrast backing) shows
while the video is loading/reconnecting: it appears on every `restartVideoStream`
(initial resume, stream-error reconnect, control-`Connected` reload) and hides
on the first decoded frame of the playback cycle (`MjpegView.OnFirstFrameListener`,
fired on decode, not canvas draw — `lockCanvas` is unreliable under Robolectric).
Robot video disabled → no frame ever decodes → the indicator keeps cycling
(indefinitely). No video URL configured → stays hidden. Branch coverage raised
0.8016 → 0.8123 (parser error paths + `MainActivity` gates + `androidx`/
Kotlin-inline-synthetic exclusion — see MEMORY "Coverage" design decisions).

## Phase 1 — Instrumented CI without macOS

Decision: **no macOS/GPU runner** — rationale and alternatives in `DECISIONS.md`
"Phase 1 experiment 2: aosp_atd + swiftshader PASSES instrumented". ✅ **DONE —
experiment 2 won:** `target: aosp_atd` + `-gpu swiftshader_indirect` + the
immersive pre-empt + `FocusAwareActivityTestRule` passes all 9 instrumented
tests (first fully-green run `31232406163`, 2026-08-08). The old "aosp_atd
never grants focus (8/8)" finding predated the pre-empt/focus-wait fixes. The
last red run was a CI script trap: `android-emulator-runner` runs each
`script:` line as its own `sh -c`, so the multi-line `if/fi` retry never
parsed — fixed as a single-line `cmd || { ...; }` (AGENTS.md rule updated). No
remaining experiments needed.

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
- ✅ Final retrospective → AGENTS.md / MEMORY.md / TESTING.md;
  last-known-good CI run id updated (`31232406163`, first fully-green run).

## Campaign 9 — End-user UX (Material/accessibility alignment)

User-visible gaps found 2026-08-10 while reviewing the app against Material
Design / Android UX guidance from an end-user point of view (no Material
theming, zero accessibility metadata, toast-only connection status, plain-text
settings fields, unconditional keep-screen-on). Scope set by user decision:
A–E + G–J + marginal batch; **deferred** F (first-run guidance) and K (pad
graphics).

- **A** ✅ Connection status = **gear-border recolor** (`btn_settings.xml` layer
  rect gets `@+id/settingsButtonBg` + `<stroke>`; runtime
  `GradientDrawable.setStroke` mutation). Pure `ConnectionState → color-resource`
  mapper (`ConnectionIndicator`, unit-tested): `Connected` → greendark,
  `Connecting` → amber (new), `Disconnected` → red (new). Wired into
  MainActivity's `repeatOnLifecycle` collector. **No new view → no
  touch-interception risk**; the id-based `SettingsTests` locator is immune to
  the background change.
- **B** Accessibility: `contentDescription` on the gear button (was "Button,
  unlabeled"), both pads, and the MJPEG view; dropped
  `FLAG_IGNORE_GLOBAL_SETTING` from `MagicButtonPanel` + `SquareTouchPadLayout`
  (haptics now respect the system setting); reconciled `MjpegView` focusability
  (XML `focusable=false` was overridden by code `isFocusable = true`).
- **C** Settings UX: removed the implicit video-URI copy on host change
  (`MainActivitySettingsController`); added an explicit **"Reset video URI to
  robot default"** preference that fills `http://<host>:8080/?action=stream` on
  tap; pads-alpha and wheel-sensitivity `EditTextPreference`s → `SeekBarPreference`
  (0..255 / 1..100) with live summaries.
- **I** Wheel toggle: `menu.xml` CheckBox action-view → `SwitchPreference` in
  Settings; sensitivity summary clarifies "smaller = more sensitive".
- **D** Feedback hygiene: with persistent gear status in place, gated toasts —
  removed per-connect `onConnectionFinished` toast spam and the routine
  "Inactive gamepad" pause disconnect toast; real errors still toast.
- **G** Empty/degraded states: video-area placeholder text when no video URL is
  configured; connection hint pairs with the gear color.
- **H** Battery-aware screen: `android:keepScreenOn` no longer unconditional —
  exposed as a Settings toggle ("Keep screen on") applied from the controller.
- **J** Font-scale resilience: verified the magic-button row at `FONT_SCALE 1.3`
  and fixed clipping.
- **E** Material theming: brand greens wired into `colorPrimary`/`colorAccent`;
  `values-night` added; Settings `Light` theme unified with the main dark theme;
  pressed-state selector for magic buttons; hardcoded `Color.RED` pad circle →
  theme color.
- **L/M/N** Marginal batch: copy-robot-IP affordance (About system), a11y lint
  rules confirmed not suppressed, error Snackbars replace error toasts.

Deferred: **F** first-run guidance, **K** pad graphics — cosmetic/onboarding,
parked for a future campaign.
