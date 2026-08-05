# MEMORY.md — trik-gamepad

<!-- encoding: utf-8 -->

Scope: Main memory for AI agents — architecture, CI quirks, workflows, and
design decisions. Hold every *why* and *detail* that AGENTS.md rules refer to.
AGENTS.md is the "what to do" front door; this file is the store it points into.
Structure: Build & layout → Testing → App protocol → CI quirks → Workflows →
Design decisions (dated entries).

## Build & layout

### Which code is alive

- The repo has the **canonical Android layout** at the root: `settings.gradle`
  (`rootProject.name = 'trik-gamepad'`, `include ':app'`) + the `app/` module.
  All gradle commands run from the repo root.
- `_apk/` holds committed release APKs with versioned names
  (`TRIKGamepad-1.40-21.apk`).
- The `app/` source tree is **Java-only** (0 `.kt`/`.kts` files) even though the
  Kotlin Android plugin is applied; `compileDebugKotlin` reports NO-SOURCE.
  Pure-Kotlin migration is planned (gated on green CI + 85% coverage, `.PLAN.md`).
- `xamarin/` was an unfinished F# port — **deleted** during the revival
  restructure (git history preserves it). `imgs/` moved to `docs/img/`.

### Keystore path (subtle)

`app/build.gradle` sets `storeFile file('../android-keystorage.jks')` relative
to the project dir `app/`. One level up from `app/` is the **parent of the repo
root** — the keystore deliberately lives *outside* the workdir (guarded-push
rule: no secrets in the repo). The keystore is gitignored via `**/*.jks`
(never committed). Signing is applied **conditionally** — `if (file('../android-keystorage.jks').exists())` — so debug builds in CI fall back
to the auto-generated debug keystore and never need the file. Locally the
keystore is present, so release builds sign normally. Key alias is `gamepad`.

### Versioning

`appMajorVersion`/`appMinorVersion` are hand-set at the top of `app/build.gradle`
(currently 1.40; next release 1.41 per `.PLAN.md` D19).
`versionCode = minSdk*10000 + abiCode*1000 + major*100 + minor`
(never set by hand), `versionName = "1.40"`, `versionNameSuffix = "-API21"`.
Bump `appMinorVersion` per release; semantic 1.x is kept intentionally
(Play Store requires a strictly increasing versionCode per app — date-based
versions risk collisions with the `minSdk*10000 + ...` formula).

### SDK/local setup

- `local.properties` (gitignored, at repo root) points at the user-local
  Android SDK via `sdk.dir=<path>`; the drive-letter colon MUST be escaped
  (e.g. `C\:/Users/<user>/Android/Sdk` style) or lint's `PropertyEscape` check
  fails the build.
- JDK 21 (Microsoft OpenJDK) works with Gradle 8.14.5 + AGP 8.13.2 (planned
  toolchain per `.PLAN.md`).
- AEHD (Android Emulator Hypervisor Driver 2.2) is installed for local
  emulator acceleration; verify with `emulator -accel-check`. Installer lives
  in the SDK: `extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat`.
- SDK platforms installed: 23, 30, 35, 36, 36.1. `compileSdk/targetSdk 36`
  needs `platforms;android-36` — installed via
  `sdkmanager "platforms;android-36"`.

### Python tooling (uv + repo-local venv)

All Python tools run through uv with a **repo-local `.venv`** (gitignored):
`uv venv` then `uv pip install --python .venv pre-commit mdformat`. Run them as
`.venv/Scripts/pre-commit.exe` / `.venv/Scripts/mdformat.exe` or `uvx`. Do NOT
use `uv tool install` (global, machine-level) or system pip. The git pre-commit
hook (`.git/hooks/pre-commit`) points at the venv Python via `INSTALL_PYTHON`.

## Testing

### Test task structure

`./gradlew test` runs the SenderServiceTest suite for **all three build types**
(`debug`, `release`, `releaseDebug`) — three unit-test tasks, executed in
parallel JVMs.

### Ephemeral ports (hard-won)

The unit-test `DummyServer` (inner class of `SenderServiceTest`) binds an
**ephemeral port** (`new ServerSocket(0)`); the client targets
`server.getPort()`. Fixed ports (historically `localhost:12345` + shifts) are
forbidden here: the parallel variants collided with `BindException` cascades
and flaky asserts. The androidTest `DummyServer.java` is a *different* class
and still binds `localhost:12345` — don't merge or confuse the two.

### Deterministic awaits

The server thread is async (accept/read on its own thread). Tests must await
server state via `CountDownLatch`: `awaitConnection()` (after `accept()`) and
`awaitCommands()` (after reading N lines), each with a 5 s timeout, before
asserting. The constructor binds the socket synchronously so the port is
guaranteed listening before the client connects.

### Robolectric specifics

`@RunWith(RobolectricTestRunner.class)`, `@LooperMode(PAUSED)`,
`@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})`
(the `[21]`/`[34]`/default suffixes in reports are the per-SDK runs), and a
`PausedExecutorService` for the `SenderService` executor — `mExecutor.runAll()`
drives background tasks deterministically, then `shadowOf(getMainLooper()).idle()`.

### Emulator prerequisites

Instrumented tests (`KeepAliveTests`, `MainWindowTests`, `SettingsTests`) are
Espresso + AndroidX Test Orchestrator (`testOptions.execution ANDROIDX_TEST_ORCHESTRATOR`, `animationsDisabled`). They need a running
emulator/device; without AEHD the x86_64 images are unusable. Local run:
boot an AVD (`Simple_Phone_API35`) with `-no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect`, wait for `sys.boot_completed=1`, then
`./gradlew connectedDebugAndroidTest`.

## App protocol

### SenderService

- One TCP connection to the robot, default `192.168.77.1:4444`; connect timeout
  `TIMEOUT = 5000` ms; `setTcpNoDelay(true)`, `setSoLinger(true,0)`,
  `setTrafficClass(0x0F)`. Connect and send run on a single-thread executor.
- Commands are newline-terminated plain text: `pad1 x y`, `pad2 x y`,
  `btn N down`, `wheel <angle>`, `keepalive <ms>`.
- `send()` lazily connects (`connectAsync()` guarded by `mSyncFlag`); a failed
  send is detected via `mOut.checkError()` in `SendCommandAsyncTask.onPostExecute`
  → `disconnect("Send failed.")`.
- `setTarget()` disconnects when host/port changes.

### Keepalive

`DEFAULT_KEEPALIVE = 5000` ms, `MINIMAL_KEEPALIVE = 1000` ms. The `KeepAliveTimer`
(a `java.util.Timer`) schedules every `keepaliveTimeout - 300` ms ("300 in order
to compensate ping"), sending `keepalive <ms>`. Sending any command restarts
the timer.

### MJPEG video

`com.demo.mjpeg` package (`MjpegView`, `StartReadMjpegAsync`). Default URI
`http://<host>:8080/?action=stream`, rebuilt from `SK_VIDEO_URI`; changing the
host address rewrites the video URI to match. The stream is force-restarted
every 30 s by re-posting a `mRestartCallback` runnable in
`MainActivity.onResume` (drop the HTTP connection and re-open). Cleartext HTTP
is enabled via `android:usesCleartextTraffic="true"`.

### Settings

Keys are `SK_*` constants in `SettingsFragment`: `SK_HOST_ADDRESS`, `SK_HOST_PORT`,
`SK_SHOW_PADS`, `SK_VIDEO_URI`, `SK_WHEEL_STEP`, `SK_ABOUT_SYSTEM`, `SK_KEEPALIVE`.
Stored via legacy `PreferenceManager`/`android.preference` APIs; wheel angle uses
`SK_WHEEL_STEP` for the dead-zone step. `BuildConfig.VERSION_NAME` feeds the
About/system-info field.

## CI quirks

### CircleCI

- `.circleci/config.yml` is v2.1. Jobs: `build` (`assembleDebug` +
  `assembleDebugAndroidTest`, both with `-PpreDexEnable=false`), `test_local`
  (`./gradlew test`), `test_instrumented` (Firebase Test Lab, needs
  `GCLOUD_SERVICE_KEY` + `GOOGLE_PROJECT_ID`). Image `circleci/android:api-30`.
- The `build` job signs with the release config — a CI build would also fail
  without the keystore present at the resolved path. (Release signing is
  local-only by policy; CI never holds the keystore.)
- DummyServer tests bind `localhost:12345` inside the app process, so they run
  on-device via Firebase without firewall changes.

## Workflows

### Branch/PR

- Fork-only workflow: all work lives in the personal fork (origin; run
  `git remote -v`). Never create PRs against upstream `trikset/trik-gamepad`.
- Base is fork `master` (tracks `origin/master`). Do NOT branch from the stale
  local `merge` branch (an older superseded line kept around by choice).
- Always branch + PR, never push to master directly (self-imposed; `gh` is
  authenticated as the fork owner). Conventional Commits; within-fork
  PRs via `gh pr create --base master`; squash-merge via `gh pr merge --squash`;
  gate on green CI.

### Release

1. Gates: green CI, 0 open PRs, 0 security alerts.
1. Bump `appMinorVersion` in `as/build.gradle`; commit + PR.
1. Build `assembleRelease` locally (keystore present), smoke-test the APK.
1. Commit the APK to `_apk/` with a versioned name.
1. Generate notes via the `release-notes` opencode skill; review the draft
   manually — never auto-publish.

## Design decisions

Dated entries, each with context → decision → rationale → consequences.

### [2026-08-05] Deterministic unit tests (ephemeral ports + latches)

**Context:** `./gradlew test` intermittently failed. `DummyServer` bound its
`ServerSocket` on a background thread and asserted client/server state
immediately after `runAll()`+`idle()`. On fast machines the client connected
before the server bound → `ConnectException`, failed `isConnected()` assert, and
a leaked accept-thread kept the port bound → cascading `BindException`. The
three test variants (`debug`/`release`/`releaseDebug`) additionally ran in
parallel JVMs over the same fixed ports (`localhost:12345` + shifts) →
cross-JVM `BindException`s.

**Decision:** bind synchronously in the constructor; assert only after
`CountDownLatch` awaits (`awaitConnection`/`awaitCommands`, 5 s timeout); bind
**ephemeral ports** (`ServerSocket(0)`) and target `server.getPort()`.

**Rationale:** fixed ports cannot survive parallel JVMs; latch awaits remove the
accept/read race; constructor-bind removes the bind-late race.

**Consequences:** 3× consecutive full `test` runs green. `DummyServer.DEFAULT_PORT`
no longer exists in the unit test (the androidTest `DummyServer.java` still has
it).

### [2026-08-05] local.properties lint escape

**Context:** `sdk.dir=C:/Users/...` (and `C\:\\...`) made `./gradlew lint` fail
with `PropertyEscape` (3 errors → 2 errors). The detector wants only the
drive-letter colon escaped.

**Decision:** `sdk.dir=C\:/Users/<user>/Android/Sdk` (generic pattern; never
commit a concrete machine path).

**Consequences:** lint green (0 errors). The file is gitignored; only this
machine is affected.

### [2026-08-05] AEHD for local emulator

**Context:** `emulator -accel-check` returned code 6 ("hypervisor driver not
installed"); x86_64 images unusable. HypervisorPresent=False, VBS off — no
conflict with AEHD.

**Decision:** installed AEHD 2.2 via `silent_install.bat` (UAC). Now
`accel-check` = 0.

### [2026-08-05] Keystore path correction

**Context:** AGENTS.md claimed the keystore lives at "repo root". The relative
path `../../android-keystorage.jks` from `as/` resolves one level **above** the
repo root.

**Decision:** document that the keystore resolves to the parent of the repo
root, without naming a concrete machine path; AGENTS.md now just says "see
MEMORY.md".

### [2026-08-05] Docs culture adoption (from trik-lobe-server)

**Context:** reviewed the lobe-server project's docs (AGENTS.md = rules only,
MEMORY.md = rationale, TESTING.md = strategy, release-notes skill, uv-managed
tooling). Culture decisions: strict branch+PR discipline, Conventional Commits,
"Root cause/Profit/Trade-offs/Verification" PR bodies, repo-root `.tmp/`,
suppression policy, error-leaves-a-trace.

**Decision:** adopted the AGENTS/MEMORY/TESTING split and the release-notes
skill. Python tooling is uv-managed (`uv tool install pre-commit`, `uvx mdformat`); `%USERPROFILE%\.local\bin` must be on PATH for the git hook.

**Consequences:** this file was created; TESTING.md created; AGENTS.md
restructured. Quality gates and the toolchain upgrade were initially postponed
by the maintainer (recorded in the next entry, "Toolchain + quality gates");
the maintainer later approved them. The full execution plan and locked
decisions D1–D19 live in `.PLAN.md`.

### [2026-08-05] Toolchain + quality gates approved (single main flavor)

**Context:** the maintainer reviewed the lobe-style plan again and granted
freedom to upgrade tooling/deps. Constraints: backward-compatible with 99% of
Androids; tests-first (TDD) so features keep working; keep Java sources this
release (pure-Kotlin later); single main flavor — a legacy flavor is postponed.

**Decision (D14–D19, full rationale in `.PLAN.md`):** minSdk 21 stays
(AndroidX floor for libs released before June 2025; 99.8% coverage vs 98.0% at
minSdk 23). Toolchain goes to Gradle 8.14.5 + AGP 8.13.2 + Kotlin 2.x (NOT AGP
9 — needs settings.gradle/plugins-DSL migration on this legacy single-module
`apply plugin:` layout). `compileSdk/targetSdk/maxSdk 36` (Play requires
targetSdk 36 from 2026-08-31). Deps pinned to minSdk-21-compatible freshest:
core 1.16.0 / appcompat 1.7.1 (core 1.17+ raises minSdk to 23). Version 1.41.

**Consequences:** `.PLAN.md` rewritten with a six-commit sequence (docs →
gates → format sweep → test fix → toolchain/deps/version → docs
retrospective). Biggest risk: targetSdk 34→36 edge-to-edge enforcement on the
fullscreen gamepad UI — needs an API 36 emulator smoke test.

### [2026-08-05] Version/SDK data snapshot (for future sessions)

**Context:** researched current Android distribution and freshest versions
(Apr 2026 Statcounter via apilevels.com; Google Maven / Maven Central).

**Data (cumulative coverage):** minSdk 16=99.9%, 19=99.9%, 21=99.8%,
23=98.0%, 26=96.1%, 28=93.5%, 30=86.9%, 34=54.5%, 36=22.3%. Play requires
targetSdk 36+ after 2026-08-31. AndroidX libs released after June 2025 require
minSdk 23.

**Freshest stable (2026-08-05):** Gradle 8.14.5 (9.6.1 pairs with AGP 9); AGP
8.13.2 (9.3.1 is freshest stable but breaking); Kotlin 2.4.10; appcompat 1.7.1,
core 1.19.0 (but 1.17+ needs minSdk 23), annotation 1.10.0, preference 1.2.1,
tracing 1.3.0; androidx.test runner 1.7.0, espresso-core 3.7.0, rules 1.7.0,
orchestrator 1.6.1; Robolectric 4.15.1, Mockito 5.18.0, junit 4.13.2,
commons-io 2.22.0. Local SDK has platforms 30/35; android-36 installable.

### [2026-08-05] Fork-only workflow (no upstream PRs)

**Context:** the repo has two remotes — `origin` = the personal fork,
`upstream` = `trikset/trik-gamepad` (canonical). A planned branch+PR flow was
refined to never touch upstream.

**Decision:** all work lives on the fork only; PRs are created within the fork
(`gh pr create --base master`, base = fork master). Never create cross-repo
PRs against `trikset/trik-gamepad`; never sync/push to upstream.

**Rationale:** the maintainer develops and merges on their own fork; upstream is
a distribution point, not a collaboration target for this project.

**Consequences:** AGENTS.md "Before push / PR" and this Workflows section state
the fork-only rule; future sessions must not propose upstream PRs.

### [2026-08-05] lint.xml MissingTranslation relaxation

**Context:** resources are English-only (`resourceConfigurations += ['en']`);
`lint.xml` downgrades `MissingTranslation` to warning so the absence of other
languages is expected, not an error.

**Decision:** keep the downgrade; it is the intended convention, recorded here
per the suppression policy.

### [2026-08-06] Emulator: AVD config is the source of truth (slow-boot lesson)

**Context:** an ad-hoc emulator launch used `-gpu swiftshader_indirect -no-snapshot`, overriding the AVD's declared `hw.gpu.mode=host` + quickboot.
The AVD cold-booted under software rendering at 1080×2340@440dpi and stayed
`offline` for ~2.7 h. The maintainer then fixed `config.ini`
(`hw.gpu.mode=host`, `fastboot.forceFastBoot=yes`,
`firstboot.bootFromDownloadableSnapshot=yes`, 6 cores, 4 GB) and created two
`emu-launch*.bat` launchers to teach the correct invocation.

**Decision:** the AVD's `config.ini` is the source of truth; CLI flags must
not fight it. Correct local launch: `emulator -avd Simple_Phone_API36 -no-window -no-audio -no-boot-anim -gpu host` (snapshots stay enabled). The
launcher `.bat` files were training material and were deleted.

**Consequences:** relaunch booted in **~39 s** (`Boot completed in 38877 ms`,
NVIDIA GPU translator) vs ~2.7 h. Quirk recorded: emulator 37.1.11 logs
"configured it not to save on exit" and `default_boot` snapshot failed to load
even though `config.ini` declares `firstboot.saveToLocalSnapshot=yes` — a
snapshot-policy discrepancy to revisit. Never pass `-no-snapshot` for local
iteration; only for pristine CI-style cold boots.

### [2026-08-06] Operational rules for command hygiene

**Context:** the agent stalled "staring at the emulator" — launched an async
process, then polled it instead of advancing the work queue. Root cause:
serialized the pipeline on an async resource (needed only later), and treated
polling as progress.

**Decision:** every command gets a reasonable timeout + a log tee
(`app/build/<task>.log` or `.tmp/`); expected-vs-actual time is compared;
≥1.5× → analyze the wrong guess; slow commands → research + tune repeatable
tooling + document quirks. Async tools: capture `Start-Process -PassThru`,
verify liveness immediately, wait for the readiness signal with timeout, then
continue independent work — never stall on a poll. (Rules in AGENTS.md, this
rationale here.)

### [2026-08-06] Revival restructure (canonical layout, delete garbage)

**Context:** the maintainer approved a full revival: canonical Android layout,
quality gates, coverage to 85%, then pure-Kotlin migration; no release, no PR.
`as/` (legacy single-module) was replaced by `settings.gradle` + `app/`.

**Decision (R1–R15, full detail in `.PLAN.md`):** `settings.gradle` +
`app/` module at repo root; delete `xamarin/`, `as/import-summary.txt`,
Eclipse junk, `.local_development.db`; `imgs/` → `docs/img/`; retire CircleCI
→ GitHub Actions; conditional signing (keystore stays outside the workdir);
toolchain Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.x / SDK 36 / minSdk 21;
coverage gate starts 60% and ratchets to 85% before Kotlin migration;
MJPEG reconnect-on-error replaces the 30 s forced restart.

**Consequences:** all gradle commands run from the repo root; keystore path
changed `../../` → `../` (same file, `trik\android-keystorage.jks`). The
restructure was a pure `git mv` so history is preserved.
