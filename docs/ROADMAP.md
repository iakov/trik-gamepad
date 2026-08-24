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

**Verification (2026-08-10):** full gate green (test lint detekt spotbugs
jacoco 95/80 spotlessCheck jscpd lizard) — branch coverage **0.8063** (new
code pulled it under 0.80 mid-campaign; covered with Int-storage
seekbar tests + `ConnectionFeedback` null-path tests, see MEMORY retrospective).
3-variant `test` suite run twice (`--rerun-tasks` on the second pass — an
up-to-date second run silently skips). Instrumented **9/9 on both API-36
emulators** (`connectedDebugAndroidTest --no-configuration-cache`); the wheel
menu removal broke `SettingsTests.openSettings`' index-based matcher
(`childAtPosition(...,1)` assumed the wheel item) — fixed to id+text+displayed.
**CAMPAIGN 9 COMPLETE.**

## Campaign 10 — hardware & connection UX

Scope (user decision 2026-08-10): end-user UX for hardware gamepads (mapping,
swap toggle), connection clarity (persistent status + explicit connect), magic
button symbols/count, a manual hide-controls toggle, robot presets, and a
settings restructure into a Basic root + "Advanced settings" sub-screen.
Campaign timing (wall-clock): `Estimated` at kickoff, `Actual` (elapsed)
finalized at the docs step — no start/end recorded. Full execution record:
MEMORY.md "Campaign 10 execution run".

| Estimated | Actual |
|-----------|--------|
| ~10.5 h | ≈10 h (approximate) |

- **Settings restructure** — root shows Basic categories inline (Robot
  connection / Video / Controls) + a nested "Advanced settings" sub-screen
  (Wheel / Pads & video / Hardware gamepad / Network / Magic buttons / Robot
  presets / About). `SettingsFragment.init*` helpers follow their prefs;
  `findPreference` traverses the nested hierarchy.
- **A** FPS overlay toggle — `SK_SHOW_FPS` (default off); `MjpegView.showFps`
  volatile, the render thread skips the `drawText` when off.
- **B** Connection status + explicit connect — a status `TextView` above the
  gear (`Connected to host:port` / `Connecting…` / `Disconnected — tap to connect`); tap calls public `SenderService.connect()`; logic in
  `ConnectionFeedback` (zero new MainActivity methods).
- **G** Magic buttons — `SK_MAGIC_BUTTON_COUNT` (0–5, default 3) + display
  glyphs `magicSymbol1..5` (defaults ▲ ■ ● ✕ ◆); glyphs display-only, the
  protocol stays numeric `btn N down`; `contentDescription` = "Button N";
  pure `MagicButtonSymbols` helper.
- **H** Hide pads & buttons — `SK_HIDE_CONTROLS` (default off, manual);
  `controlsOverlay` + button row → `GONE` (removed from hit-testing);
  Advanced > Pads & video.
- **D** Robot presets — pure `RobotPresetStore` (SharedPreferences + `org.json`):
  save/load/all/delete; dynamic category rows; apply = single `edit { }` of
  host/port/videoURI.
- **E** Hardware gamepad — pure `HardwareGamepadController`: D-pad/left
  stick→pad1, right stick→pad2, A/B/X/Y→magic 1–4, L1/R1→magic 5 (within
  count); `SK_GAMEPAD_SWAP` swaps sticks; MainActivity overrides
  `dispatchKeyEvent`/`onGenericMotionEvent`.
- **Verification** — full gate ×2 (`--rerun-tasks` on pass 2), instrumented
  9/9 on both API-36 emulators; detekt `TooManyFunctions` 25→31 (adapter/
  override rationale). Instrumented `SettingsTests` rewritten index→id-based
  with sub-screen navigation.
  **CAMPAIGN 10 COMPLETE** (commits `e6f556a`, `60773e0`; retrospective in
  MEMORY.md).

## Campaign 10 follow-up (implemented 2026-08-11)

Connection-status pill + loading-spinner gating. Decided by user (2026-08-10);
spec was recorded here and in `.PLAN.md`, then executed this session.
Rationale/alternatives: DECISIONS.md "Connection status pill + spinner
gating". Retrospective + timing: MEMORY.md "Campaign 11 execution run".

- **Status text** becomes a centered orange pill: text `Tap to connect…`
  (20sp ≈5% of landscape height), `@color/status_orange` on the dark rounded
  pill, centered on screen. `Connecting` shows `Connecting…`; `Connected`
  hides the pill (`visibility=gone` — the green gear carries that state);
  `Disconnected` shows `Tap to connect…` and stays tappable (tap →
  `SenderService.connect()`). The pill is the last child, drawn over the
  spinner center (z-order) but the two never co-display.
- **Loading spinner gating**: the spinner (`video_loading_size` = 120dp ≈30%
  of landscape height, up from the default size) shows only while the control
  connection is `Connected` **and** a stream URL is configured. No spinner
  when not connected or when no URL is set.
- Implied changes: dropped `addressProvider` from `ConnectionFeedback`; dropped
  the now-dead `SenderService.getHostPort()` (+ its test); deleted the unused
  `connection_status_connected` string; `MainActivity.restartVideoStream`
  gates `showVideoLoading()` on `mVideoURL != null && connectionState is Connected`; `ConnectionFeedbackTest` + `MainActivityTest` updated
  (spinner-visible tests connect the sender first; new gate tests cover
  disconnected / no-URL spinner-hidden).
- **Verification**: full gate green (`0.95 LINE / 0.80 BRANCH`, jscpd 0 clones,
  detekt/spotbugs/lint clean), full 3-variant `test` suite ×2 green,
  instrumented 9/9 on both emulators. Screenshot proof (centered orange pill
  on a fresh disconnected launch): `.tmp/status_line_final.png` (orange pixel
  cluster verified at screen center).
  **CAMPAIGN 11 COMPLETE.**

## Campaign 12 — empty-host / video-only mode + connect-UX hardening

Scope (user decision 2026-08-11): an empty robot IP is a valid configuration
(device used for video streaming only) — the pill and the pads/buttons are
hidden when no target is configured, `connect()` no-ops on a blank host, and
the connect state machine is hardened (a failed connect no longer sticks at
"Connecting…"). Video-stream failures get a throttled Snackbar while bounded
retries continue (relaxed for the empty-host case so video-only still
auto-recovers). Rationale: DECISIONS.md "Empty-host video-only mode".
Retrospective + elapsed: MEMORY.md "Campaign 12 execution run".

| Estimated | Actual |
|-----------|--------|
| ~6 h | 2 h 19 m |

- **Pill gating** — `ConnectionFeedback` gains `targetConfiguredProvider`; a
  `Disconnected` state with no configured host hides the pill entirely
  (no "Tap to connect…" with nothing to connect to). `MainActivity` supplies
  it from `SenderService.getHostAddr()`.
- **Controls auto-hide** — pads + magic-button row hide when the host is blank,
  regardless of the "Hide pads & buttons" toggle (which keeps meaning for a
  configured robot). No new settings option.
- **connect() no-op** on blank host; **stuck-Connecting fix** — a failed
  connect transitions back to `Disconnected("")` so the pill honestly returns
  to "Tap to connect…" and the existing "Connection to X error." Snackbar is
  the single notification.
- **Throttled video-failure Snackbar** — pure `VideoStreamErrorNotifier`
  (~15 s window) surfaces "Video stream unavailable" once per failure episode;
  bounded 5 s retries unchanged.
- **Video-only retry** — `shouldReloadVideo` allows retry when the host is empty
  (no control connection needed); the spinner stays `Connected`-gated.
- **Empty-host video-URI default** — blank host yields an empty default
  (placeholder shown) instead of the malformed `http://:8080/...`, removing a
  misleading "Illegal video stream URL" toast.
- **Tests** — `ConnectionFeedbackTest` (target-configured GONE/VISIBLE),
  `MainActivityTest` (empty-host controls hidden; `shouldReloadVideo` gate:
  video-only true / configured-but-disconnected false),
  `MainActivitySettingsControllerTest` (host-blank hides controls even with the
  toggle unset; empty-host URI default null), `SenderServiceAdvancedTest`
  (failed connect returns to `Disconnected`; `connectWithBlankHostShouldNotAttempt`),
  `VideoStreamErrorNotifierTest` (throttle boundary). The magic-buttons
  instrumented test now uses direct `performClick` (a tap during the
  connect→Connected transition was dropped by touch injection — see MEMORY).
- **Verification**: canonical gate green ×2 (jacoco 96.5% line / 83.4% branch,
  jscpd 0 clones), instrumented **9/9 on both emulators** (after a true cold
  boot + the `performClick` fix), pre-commit clean. Screenshot proof of the
  empty-host video-only state: `.tmp/empty_host_final.png` (no pill — 0 orange
  pixels — no controls, placeholder shown).
  **CAMPAIGN 12 COMPLETE.**

## Campaign 14 — user-facing diagnostics & crash reporting (offline-first)

Scope (user decision 2026-08-11): give users a way to hand developers a
reproduction bundle (app+device spec, app settings, connection state, an event
trace, crash stacktraces) so an indie maintainer spends as little time as
possible reproducing bugs — across all stores incl. F-Droid (no Play-only
telemetry, no new permissions, no silent data egress). Rationale/alternatives:
DECISIONS.md "User-facing diagnostics & crash reporting (offline-first,
Campaign 14)". Retrospective + elapsed: MEMORY.md "Campaign 14 execution run".

| Estimated | Actual |
|-----------|--------|
| ~2 h | ≈1 h 45 m (impl + gate iterations + docs + emulator proof) |

- **AppLog + 500-line ring buffer** — every log call mirrors to logcat and
  into a thread-safe ring buffer; the buffer floor defaults to INFO and is
  user-tunable via "Diagnostics verbosity" (Errors only / Info / Debug /
  Verbose). Keepalive/connect logs were promoted to INFO so they land in the
  default report; per-command sends stay DEBUG.
- **Diagnostic report** — one markdown data block (version/versionCode/build,
  device model, Android/API, display+density+font scale, locale, connection
  state, full settings snapshot with `(default)` markers, presets, log tail,
  last crash trace).
- **Review-then-share flow** — "Report an issue" writes the report file
  (`cacheDir/diagnostics/`, FileProvider, cache-path only) and opens it in a
  text editor (chooser title = "Choose a text editor to review and edit before
  sharing to developers"); the default-off "Share logs without editing" switch
  skips the editor with a direct share sheet (also the no-editor fallback).
  "Copy report" + in-app "View log" round out the About rows.
- **Crash capture + next-launch dialog** — chaining `UncaughtExceptionHandler`
  via a new `App` Application; bounded crash store; once-per-crash dialog with
  Review & share / Copy / Dismiss.
- **Verification** — canonical gate green (LINE **0.952**, BRANCH **0.865**,
  gates 0.95/0.85; jscpd 0 clones), full 3-variant `test` ×2, pre-commit
  clean; pushed `18f6e1c..b07b570`. Instrumented suite + emulator screenshot
  proof tracked after the docs step. **CAMPAIGN 14 COMPLETE** (implementation
  commits `c8c5a64..b07b570`).

## Campaign 15 — UX & accessibility (a11y, WCAG, i18n, system theme)

Scope (user decision 2026-08-11): end-user UX + accessibility across gamepad,
networking, video, HUD and Settings, grounded in Android/Material/WCAG
guidance. Decision + rationale: DECISIONS.md "[2026-08-11] Campaign 15: UX &
accessibility scope"; conventions: DESIGN.md; retrospective + quirks:
MEMORY.md "Campaign 15 execution run".

| Estimated | Actual |
|-----------|--------|
| ~6 h | ≈3 h 30 m (impl + gates + instrumented + docs) |

- **Settings** — strings externalized to values/strings.xml (E1); the five
  per-button glyph rows replaced by one "Button symbols…" dialog (pre-filled
  defaults ▲ ■ ● ✕ ◆, "Use default symbols", saves the array independent of the
  count); current-value summaries for videoURI/seekbars/verbosity (with a
  fresh-install default fix: seekbars now show the XML default, not 0);
  ellipsis on input-dialog rows; About system copies the short spec only
  ("Copy report" stays the full-report row); Advanced summary mentions
  diagnostics.
- **Accessibility** — deduped, target-gated announceForAccessibility
  announcements (ConnectionAnnouncer); the gear's contentDescription carries
  the connection state; magic-button descriptions include the glyph; the pill
  is a real 48dp touch target; magic buttons got 48dp minima, an explicit
  contrast-safe text color and Material ripple; importantForAccessibility
  hygiene on the spinner.
- **WCAG regression tests** — WcagContrastTest (relative-luminance ratios on
  the live color resources, ≥4.5:1 text / ≥3.0:1 UI) and TouchTargetSizeTest
  (48dp on gear/pill/button-row).
- **Connection & video** — the pill shows Connecting to host:port:; a reload
  of a previously-playing stream shows a "Video reconnecting:" badge. Stall
  detection was deliberately not added (a video-disabled robot legitimately
  keeps the spinner cycling).
- **Localization** — ships en+ru+fr+de+vi (
  resourceConfigurations,
  values-*/strings.xml, Android 13+ localeConfig); exact-match typical
  strings reuse @android:string/* (copy/cancel); deterministic
  scripts/check_translations.py --sync parity guard wired into the canonical
  gate; one-off MyMemory back-translation review ran for all four locales
  (RU will also get a native-speaker review).
- **System theme** — Theme.AppCompat.DayNight for Settings/dialogs; the
  gamepad HUD stays dark-over-video (black window background,
  forceDarkAllowed=false). Verified by pixel-sampled screenshots
  (.tmp/settings_light.png / settings_dark.png).
- **Verification** — canonical gate green twice (LINE **0.9752** / BRANCH
  **0.8661**, jscpd 0 clones, translations sync OK); 3-variant test ×2
  (second with --rerun-tasks); instrumented **9/9 on both API-36 emulators**
  (two instrumented tests updated for the ellipsis titles / glyph-suffixed
  button descriptions); commits c817649..1b0115e. **CAMPAIGN 15 COMPLETE.**

## Campaign 16 — K2 `-Wextra` warnings-as-errors (build hardening)

Scope (user decision 2026-08-12): make Kotlin compiler warnings a build failure
across all compilations (main, unit test, androidTest) by enabling K2's
`-Wextra` extra checks + `allWarningsAsErrors`, then resolving every warning
that surfaced (fix the fixable, suppress the genuinely-unfixable per-site with
a rationale). Decision + rationale: DECISIONS.md "[2026-08-12] K2 -Wextra
warnings-as-errors"; accepted-suppression registry: TESTING.md "Compiler
warnings as errors"; retrospective + quirks: MEMORY.md "Campaign 16 execution
run".

| Estimated | Actual |
|-----------|--------|
| ~4 h | ≈7 h 45 m (impl + fixes + gates ×4 + commit + green CI; includes a 15-min cold-daemon hang) |

- **Config** — `kotlin { compilerOptions { extraWarnings.set(true); allWarningsAsErrors.set(true) } }` in `app/build.gradle` (extension level =
  every Kotlin compilation; AGP 9 built-in Kotlin wires the real KGP 2.2.10
  `KotlinAndroidProjectExtension`, verified empirically via javap).
- **Fixes** — 38 warnings resolved: redundant `.toDouble()` ×2
  (TouchPadController), redundant initializer (MjpegInputStream),
  `BoundedInputStream` → builder API ×4 (MjpegInputStream/RawSocketHttpStream),
  `Object()` → `Any()` (TestTcpServer), written-once `lateinit` → nullable var
  (VideoStreamSelfHealingTest), `flushForegroundThreadScheduler()` →
  `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()` ×10,
  `createSensorEvent` → `SensorEventBuilder` ×3, About display metrics →
  `resources.displayMetrics`, unsafe `parentFile` null-calls (report tests).
- **Dedup** — `MainActivitySettingsController.readInt` (private) collapsed into
  the shared `SettingsFragment.readSeekBarValue` companion member.
- **Suppressed (rationale-commented, registry in TESTING.md)** —
  `getParcelableExtra(String)` ×2 (typed overload is API 33+; tests run at
  minSdk 23), `updateConfiguration(config, metrics)` (1-arg removed in SDK 36),
  `ACTION_MULTIPLE` (only non-DOWN/UP KeyEvent action), `TYPE_ANNOUNCEMENT`,
  `ActivityTestRule` (file-scope), `Object()` monitor in DummyServer
  (`PLATFORM_CLASS_MAPPED_TO_KOTLIN`).
- **Verification** — all three compilations build with ZERO warnings under
  warnings-as-errors; 3-variant test ×4 green (`--rerun-tasks`, 2m22s–2m40s);
  canonical gate green twice (LINE **0.9753** / BRANCH **0.8661**, jscpd 0 in
  gate scope); lint/detekt/spotbugs clean. Commit `3855815`, pushed; CI run
  31567580412 green. **CAMPAIGN 16 COMPLETE.**

## Campaign 17 — Type 1 HUD theme + app/robot settings split (UX prototyping)

Scope (user-driven, 2026-08-12): translate a v0.dev gamepad mockup into the
Android HUD as the **Type 1 theme** (glass/arcade), remove the legacy green
action bar, and split Settings into **App** (appearance/controls/wheel/pads/
hardware/magic/about) and **Robot/target** (host/port/video/network/presets)
screens. **Fully local — no commits, no pushes** (working-tree changes only;
since committed 2026-08-14 as part of `c71ac63`, pushed, CI green).
Decision + rationale: DECISIONS.md "[2026-08-12] Type 1 HUD theme + app/robot
settings split". Retrospective + quirks: MEMORY.md "Campaign 17 execution run".

| Estimated | Actual |
|-----------|--------|
| — | ≈3.5 h (12:45 → 16:20 local; impl + gate ×20 + emulator screenshots) |

- **Type 1 HUD** — glass pads (one tintable chrome vector: crosshair rings,
  arrows, mode glyph), round glass magic buttons in a capsule, circular glass
  gear, glass status pill (state icon as a compound drawable), readability scrim,
  green spinner. All static chrome in `hud_*` XML/styles; Kotlin only tints the
  runtime connection-state accent.
- **Connection-tone semantics** — pure `ConnectionIndicator` maps green
  (Connected) / amber (Connecting) / **sepia #C9A97A** (idle + clean pause) / red
  (real error); pads/buttons/pill/chip all read the same accent. Controls dim to
  "at most 40%" while disconnected (never below the user's `showPads` base).
- **Action bar removed** — `supportActionBar?.hide()`; the robot IP moved to the
  tappable top-left glass chip (host → video host → `---.---.---.---` filler),
  tinted to the robot control status. Gear opens App settings; chip opens Robot
  settings.
- **Settings split** — two activities (`SettingsActivity`/`RobotSettingsActivity`)
  sharing one parameterized `SettingsFragment` (`ARG_PREFERENCE_XML`), nested
  sub-screens preserve the XML choice; cross-link rows both ways. App keeps the
  Basic/Advanced structure; robot screen is flat.
- **Verification** — canonical gate green (test ×3-variant `--rerun-tasks`,
  lint, detekt, spotbugs, jacoco, spotless, jscpd 0 clones, translations sync
  118 keys); screenshots pixel-census + hash-verified on the Swiftshader
  emulator: `_ui_260812-1840-01_gamepad.png`,
  `_ui_260812-1840-02_app-settings.png`, `_ui_260812-1840-03_robot-settings.png`.
  **CAMPAIGN 17 COMPLETE** (uncommitted at the time — session rules forbid
  commits/pushes; committed later in `c71ac63`).

### Deferred (recorded this session)

- **Left-pad spring-back joystick** — a configurable option making the left pad
  clamp the knob to a circle and spring back on release (real-stick feel).
- **Left-handed mode** — swap the on-screen pads for left-handed users.
- **Dual-network socket binding** — fix "connection lost" when cellular data is
  on alongside the robot Wi-Fi (bare `Socket()` routes over the default network;
  bind to `TRANSPORT_WIFI`). User-reported 2026-08-12; tracked in `.PLAN.md`.

## Campaign 18 — pad render + layout + compact chip (UX follow-up)

Scope (user-driven, 2026-08-12): fix the C17 "pads barely visible" rendering
regression, then land the approved pad visuals + layout (~260dp pads centered in
their halves, mockup chrome/knob, compact robot-IP chip). **Fully local — no
commits, no pushes** (working-tree changes only; user rule; since committed
2026-08-14 as part of `c71ac63`, pushed, CI green). Decision +
rationale: DECISIONS.md "[2026-08-12] Pad render + layout (C18)". Retrospective +
quirks: MEMORY.md "Campaign 18 execution run".

| Estimated | Actual |
|-----------|--------|
| — | ≈3.7 h (18:40 → 22:25 local; incl. cold-boot emulator relaunch + gate/9-test iterations + docs) |

- **Render root cause fixed (2 bugs)** — `SquareTouchPadLayout.onMeasure` never
  measured its children (chrome/glyph were 0×0 — the C17 "verified" screenshot
  was byte-identical to a fresh capture, proving the chrome never rendered), and
  `animatePadsAlpha`'s `AlphaAnimation` (fillAfter) multiplied with
  `applyHudTone`'s direct `.alpha` (≈0.392² ≈ 0.154 effective opacity).
- **Pad layout** — full-screen `controlsOverlay` with two `weight=1`
  gravity-centered halves; pads are 260dp squares centered at 25%/75% width,
  vertically centered over the video. Buttons/gear/chip re-brought to front so
  they stay tappable above the pads.
- **Pad visuals** — dashed outer ring (drawn in code; vectors have no dashes),
  solid inner ring, full crosshair lines + edge arrows (tintable vector),
  radial-gradient joystick knob with glow + center dot (`onDraw`), 2dp glass
  border + soft glow.
- **Compact chip** — 28dp min-height, tight padding/margins, **14sp kept**;
  sub-48dp touch target accepted + documented.
- **C17 test regression** — `SettingsTests` double-`pressBack` reduced to one
  (navigation flattened to a single level by the settings split).
- **Verification** — canonical gate green; 3-variant `test --rerun-tasks` green;
  instrumented **9/9 on both emulators**; proof screenshots pixel-census +
  hash-matched: `_ui_260812-2205-01.png`. **CAMPAIGN 18 COMPLETE** (uncommitted
  at the time — session rules forbid commits/pushes; committed later in
  `c71ac63`).

## Deferred — drop AppCompat (re-evaluate later)

**Status: deferred (2026-08-14).** No work scheduled; revisit when the
settings UI is next touched or before a release size pass.

**Why now-deferred:** the direct AppCompat surface is small (two
`AppCompatActivity` bases, appcompat `AlertDialog`, `ActionBar`, the
`Theme.AppCompat.*` parents), but **androidx.preference requires appcompat
transitively**, so removing the direct dependency would not remove AppCompat
from the APK. A real drop needs the settings screens rewritten off
`PreferenceFragmentCompat` — a large, risky churn for little gain.

**What changed in the meantime (2026-08-14):** `material` was removed entirely
(the HUD error pill replaced its only consumer, the Snackbar). `material` also
pulled in appcompat transitively, so the transitive AppCompat surface shrank to
just androidx.preference. Re-evaluating is cheaper than before.

**When to re-evaluate:**

- If the settings screens are rewritten anyway (custom RecyclerView/preferences
  UI), the appcompat dependency can likely go with them.
- A release size pass that shows appcompat's dex/resource weight as
  unacceptable (measure `app-debug.apk` size before/after; material removal
  already trimmed the tree).

**Work needed if approved:** replace `AppCompatActivity` (→ plain
`android.app.Activity`/`ComponentActivity`), appcompat `AlertDialog` →
framework `AlertDialog`, `ActionBar` handling → framework equivalent,
`Theme.AppCompat.*` → framework `Theme.Material`/`DeviceDefault` day-night
parents. Keep `androidx.core`/`lifecycle`/`activity` (not appcompat).

## Campaign 19 — HUD error pill, two-layer layout, timeout tooling (2026-08-14)

Scope (user-driven, 2026-08-14): bullet-proof symbol glyphs via a bundled mono
font subset; two-layer HUD layout (pads layer + visuals layer); a
content-fitting glass error pill replacing the Material Snackbar + the
`material` dependency drop; timeout-bound tooling (process-tree kill) after a
~15 h `adb install` hang; `RobotChipController` extraction; video renderer
smart-fit; CC0 test fixtures + theme screenshots. Decisions + rationale:
DECISIONS.md "[2026-08-14] HUD error pill …", "Timeout-bound tooling", "Drop
the material dependency", "RobotChipController extraction", "Delete stale
untracked layout-v26", "Video smart-fit", "CC0 test images + theme screenshot
test". Retrospective + quirks: MEMORY.md "Campaign 19 execution run" +
"Campaign 19 addendum" + "Campaign 19 retrospective". **Committed + pushed
2026-08-14/15** (6 commits, `9596f72`..`a52c01d`), CI green.

| Estimated | Actual |
|-----------|--------|
| — | 9 h 17 m (committed span 2026-08-14 17:37 → 2026-08-15 02:44 +03:00; CI green) |

- **Symbol font** — `res/font/symbols_mono.ttf` (DejaVuSansMono Nerd Font
  subset, ~9 KB) via `scripts/build_symbol_font.py` (cmap-verified); pill ⏻/↺,
  gear ⚙, magic buttons all use it; license texts in `res/raw` + in-app
  "Open-source licenses" dialog (About).
- **Two-layer layout** — pads (260dp, `layout_gravity="center"` in two weight-1
  halves) below chip/gear/buttons/pill; `hud_half_glyph` 7dp spacing; no
  `bringToFront()` (XML order = z-order). Deleted the stale untracked
  `layout-v26` shadow that broke the emulator render.
- **Error pill** — `connectionError` TextView (Hud.GlassPill, wrap_content)
  replaces the Snackbar; fade-in + auto-dismiss at the pill↔buttons midpoint;
  **`material` dependency removed** (verified gone from debugRuntimeClasspath).
- **Timeout tooling** — `scripts/run_bounded.py` (process-TREE kill), bounded
  `_gradle.call_gradle` (900 s) + `gate.py` non-gradle steps (300 s); host
  `adb.bat` shim (machine-local).
- **Chip extraction** — `RobotChipController`; MainActivity 36 → ~28 functions
  (detekt gate).
- **Video smart-fit** — `MjpegFrameRenderer.destRect` center-crops to cover the
  screen (was letterbox); any-size robot feeds now fill edge-to-edge.
- **CC0 test fixtures + theme test** — three committed cat JPEGs
  (`app/src/test/resources/mjpeg/`) replace in-memory frame generation;
  consolidated `mjpeg/MjpegServerTest`; new `HudThemeTest` renders the real HUD
  over the cat frame for each connection state and writes `hud_*.png` to the
  always-on `screenshots.dir` build output. Video-test refactor committed on its
  own (9803bb5); the rest rode with the HUD commit (`c71ac63`).
- **CAMPAIGN 19 COMPLETE** — instrumented re-verify covered by the CI emulator
  job (API 36, green); the 4 theme screenshots are the layout/video proof;
  `.PLAN.md` trimmed to the remaining deferred items.

## Campaign 20 — idiomatic Kotlin pass (Java→Kotlin leftovers) (2026-08-15)

Scope (user-driven, 2026-08-15): make the Kotlin canonical after the Java→Kotlin
migration — own-code Java-isms only (framework Java API calls untouched):
redundant `!!` on nullable `getString` (elvis instead), `m`-prefixed fields
(AOSP Java convention), JavaBeans `getX()/setX()` accessors → Kotlin properties
(including the `SettingsUi` interface `getWheelStep`/`setWheelStep`/
`isWheelEnabled`/`setWheelEnabled` → `var wheelStep`/`var wheelEnabled`), plus
syntax-only detekt idiom rules (`ExpressionBodySyntax`, `UseIfInsteadOfWhen`,
`UseLet`). Decision + rationale: DECISIONS.md "[2026-08-15] Idiomatic Kotlin
pass"; discovery + findings: MEMORY.md "Campaign 20 execution run". **Committed + pushed
2026-08-15** (single commit, `212791b`), CI green. Full retrospective (checklist
protocol): MEMORY.md "Campaign 20 retrospective".

| Estimated | Actual |
|-----------|--------|
| — | 1 h 9 m (committed span 2026-08-15 07:18 → 08:27 +03:00; CI green) |

- **`!!` removal** — the 2-arg `SharedPreferences.getString(key, default)` is
  nullable in the SDK; `getString(...)!!` → `getString(...) ?: default`
  (`MainActivitySettingsController`).
- **`m`-prefixes dropped** — `mSensorManager`/`mAngle`/`mWheelStep`/
  `mWheelEnabled`/`mVideo`/`mVideoURL`/`mSettingsController` (MainActivity),
  `mConnectTask`/`mOut`/`mHostAddr`/`mHostPort` (SenderService).
- **Accessors → properties** — `SenderService.hostAddr`/`hostPort`/
  `keepaliveTimeout` (custom setter keeps the keepalive-timer restart),
  `SquareTouchPadLayout.padName`/`sender`, `MjpegView.isPlaying`,
  `MainActivity.senderService`/`settingsController`; the `SettingsUi`
  interface gained `var wheelStep`/`var wheelEnabled`.
- **Kept as methods** — listener-registration setters
  (`setShowTextCallback`/`setOnDisconnectedListener`, Android `setOnClickListener`
  style: Kotlin does not SAM-convert a lambda into a fun-interface *property*),
  and the null-guard `setSenderService` (a null sender must keep the existing
  one).
- **detekt rules** — `ExpressionBodySyntax`, `UseIfInsteadOfWhen`, `UseLet`
  (syntax-only; the type-resolution idiom rules `CanBeNonNullable`/
  `UseDataClass`/`ObjectLiteralToLambda` are gated on the detekt 2.0.0 bump —
  detekt 1.23.8 under AGP 9's built-in Kotlin generates no type-resolution
  tasks; see `.PLAN.md`).
- **CAMPAIGN 20 COMPLETE** — one commit for code + tests + detekt config
  (`212791b`), gate green, 3-variant `test` twice, CI build+gate suite and
  instrumented API 36 green on the first push.

## Campaign 21 — inset-aware HUD container (on-device defect from A.1) (2026-08-15)

Scope (user-driven, 2026-08-15): the physical-phone instrumented re-verify
(A.1) FAILED — both `SettingsTests` showed the Settings screen never opening.
Root cause: on the phone (landscape) the top-left chip sits inside the top
`mandatorySystemGestures`/shade strip and its taps were delivered to systemui,
not the app — a real on-device UX defect the emulator cannot reproduce. Fix the
UI, keep the discovered test as the pass criterion. Decision + rationale:
DECISIONS.md "Inset-aware HUD container"; findings: MEMORY.md "Campaign 21
execution run". **Committed + pushed 2026-08-15** (CI green: build + gate suite
and instrumented API 36).

| Estimated | Actual |
|-----------|--------|
| — | 2 h 50 m (14:26 → 17:16 +03:00; CI green) |

- **Inset-aware HUD container** — the three edge-pinned controls (chip, gear,
  magic buttons) moved into a full-screen `@+id/hudControls` RelativeLayout,
  padded per edge from the window insets (`systemBars` | `displayCutout` |
  `systemGestures` | `mandatorySystemGestures`) in `MainActivity.onCreate`.
  The video stays full-bleed; center pills stay in `main`. Standard
  edge-to-edge + gesture-nav recipe: clears the shade/status strip, cutout
  and nav zones on every device.
- **New Robolectric structural test** — `hudControlsPaddingShouldFollowWindowInsets`
  dispatches a compat insets frame and asserts the container adopts it per
  edge. Test-authoring traps hit (minSdk qualifier): Robolectric simulates a
  status-bar inset at activity setup (assert the effect of the dispatched
  frame, not an initial zero), and raw `WindowInsets.Type` is not mocked on
  API 23 (use the compat builder).
- **Device-identifier guardrail (user instruction)** — adb serial/model/IMEI
  are session-only, never committed (AGENTS.md "Repo hygiene" + DECISIONS.md
  "Device identifiers never enter repo content"; 521 commits verified clean).
- **Deferred → RESOLVED 2026-08-15** — the on-device confirmation (unchanged
  `SettingsTests` on the phone + real `input tap` + screenshot): the phone
  dropped off adb mid-campaign, then reconnected the same day. Result:
  **9/9 on the phone** including both `SettingsTests`; a real `input tap` at
  the chip center opened `RobotSettingsActivity` (the tap the shade strip used
  to eat); pixel-census shows the chip clear of the top OS strip. Full record:
  MEMORY.md "Campaign 21".

## Campaign 22 — magic-button cluster fix + S10e screenshot profile + README hero (2026-08-17)

Scope (user-driven): (1) the magic-button cluster's torn top arcs and
pill-centered glyph read — root-caused to `centerGlyph()`'s `translationY`
moving the whole button into the cluster's clip area (DECISIONS.md "Magic-button
glyph centering: asymmetric padding, not view translation"); (2) drop the
cluster capsule so the buttons render as separate bare circles; (3) make the
`HudThemeTest` screenshots realistic — the mdpi render was useless (every dp =
1px), so switched to the S10e profile (2280×1080 @ 440dpi) and verified by
pixel-census (buttons 132px = 48dp, glyphs centered ±0.18dp); (4) README hero
screenshot from the connected-state render, exported lightweight
(1.87 MiB → 103 KiB JPEG) via `scripts/export_readme_screenshot.py`. **Committed
2026-08-17** (3 commits, local; push pending CI/retrospective).

| Estimated | Actual |
|-----------|--------|
| — | 4 h 30 m (2026-08-15 evening → 2026-08-17 14:03; incl. laptop-crash recovery) |

- **Borderless magic-button cluster** — removed
  `android:background="@drawable/hud_action_cluster"`, deleted the drawable;
  glyph centering now uses asymmetric per-button padding (glyph ink only), so
  the circular backgrounds stay centered in the cluster while each glyph reads
  centered in its circle. Verified on the emulator (5 intact circle rings, no
  capsule stroke, all glyphs ±0.18dp centered).
- **S10e screenshot profile** — `HudThemeTest` now renders at
  `w829dp-h393dp-land-440dpi` → 2280×1080, the S10e landscape resolution, and
  hides the loading spinner/reconnect badge (a real device hides them on the
  first video frame; no frame renders in the test). Verified: 4 PNGs all
  2280×1080, circles 132px, connected center clean.
- **README hero screenshot** — `docs/img/hud_connected.jpg` (1280×606, JPEG
  q82, 103 KiB) added to the README, regenerable via the export script. Kept
  fresh by the AGENTS.md "Before release" checklist (user chose a note, no
  hook).
- **Emulator-launch fix (tooling)** — launching the emulator from the tool now
  goes through run_bounded wrapping a detached-launcher `.ps1` (returns in
  3.5 s, no boot block) + a separate bounded boot-wait, instead of a raw
  `Start-Process` (third shape of the caller-blocking trap). DECISIONS.md
  "[2026-08-17] Emulator launch".

## Campaign 23 — dual-network socket binding + emulator snapshot cleanup + deferred docs fixes (2026-08-17)

Scope (user-driven): (1) push + green CI to open the campaign; (2) **A.2** —
bind TCP/video sockets to the robot's Wi-Fi when cellular is the system
default network (user-reported connection loss, tracked since Campaign 17);
(3) **A.4** — emulator snapshot-policy cleanup; (4) the deferred
`dimens.xml`/`DESIGN.md` comment fixes. Decision + rationale: DECISIONS.md
"[2026-08-17] A.2 dual-network socket binding: injectable Wi-Fi provider
seam"; findings + retrospective: MEMORY.md "Campaign 23 retrospective".
**Committed + pushed 2026-08-17** (CI green: build + gate suite and
instrumented API 36).

| Estimated | Actual |
|-----------|--------|
| — | 1 h 48 m (14:20 → 16:08 +03:00; CI green) |

- **A.2 dual-network socket binding** — `SocketBinder` fun interface +
  `WifiSocketBinder` with an injectable `wifiNetworkProvider` seam; the
  production provider tracks `TRANSPORT_WIFI` via the non-deprecated
  `registerNetworkCallback` flow (the `allNetworks()` scan was rejected by the
  user even though suppressible; the `Handler` overload of
  `registerNetworkCallback` is API 26+, so the 2-arg form is used). Sockets
  bound via `Network.bindSocket` (verified minSdk-23-safe in android-23.jar)
  at both connect sites (`SenderService.connectToTRIK`,
  `RawSocketHttpStream.open`); the https path stays on the default network;
  `ACCESS_NETWORK_STATE` added. New `SocketBinderTest` (4 tests,
  mutation-checked red) — 8 variants green; coverage 96.7% line / 85.1%
  branch. On-robot verification (cellular + robot Wi-Fi) is a manual user step.
- **A.4 emulator snapshot cleanup** — set `firstboot.saveToLocalSnapshot=no`
  in both `Atd_API36`/`Swiftshader_API36` `config.ini` (machine-local AVD
  configs, so no repo commit was warranted); verified the Swiftshader AVD
  cold-boots (51 s). Finding: the key is firstboot-era; launch args govern
  snapshot behavior (`-no-snapshot` for Swiftshader; Atd keeps snapshots for
  fast reboots per TESTING.md) — the discrepancy is cosmetic.
- **Deferred docs fixes** — `dimens.xml` magic-button margin comment (was the
  6dp+4dp capsule rationale, now a 2dp borderless cluster) + `DESIGN.md`
  "rounded capsule buttons" → "borderless bare-circle magic buttons".
- **Tooling note** — the run_bounded-wrapped emulator launch hung this session
  (no PID output, bash-tool timeout killed the wrapper; the detached emulator
  still booted). Root cause not pinned; the AGENTS.md emulator-launch rule was
  updated to verify liveness independently and never treat a hung wrapper as a
  failed launch.

## Campaign 24 — on-device performance analysis + GPU-backed MJPEG render + haptics (2026-08-17/18)

Scope (user-driven): (1) host **DummyRobotServer** (pure Kotlin in the test
source set — TCP 4444 log-only + MJPEG 8080 via the reused
`SyntheticMjpegServer`) for on-device smoke + profiling over Wi-Fi; (2)
measured-before profiling of `releaseDebug` on the phone (60 s simpleperf
callgraph + gfxinfo + meminfo); (3) **fix-all-local** — **A.5** MJPEG
render-loop de-spin + GPU-backed render, **A.6** haptics (user design: one
light tick on pad-up, every button incl. the gear); (4) emulator re-measure,
then real-device re-measure. Retrospective + new simpleperf workflow:
MEMORY.md "Campaign 24 retrospective" + "Device performance profiling".
Decision: DECISIONS.md "[2026-08-18] A.5 GPU-backed MJPEG rendering".
**Code + tests + gates done; COMMIT/PUSH PENDING (user: no commit/push).**

| Estimated | Actual |
|-----------|--------|
| — | ~1 h 15 m (08-17 evening: profiling + A.5/A.6 + gates + emulator measure) + ~10 m (08-18 device re-measure) — uncommitted so far |

- **DummyRobotServer.kt** (new, test source set) + `runDummyRobotServer`/
  `writeDummyServerClasspath` gradle tasks; the detached launcher runs
  `java -cp <test classpath>` via `scripts/run_bounded.py`.
- **A.5 measured deltas** (releaseDebug, 60 s, synthetic 50 fps
  MJPEG): total samples **510 421 → 145 588 (−71%)**; render-thread spin
  (`access$getRunning$p`/`access$getSurfaceDone$p`, 36%) and Skia lowp software
  raster (`gather_8888`/`store_565`, ~29%) **gone**; gfxinfo **High input
  latency 1222 → 2**, frames 2689 → 3970 (~66 fps), janky 0.05%, p99 8 ms;
  meminfo Graphics 68 → 99 MB (GL 28 → 58 MB — the GPU texture trade-off).
  Architecture: plain `View` drawing the decoded frame in `onDraw` on the HWUI
  canvas (`TextureView.onDraw` is final); render thread blocks on the socket
  read + 5 ms idle after a dropped frame.
- **A.6 haptics**: pad fires one light `KEYBOARD_TAP` on ACTION_UP only (no
  per-move, no queued-after-lift); every button incl. the settings gear.
- **Follow-ups (→ `.PLAN.md`)**: main-thread socket-I/O (~30% of samples);
  `inSampleSize` decode to display size (cuts texture upload + the Graphics
  bump); real-robot re-verify (synthetic source only); emulator gfxinfo is
  software-GPU-bound — frame timing is real-device-only.

## Campaign 25 — scenario-driven video retry + decision governance (2026-08-18)

Scope (user-driven, interactive design re-discussion): (1) establish the
**scenario contract** (DESIGN.md "Scenarios & use-cases", S1–S14) as the top
of the design — decisions may never violate a scenario; (2) **type-tag every
DECISIONS.md entry** (`design-clarifying` / `problem-avoiding`); (3) re-design
the video retry: **Option B** removes the control gate (`shouldReloadVideo` =
`videoUrl != null && !isPlaying`), fixing the reported "video frozen during
Connecting" and making video-from-a-different-IP / spectator / competition
setups recover (S4/S6/S7/S14); (4) URL-gated spinner (simplest). Governance
rule: design sits above decisions; auto mode sticks to the agreed contract,
re-design happens interactively.

| Estimated | Actual |
|-----------|--------|
| — | ~50 m docs (C25) + per-feature commits + full gate (code features: C24 perf, haptics, video retry) |

- **Design/docs**: DESIGN.md contract section (S1–S14 + P1–P6 + empty-value
  semantics); DECISIONS.md typing convention + type on all 64 entries + new
  "[2026-08-18] Scenario-driven video retry" entry + superseded banners
  (Campaign 8, Campaign 10 spinner part, Campaign 12 gate-relaxation part);
  MEMORY/TESTING/architecture/AGENTS updated.
- **Code (per-feature commits)**: C24 perf (GPU-backed render), haptics schema,
  then the video-retry Option B gate + spinner + tests (red-first). Pending
  follow-up from C24 stays: main-thread socket-I/O, `inSampleSize`,
  real-robot re-verify.

## Campaign 26 — E2 settings video-URI row + E3 https-over-Wi-Fi video + push-prep audit (2026-08-19/20)

Scope (user-driven): (1) **E2** — fresh-install settings inconsistency (the
video-URI row showed "No stream URI set" while the app streamed the
host-derived default); (2) **E3** — https video bypassed the robot Wi-Fi
(default network) and rejected the robot's self-signed camera; (3) record the
user-suggested futures (UDP control, extra video formats, SBC video source);
(4) push-prep device-identifier audit + history scrub.

| Estimated | Actual |
|-----------|--------|
| — | ≈08-19 daytime (E2+E3) + ~1 h 08-20 (push-prep audit + scrub + push) |

- **E2** (`c8852da`): shared `effectiveVideoUri` between the settings row and
  `MainActivitySettingsController`; unset = derived default, explicitly-empty
  = disabled; host-change refresh. Red-first tests in
  `RobotSettingsActivityTest` + `MainActivitySettingsControllerTest`.
  On-device verified via `dumpsys activity top` view bounds (uiautomator dump
  is broken device-wide on the phone).
- **E3** (`1a0223f`): `ConnectionOpener` fun interface + `WifiConnectionOpener`
  (`Network.openConnection` on the tracked Wi-Fi network; fallback; trust-all
  TLS isolated in the companion for a later TOFU swap); `WifiNetworkTracker`
  extracted from `WifiSocketBinder` (shared by both binders);
  `VideoStreamLoader.connectionOpener` param. Red-first + mutation-checked:
  `WifiConnectionOpenerTest` (5) + `HttpsVideoStreamTest` (end-to-end frames
  over a self-signed local `HttpsServer`, committed throwaway keystore).
  DECISIONS.md entry + DESIGN.md S13 clarified. On-robot verification
  (cellular + Wi-Fi, https camera) is a manual user step.
- **Dreams** (`387bdd8`): UDP control transport, extra video formats
  (RTSP/HLS/MPEG-TS candidates), SBC as a separate video source (already the
  designed S4/S6 contract — note only). See the Dreams section below.
- **Push-prep audit** (`ce7365e`..`a8ebdf0`): the unpushed commits carried a
  a device serial and a model code; amended the oldest
  unpushed commit + rebased so pushed history is clean, plus a scrub commit
  for the carried published line; `--force-with-lease` to the fork branch.
  Guardrail violation #2 → AGENTS.md pre-push scan step + DECISIONS.md
  "[2026-08-20] Device-identifier scrub on push-prep". Published history not
  rewritten (user decision); the `20e0085` blob still carries one model reference.
- **Retrospective** — MEMORY.md "Campaign 26 retrospective" (checklist format;
  rephrased Process Q2).

## Campaign 27 — UDP control transport + robot keepalive + protocol source of truth (2026-08-20)

Scope (user-driven, auto mode): (1) **UDP control transport** (optional, next
to TCP) with **robot keepalive** liveness processing; (2) the **gamepad
protocol becomes a DESIGN.md source of truth** with DummyRobotServer as the
reference implementation; (3) **A.3 release smoke** (TCP + UDP + MJPEG on the
emulator); (4) main-thread socket-I/O cleanup + MJPEG **decode downsampling**
(perf); (5) **device-identifier pre-commit hook + gate step** (the
2026-08-20 decision's "future candidate"); (6) scripts folder polish.

| Estimated | Actual |
|-----------|--------|
| — | ~8 h 45 m (2026-08-20, ~14:10→22:55 — includes earlier UDP implementation, the push, and the CI flake-fix cycle) |

- **Main-thread I/O fix** (`eaaa5f2`): `CommandTransport` + `TcpTransport`
  extracted; the send + `checkError()` runs on the executor thread, the
  failure decision is posted to the main thread. Guard test
  `postCommandSendsOnTheExecutorThreadNeverTheMainThread` (real executor).
  Emulator simpleperf re-measure: main thread **4.52%** (idle/wait symbols
  only) vs the C24 "main ~30% socket I/O".
- **UDP control transport** (`3b81bd7`): `TransportMode` (`tcp`/`udp`, global
  `SK_TRANSPORT` pref in the robot screen's Network category),
  `UdpTransport` (one newline-terminated command per datagram; optimistic
  Connected on first send; per-keepalive-tick resend of the last pad/wheel —
  buttons are edges and are NOT re-sent), `WifiDatagramBinder`
  (`Network.bindSocket(DatagramSocket)` for S13), and the **robot-keepalive
  liveness**: any received control message resets the clock; `keepalive <ms>`
  sets the expected interval (`-1`/absent = disabled); `ms + 2000` gap →
  disconnect. Protocol decision (user, 2026-08-20): robot MAY send optional
  keepalive; the app's own keepalives stay additive. TCP read path deferred
  (write-only stays). Verified today there is **no** robot→app channel. Tests:
  `SenderServiceUdpTest` (11), `WifiDatagramBinderTest` (3),
  `UdpTransportTest` (4), `SenderViewModelTest` UDP factory branch; coverage
  gate restored (branch 0.843 → 0.854).
- **Protocol source of truth** (`ce9a942`): new DESIGN.md section "Gamepad
  protocol (source of truth)" — wire format, command matrix (when/why), TCP
  write-only + UDP optimistic lifecycles, robot→app receive rules, additive
  rule, reference implementation. architecture.md "TCP command protocol" and
  MEMORY.md "App protocol" reduced to pointers; AGENTS.md index + trigger
  updated. DummyRobotServer (already gained `DummyRobotUdpServer` in the UDP
  commit) is the reference implementation and must mirror the section.
- **MJPEG decode downsampling** (`bf7b08a`): `inSampleSize` set from the
  previous frame's source size so a camera larger than the display is not
  decoded at full size every frame (first frame and pre-layout decode at 1).
  Red-first tests `extractFrameDownsamplesToTheDisplaySizeFromThePreviousFrame`
  - `extractFrameSkipsDownsamplingWhenTheFrameFitsTheDisplay`; mutation-checked
    (the detekt ComplexCondition + the 0-size-display infinite-loop guard were
    caught by the gate).
- **Device-identifier hook** (`6a26a50`): `scripts/check_device_identifiers.py`
  (IMEI `\b\d{15}\b`, Samsung `SM-<letter><digits>` model codes, the observed
  `RFCX`-prefixed serial shape; the `SM-XXXXXX` docs placeholder is not
  matched) as a pre-commit hook + gate.py step + CI step. The push-prep scan
  stays the final gate for the committed diff. DECISIONS entry updated.
- **A.3 release smoke** (verified on `emulator-5554`, no code change): built +
  installed `app-releaseDebug.apk`, host DummyRobotServer on `10.0.2.2:4444`,
  verified TCP (`TCP< pad 1 up`, `TCP< keepalive 1000`) and UDP
  (`UDP< pad 1 -6 -3`, `UDP< keepalive 1000`) command flow, MJPEG streaming
  (server frames climbing, live pixel-census between two captures, chip
  "control Connected"). Proof: `.tmp/_ui_260820-2153.png` (pixel-census
  verified).
- **Retrospective** — MEMORY.md "Campaign 27 retrospective" + DECISIONS.md
  entries.

## Campaign 28 — TCP robot keepalive read path + toolchain hygiene (2026-08-21)

Scope (user-driven, auto mode): device-free continuation — no phone, no
emulator. (1) **TCP robot keepalive read path** (the C27 "TCP stays
write-only" deferral, closed): the input half stays open, a `TcpReceive`
thread feeds the existing robot-liveness machinery over the default
transport; (2) **toolchain hygiene** — root-caused the Gradle-10-era
deprecations and added a **device-identifier selftest**.

| Estimated | Actual |
|-----------|--------|
| — | ~2 h 45 m (2026-08-21) |

- **TCP robot keepalive read path** (`65fb9f6`): `TcpTransport` no longer
  calls `socket.shutdownInput()`; a `TcpReceive` thread reads optional robot
  lines and reports them through the existing `CommandTransport.onMessage`,
  which feeds the SAME `SenderService.onRobotMessage`/`checkRobotLiveness`
  machinery as UDP (already transport-agnostic). Additive rule unchanged: a
  robot that never sends anything simply never invokes the callback and
  liveness stays disabled at `-1`. Dead-connection detection stays primarily
  write-error-based. Red-first tests (3, plus a passing negative test) in
  `SenderServiceUdpTest`: `tcpRobotKeepaliveSetsTheHeartbeatInterval`,
  `tcpRobotMessageResetsTheLivenessClock`,
  `tcpMissingRobotKeepaliveDisconnectsAfterIntervalPlusGap`,
  `tcpUnknownRobotMessageIsIgnored` — confirmed failing on the old
  `shutdownInput()` implementation, green after. `TestTcpServer` gained
  `sendRobotMessage`; `RobolectricTestBase` gained the shared `runBounded`
  poll helper. `DummyRobotServer` gained the optional `--tcp-keepalive <ms>`
  flag (reference implementation of the read path). DESIGN.md "Gamepad
  protocol", architecture.md, MEMORY.md "App protocol", CommandTransport KDoc,
  and a new DECISIONS.md entry all updated in the same commit.
- **Toolchain hygiene** (`bc6a1c4`): `--warning-mode all --no-configuration-cache -Dorg.gradle.deprecation.trace=true` traced the two
  "incompatible with Gradle 10" deprecations to plugin internals — detekt
  1.23.8's `DetektPlugin.apply` (`ReportingExtension.file`) and AGP-internal
  `VariantDependenciesBuilder` (project-as-dependency-notation). Neither is
  fixable from our build scripts; both remain until the toolchain bumps
  (`.PLAN.md` updated with the precise trace). `check_device_identifiers.py`
  gained `--selftest` (synthetic fixtures; self-scan skip so the tool's own
  fixtures never trip the gate).
- **Retrospective** — MEMORY.md "Campaign 28 retrospective" + DECISIONS.md
  entry.

## Campaign 29 — on-phone smoke verification of the TCP keepalive read path (2026-08-21)

Scope (user-driven, full auto): verify the C28 TCP robot-keepalive read path
on a **real phone** (the deferred "on-robot verification" from C28), plus the
UDP transport, MJPEG video, and the silent-robot baseline — all against the
host `DummyRobotServer` over Wi-Fi.

| Estimated | Actual |
|-----------|--------|
| — | ~1 h 10 m (2026-08-21, incl. the server-launch hang cycles) |

- **TCP read path VERIFIED on device:** the app (TCP transport, host
  `192.168.88.254:4444`) stayed Connected for 20+ minutes while
  `DummyRobotServer --tcp-keepalive 2000` emitted `keepalive 2000` every 2 s.
  Server log: `TCP> <phone> keepalive 2000` (27+ emissions) AND the app's own
  `TCP< <phone> keepalive 5000` (every ~4.7 s) + `pad`/`btn` commands. App
  logcat: `Robot keepalive: 2000 ms` (parsed by `SenderService.onRobotMessage`)
  and `Sending keepalive 5000 message`. Chip contentDescription read
  "…192.168.88.254, control Connected, video streaming" via uiautomator.
- **UDP VERIFIED:** with `transport=udp`, movement datagrams
  (`UDP< pad 1 x y`), the `keepalive 5000` tick, AND the per-tick pad-state
  resend (`pad 1 up` re-sent with each keepalive) all appear — the UDP
  convergence rule works on-device. Chip stayed Connected.
- **MJPEG VERIFIED:** `servedFrames` climbed steadily (~33 fps) and a pixel
  diff of two captures 4 s apart showed ~125k differing pixels (the dark-sepia
  vintage-cat fixture — the census bands match the fixture's actual dark tones,
  not a black screen).
- **Silent-robot baseline VERIFIED:** server relaunched WITHOUT
  `--tcp-keepalive` → the robot sends nothing, the app keeps its own
  `keepalive 5000` and STAYS Connected (liveness stays disabled at `-1` when
  the robot never announces an expectation). The additive rule is real.
- **Tooling:** fixed `scripts/ui_dump_parse.py` — it crashed (cp1251
  `UnicodeEncodeError`) on the magic-button gear glyph (U+2699) in a
  contentDescription during this smoke; `reconfigure(errors="replace")` for
  single-byte console codecs.
- **Retrospective** — MEMORY.md "Campaign 29 retrospective".

## Campaign 30 — protocol v1 doc/code sync + `keepalive <= 0` + dummy_gamepad (2026-08-21)

Scope (user-driven, full auto): make `DESIGN.md` "Gamepad protocol (source of
truth)" the future contract — document the v1 protocol fully (additive
features: robot→app keepalive read path, `custom <message>`, `keepalive <= 0`
disables the expectation, UDP transport, canonical `btn N down`), record the
robot-firmware / desktop-C++ "Known violations" ledger, align the app code
(`checkRobotLiveness` guard `> 0`), enforce the robot-side keepalive watchdog
in `DummyRobotServer`, and add the `dummy_gamepad.py` protocol-tracking client.
Single commit; Step 2 (upstream issues) starts after green CI.

| Estimated | Actual |
|-----------|--------|
| ~2 h | ~2 h 30 m (2026-08-21, incl. the hang-fix loop + Step 2 upstream issues) |

- **Protocol v1 documented** in DESIGN.md: "Protocol versioning" (v1 = no wire
  tag, additive; v2 deferred), symmetric "Keepalive semantics" (any received
  control message re-arms the single-shot timer; `keepalive <= 0` = disabled),
  `custom <message>` matrix row (specified, app-side deferred), and the "Known
  violations (review later)" ledger (robot firmware + desktop C++).
- **App code aligned:** `SenderService.checkRobotLiveness` now guards `> 0`, so
  a robot `keepalive 0` disables the liveness expectation (red-first test
  `keepaliveZeroDisablesRobotLiveness`).
- **DummyRobotServer** enforces the robot-side keepalive watchdog on its TCP
  port: `keepalive <ms>` (`> 0`) arms it, any message re-charges it, `<= 0`
  disarms it, and a silent client is dropped with a friendly `TCP!` ERROR line
  (the conformance signal). `custom <message>` accepted + logged.
- **`scripts/dummy_gamepad.py`**: protocol-tracking client (TCP/UDP), stdin
  commands with `wait <ms>`, and `--batch "c1;c2"` for scripting. Registered in
  `scripts/README.md`.
- **Retrospective** — MEMORY.md "Campaign 30 retrospective".

## Campaign 31 — package rename + minSdk 21 + custom message + release process change (2026-08-22)

Scope (user-driven, plan mode → build mode): (1) package rename
`com.trikset.gamepad` → `com.trikset.gamepad2` (140 files); (2) drop minSdk
23 → 21 with API-22 guard in `WifiDatagramBinder`; (3) add custom message
`EditTextPreference` to the robot network settings (5 locales); (4) bump
version to 2.42 (`versionCode = 212042`); (5) update release process to
signed tags + GH releases; (6) single `docs:` commit.

| Estimated | Actual |
|-----------|--------|
| — | ~40 min (plan-mode discussion + docs edits + gate) |

- **Package rename** — content replace in 131 Kotlin + resource files + `git mv`
  of `gamepad/` → `gamepad2/` trees in `src/main`, `src/test`, `src/androidTest`.
  `applicationId`, `AndroidManifest.xml`, CI scripts, AGENTS.md, MEMORY.md all
  updated. Compilation green on the first pass.
- **minSdk 21** — one-line change in `app/build.gradle`; `WifiDatagramBinder`
  guards `Network.bindSocket(DatagramSocket)` with `Build.VERSION.SDK_INT >= 22`;
  detekt `MagicNumber` on `22` fixed by `const val API_22 = 22`. Release notes
  describe it as "backward compatible with Android 5.0 Lollipop (API 21)".
- **Custom message** — `EditTextPreference` in `pref_robot.xml` (after
  transport); 5-locale strings; wiring in `MainActivitySettingsController.kt`
  reads `SK_CUSTOM_MESSAGE` and calls `sender.send("custom $value")`.
- **Release process** — AGENTS.md "Before release" rewritten: signed tag
  (`git tag -s`, 10 min GPG timeout guard), `assembleRelease`, APK attachment
  (not committed), release-notes skill, `gh release create --draft`, user
  publishes on web UI. DECISIONS.md entries for all three decisions.
- **Verification** — canonical gate green (spotless, test, lint, detekt,
  spotbugs, jacoco 0.951 line / 0.851 branch, jscpd 0 clones, translations
  138 keys in sync). Single feature commit `deed7fe` pushed to
  `origin/feat/global-refresh`.
- **Retrospective** — MEMORY.md "Campaign 31 retrospective".

## Campaign 32 — VideoPlayer abstraction + RTSP support via MediaPlayer (2026-08-22)

Scope (user-driven, auto mode): (1) extract a `VideoPlayer` interface so the
retry/self-heal controller drives whichever video sink is active; (2) add
`MediaPlayerVideoPlayer` for RTSP/H.264 streams using Android's built-in
`MediaPlayer` + `TextureView`; (3) add `MjpegVideoPlayer` wrapping the
existing `MjpegView`; (4) add `VideoPlayerFactory` that routes by URL scheme;
(5) update `MainActivity` to use `VideoPlayer` instead of `MjpegView` directly;
(6) document the WebRTC deferral and Media3 deferral in DECISIONS.md.

| Estimated | Actual |
|-----------|--------|
| — | ≈2 h 48 m (committed span 2026-08-22 14:04 → 16:52 +03:00) |

- **Video source files** — `video/VideoPlayer.kt` (interface),
  `video/MjpegVideoPlayer.kt` (MJPEG wrapper, uses executor + MjpegInputStream),
  `video/MediaPlayerVideoPlayer.kt` (RTSP via `MediaPlayer` + `TextureView`,
  surface-lifecycle-aware), `video/VideoPlayerFactory.kt` (routes by URL
  scheme: `rtsp://` → `MediaPlayerVideoPlayer`, everything else →
  `MjpegVideoPlayer`).
- **Layout** — `activity_main.xml` wraps the `MjpegView` and a new `TextureView`
  in a `FrameLayout`; the factory toggles visibility so only one surface is
  active at a time.
- **String-URI flow** — the video URI now flows as an opaque `String` (a
  `java.net.URL` cannot parse `rtsp://`), so RTSP reaches the players;
  per-player validation happens at open time instead of the removed
  `illegal_video_uri` settings-time toast (all 5 locales cleaned).
- **MainActivity refactor** — `video` field type changes from `MjpegView?` to
  `VideoPlayer?`; `restartVideoStream` uses `player.play(url)` with an
  `onPlayResult` callback; `setVideoUrl` recreates the player via factory on
  URL change; `setShowFps` delegates through the interface; lifecycle methods
  use `video.stop()` / `video.release()`.
- **Tests** — `MjpegVideoPlayerTest` (delegation, null/invalid/live-server
  open, live play), `MediaPlayerVideoPlayerTest` (null/pending/lifecycle),
  `VideoPlayerFactoryTest` (scheme routing), `MainActivityTest` updated (uses
  `StubVideoPlayer`; String-URL flow); parameterized 5 mapping tests
  (`ConnectionIndicator`/`VideoStatusIndicator`/`DiagLevel`/`MagicButtonSymbols`/
  `WheelController` — 16 → 6 methods) and consolidated reflection/pref helpers
  (`field`/`setField`/`method`/`setPref`) into `RobolectricTestBase`.
- **Coverage** — new video code dipped branch to 0.847; recovered to **0.852**
  (real-server + https-fallback tests) with the hardware-bound
  `MediaPlayerVideoPlayer` excluded from the gate (Surface/MediaCodec lifecycle,
  no `ShadowTextureView`; same rationale as the `MjpegView` render-thread
  exclusion). Final: **0.963 line / 0.852 branch**, jscpd 0 clones, translations
  137 keys in sync.
- **Docs** — DECISIONS.md: WebRTC deferral + Media3 deferral + toolchain-bump
  entries; DESIGN.md: "Video player abstraction" section; ROADMAP.md: this
  entry; MEMORY.md: Campaign 32 retrospective.
- **Media3 deferred** — not added to dependencies until device-specific
  `MediaPlayer` RTSP issues prove it necessary. The `VideoPlayer` interface
  makes the swap a drop-in change.
- **Verification** — canonical gate green (all steps); 3-variant `test` suite
  green twice; lint/detekt/spotbugs/jacoco/spotless/jscpd clean. On-robot RTSP
  verification stays a manual user step (`.PLAN.md`).

## Campaign 33 — Bug fixes + FPS + video fit mode + thread safety (2026-08-24)

Scope (user-driven, auto mode): (1) fix crash `CalledFromWrongThreadException`
on stream error (wrap `onStreamError` in `runOnUiThread`); (2) fix the same
bug class in `MediaPlayerVideoPlayer` (route all callbacks through
`mainHandler.post`); (3) FPS counter: integer format, hysteresis (≥2 change),
dark pill background, accent green color; (4) rename color resources to
semantic names (`greenlight→hud_accent_connected`, etc.); (5) add `ScaleMode`
enum (FIT/CROP) with user preference, default FIT; (6) add pixel-probe test
verifying P7/P8 video fidelity; (7) document P7/P8/P9 in DESIGN.md.

| Estimated | Actual |
|-----------|--------|
| — | ≈2 h 10 m (2026-08-24 09:30 → 11:40 +03:00) |

- **Crash fix** — `MainActivity.kt:305-308`: `onStreamError` now wrapped in
  `runOnUiThread` (was missing, unlike `onFirstFrameListener`). Systemic audit
  of all listener callbacks found one more unsafe pattern:
  `MediaPlayerVideoPlayer`'s `onPlayResult(false)` fired from a `MediaPlayer`
  internal thread and chained into `ConnectionFeedback.error()` which touches
  views. Fix: `MediaPlayerVideoPlayer` now injects `mainHandler` and routes all
  callbacks through `mainHandler.post`.
- **FPS** — `MjpegFrameRenderer.kt`: color changed from `Color.WHITE` to
  `hud_accent_connected`; format `%.1f` → `%d`; added hysteresis field
  `lastShownFps`; added `fpsPillPaint` (#88000000, 6px round rect) for
  readability. `MjpegView.kt:92`: `Color.WHITE` → `ContextCompat.getColor(context, R.color.hud_accent_connected)`.
- **Color rename** — `greenlight→hud_accent_connected`, `greendark→hud_accent_connected_dark`,
  `amber→hud_accent_connecting`, `red→hud_accent_error`. ~70 references across
  20 files updated. `values-night/colors.xml` also renamed.
- **Video fit mode** — `ScaleMode` enum (`FIT`/`CROP`), `MjpegFrameRenderer`
  defaults to `FIT`. `VideoPlayer` interface gains `var scaleMode`. Preference
  `videoCropToFill` (default `false` = FIT) in "Pads & video" category. 5-locale
  strings. Tests: 2 new FIT destRect tests; existing CROP tests explicitly set
  `scaleMode = ScaleMode.CROP`; `HudThemeTest` screenshot tests use `CROP`.
- **Pixel-probe test** — `videoZonesShouldBeCleanAndOverlaysShouldOnlyDarken` in
  `HudThemeTest`: 5 clean-zone probes match the source cat (1-bit tolerance for
  NATIVE Skia), 2 pad overlay probes differ, 2 scrim probes differ. Verifies P7
  (video fidelity) and P8 (overlay darkening).
- **Design principles** — P7 (video fidelity), P8 (overlay design flaw), P9
  (video processing research deferred) added to `DESIGN.md`.
- **Thread-safety audit** — all 10 callback sources classified (1 UNSAFE found
  and fixed, 9 SAFE confirmed). Documented in DECISIONS.md.
- **Verification** — 849 unit tests pass (0 failures, 0 errors). Translations
  key sync: 140 keys in sync across 5 locales. Build `releaseDebug` APK
  (4.71 MB) copied to `_apk/TRIKGamepad-2.42-API21-releaseDebug.apk`.
- **Retrospective** — MEMORY.md "Campaign 33 retrospective".

## Dreams / future roadmap (recorded 2026-08-19, user-suggested; no schedule)

Futures the user wants tracked as candidate campaigns; each needs a design pass
(interactive) before it becomes a scoped campaign. The UDP control transport
was **partially built in Campaign 27** (basic UDP + robot keepalive liveness);
the robustness tiers below are the remaining dream.

### `custom` command ✔ Shipped in C31 (2026-08-22)

The protocol spec now defines `custom <message>` (opaque plain-text message to
the robot, exposed to user programs) and the reference implementation
(`DummyRobotServer`) accepts and logs it. The Android app ships an
`EditTextPreference` in the robot network settings that sends `custom <value>`
on preference change — Campaign 31 closes the app-side gap.

### Protocol v2 (deferred design)

Campaign 30 documents the **v1** protocol as the contract (no wire tag,
additive changes only). **v2** is deferred and must stay v1-compatible (a v2
client ↔ v1 robot, and vice versa). Candidates, for when a robot can answer:
message ids / sequence numbers (UDP, possibly TCP), robot status replies to
numbered messages (error codes, `btn` state tracking), and **data packets /
telemetry (v2-only)**. Framing and semantics need an interactive design pass
before scoping.

### DummyRobotServer validation oracle + fault injection (deferred)

The reference server already enforces the keepalive watchdog and logs every
line; its only `ERROR` output is the watchdog's conformance-signal disconnect.
Deferred re-design: a semantic-validation oracle (ERROR logs for protocol
violations a client commits) and fault-injection scenarios (e.g. a `btn N`
toggle that stops the robot→app keepalive) so third-party implementations can
be checked against robot failure modes. Default start stays the normal
situation.

### UDP control transport (in addition to TCP)

The robot will soon support UDP control alongside TCP. Campaign 27 shipped the
baseline: one newline-terminated command per datagram, optimistic Connected,
per-keepalive-tick pad/wheel re-send, and the robot-keepalive liveness rule
(any received message resets the clock; `keepalive <ms>` sets the interval;
`ms + 2000` gap → disconnect) — see DESIGN.md "Gamepad protocol (source of
truth)". Still open (the dream):

- **Command frame format** — resolved in C27 (newline-terminated, one command
  per datagram; a selectable `SK_TRANSPORT` setting, not a replacement).
- **Loss/ordering robustness tiers:** UDP drops and reorders — pad/button
  commands are idempotent, but the per-keepalive-tick resend only converges
  the *latest state*, it does not ack. Future tiers: keepalive ACK → button
  ACK → monotonic sequence numbers. Deferred (documented in DESIGN.md; not a
  contract yet).
- **The half-open story** — resolved in C27: UDP "connected" is optimistic on
  first send; liveness is the robot-keepalive clock, not a socket state.
- **Wi-Fi binding** — resolved in C27: `WifiDatagramBinder` applies
  `Network.bindSocket(DatagramSocket)` (S13).

### Additional video-streaming formats (SBC-typical)

The robot is Linux-based and can serve typical single-board-computer stream
formats; pick a small set that is easy to support on Android. Candidates (to
narrow down in the design pass, not a commitment):

- **RTSP (H.264/H.265)** — `MediaPlayer`/ExoPlayer handle it; the obvious
  SBC/robot-camera standard.
- **HLS (.m3u8)** — also `MediaPlayer`-native, trivial from ffmpeg on the SBC.
- **MPEG-TS over UDP / plain H.264-over-HTTP** — also `MediaPlayer`-friendly.

Design question for the pass: keep MJPEG as the baseline (TRIK-native) and add
a player abstraction over `MjpegView` vs `MediaPlayer`, so the retry/self-heal
controller (`VideoRetryController`) drives whichever sink is active.

### SBC as a separate video source (video IP ≠ control IP)

Sometimes a single-board computer accompanies the TRIK controller for video
streaming / algorithms, so the video source IP can differ from the control IP.
This is **already the designed contract** (`DESIGN.md` S4/S6: video is fully
decoupled from control; different-host video self-heals — shipped in Campaign
25's Option B retry), so no design change is needed; the SBC case is just a
concrete instance. Note it so future format work (above) never re-couples
video to the control host.

### Robot-initiated telemetry HUD + telemetry screen

The robot drives its own telemetry UI (extends the gamepad to *show* robot
data): it sends a **data-structure description** (which fields exist, their
order, the var name, the UI label, the expected data type, the string format),
then streams **named values** for those fields, and the app renders them in two
surfaces:

- **HUD overlay** — a small-font readout over the video (the robot picks the
  default field set; the user can override per field);
- **Telemetry screen** — a separate full screen (settings-like, textual)
  showing **all** fields refreshing: var name, data type, description, current
  value, plus a per-field "show on HUD" toggle that overrides the robot's
  default.

Not all vars are useful in the HUD, hence the separate screen is the
comprehensive view and the HUD is the curated subset. The wire format is open
(likely JSON) and the transport is undecided — both are first-order design
questions. **Recorded 2026-08-20 (user-suggested); no design work yet —
refine interactively next time.** **Re-scoped 2026-08-21 (Campaign 30):**
telemetry / data packets are **v2-only** by design (DESIGN.md "Protocol
versioning") — the v2 framing + schema design pass is the prerequisite.

Design questions to resolve before scoping:

- **Transport:** same TCP control socket (interleaved with commands +
  keepalive) or a separate stream/channel? If separate, how is it routed (a
  new socket must keep S13 Wi-Fi binding)? Does telemetry require the control
  connection to be up, or can it be video-only/spectator?
- **Format & framing:** JSON lines over TCP? A distinct prefix/line shape so
  the existing plain-text protocol parser tolerates unknown telemetry lines
  (backward compatible)? Is the description versioned/schematized?
- **Lifecycle:** when is the description sent (once at connect, per
  reconnect, on change)? What if the app opens mid-stream (misses the
  description)? Does the app persist the last schema + values across restarts,
  or is telemetry session-only?
- **Types & formats:** which data types (int/float/string/bool/enum?) and
  string formats (printf-style? units? decimal places?) must the description
  support? Are labels robot-localized (the app does not translate robot text)?
- **Update rate & rendering:** how fast do values arrive; throttle to
  delta-cell updates (never a full re-render per packet)? Batching?
- **HUD overlay placement:** the video is center-cropped and the HUD already
  hosts pads, magic buttons, the status pill and the error pill — where does
  the readout go, when is it shown (video playing? toggled?), and how does it
  avoid collision (existing "bringToFront / overlay eats taps" and alpha traps
  apply)?
- **Telemetry screen UX:** entry point (gear → telemetry row?), settings-like
  layout, and whether the per-field "show on HUD" override is persisted
  (`SharedPreferences`, the app's setting convention) or session-only.
- **Degradation & bounds:** malformed description, unknown var, description
  without values, values without description — control + video must keep
  working (P2/P4-style self-healing); cap the field count / layout bounds so a
  misbehaving robot cannot break the UI.
- **Empty-video case:** the telemetry screen and the HUD readout should work
  with no video stream (the overlay simply has no video under it).
- **i18n:** UI chrome (screen title, "Show on HUD" label) follows the 5-locale
  convention; robot-sent labels are opaque text, not translated.
