# MEMORY.md — trik-gamepad

<!-- encoding: utf-8 -->

Scope: Main memory for AI agents — architecture, CI quirks, workflows, and
design decisions. Hold every *why* and *detail* that AGENTS.md rules refer to.
AGENTS.md is the "what to do" front door; this file is the store it points into.
Structure: Build & layout → Testing → App protocol → CI quirks → Workflows →
Design decisions (dated entries).

## Build & layout

### Which code is alive

- `as/` is the only maintained project. `xamarin/` is an unfinished F# port
  (git history: "Draft of F# version. Raw translation. Not finished yet."),
  not built by CI — never edit.
- `_apk/` holds committed release APKs with versioned names
  (`TRIKGamepad-1.40-21.apk`).
- The `as/` source tree is **Java-only** (0 `.kt`/`.kts` files) even though the
  Kotlin Android plugin is applied; `compileDebugKotlin` reports NO-SOURCE.

### Keystore path (subtle)

`as/build.gradle` sets `storeFile file('../../android-keystorage.jks')`
relative to the project dir `as/`. Two levels up from `as/` is the **parent of
the repo root**, not the repo root — earlier AGENTS.md wording ("repo root")
was wrong. The keystore is gitignored via `**/*.jks` (never committed).
Because `defaultConfig` applies the signing config, **even debug builds
require the file**; builds fail without it. Key alias is `gamepad`.

### Versioning

`appMajorVersion`/`appMinorVersion` are hand-set at the top of `as/build.gradle`
(currently 1.40). `versionCode = minSdk*10000 + abiCode*1000 + major*100 + minor`
(never set by hand), `versionName = "1.40"`, `versionNameSuffix = "-API21"`.
Bump `appMinorVersion` per release; semantic 1.x is kept intentionally
(Play Store requires a strictly increasing versionCode per app — date-based
versions risk collisions with the `minSdk*10000 + ...` formula).

### SDK/local setup

- `as/local.properties` (gitignored) points at the user-local Android SDK via
  `sdk.dir=<path>`; the drive-letter colon MUST be escaped (e.g.
  `C\:/Users/<user>/Android/Sdk` style) or lint's `PropertyEscape` check fails
  the build.
- JDK 21 (Microsoft OpenJDK) works with Gradle 8.11.1 + AGP 8.9.0.
- AEHD (Android Emulator Hypervisor Driver 2.2) is installed for local
  emulator acceleration; verify with `emulator -accel-check`. Installer lives
  in the SDK: `extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat`.

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
restructured. Code changes and quality gates (google-java-format, Checkstyle,
SpotBugs+find-sec-bugs, Error Prone, JaCoCo, Mockito, pre-commit config,
Dependabot, CI hardening) were **postponed** by the maintainer — do not add
them without explicit request. The full execution plan and rationale live in
`.PLAN.md`.

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
