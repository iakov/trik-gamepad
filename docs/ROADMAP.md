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

## Campaign 3 — strict full-project review (DONE 2026-08-08)

Full code audit + web best-practice research; durable reference in
`docs/architecture.md` (domain knowledge + pitfalls), findings in the "Campaign
3 execution run" MEMORY entry. Priorities: **P0 correctness → P1 leak fix → P2
architecture → P3 hygiene** (each item one commit, gate, push; detailed plan:
`.PLAN.md` "Campaign 3").

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
`e0dcc18`; execution record: `.PLAN.md` "Campaign 4" + MEMORY "Campaign 4
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
- ✅ Final retrospective → AGENTS.md / MEMORY.md / TESTING.md / `.PLAN.md`;
  last-known-good CI run id updated (`31232406163`, first fully-green run).
