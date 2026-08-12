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
