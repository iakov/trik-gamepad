# AGENTS.md — trik-gamepad

<!-- encoding: utf-8 -->

Scope: Action triggers, guardrails, and commands for AI agents.
Memory: Details, architecture, design decisions, and quirks explanations live
in `MEMORY.md` (pull sections on demand) — never duplicate rationale here.
Testing strategy lives in `TESTING.md`.

Every line must answer: "Would an agent likely miss this without help?" If not, cut it.
Removing a documented rule changes agent behavior — only delete if provably incorrect.

## Project

Android app (mixed Kotlin/Java, currently Java sources — 0 `.kt` files) that
mimics a gamepad to control TRIK robots. It sends plain-text commands over a
TCP socket and streams MJPEG video over HTTP. Pure-Kotlin migration is planned
after the 85% coverage gate passes (see `.PLAN.md`).

## Layout

- Canonical Android layout at repo root: `settings.gradle` + `app/` module.
  All gradle commands run from the repo root (`./gradlew ...`).
- `_apk/` — committed release APKs (historical).
- `.github/workflows/` — CI: build, Robolectric unit tests, lint/checkstyle/
  spotbugs/jacoco gates, instrumented tests on emulator.
- `.opencode/skills/` — opencode skills (e.g. release-notes).
- `docs/img/` — screenshots/logos.
- `.venv/` — repo-local uv virtualenv (pre-commit, mdformat); gitignored.

## Build (from repo root)

- Planned toolchain (locked in `.PLAN.md`): Gradle 8.14.5, AGP 8.13.2, Kotlin 2.x, Java 11. `compileSdk 36`, `targetSdk 36`, `minSdk 21`, `maxSdk 36` — single main flavor (D14/D15). Do not migrate to AGP 9 / Gradle 9 without a settings.gradle restructure.
- `settings.gradle` at repo root: `rootProject.name = 'trik-gamepad'`, `include ':app'`.
- Signing: `app/build.gradle` applies a release signing config conditionally — only when `file('../android-keystorage.jks')` exists. Debug builds fall back to the auto-generated debug keystore in CI. See MEMORY.md "Build & layout" for gitignore status and how the path resolves.
- Version is hand-set at the top of `app/build.gradle` (`appMajorVersion`/`appMinorVersion`, currently 1.41). `versionCode` is computed (`minSdk*10000 + major*100 + minor`), `versionNameSuffix` is `-API<minSdk>`. Bump `appMinorVersion` for a release; never set versionCode by hand.

## Hooks

Action triggers for AI agents. When adding/removing tools or changing
configurations, update this section and the referenced config files.

### On session init

- Read this file, `MEMORY.md` header + section list, `TESTING.md`, `app/build.gradle`, and `.github/workflows/ci.yml`; pull MEMORY sections on demand.
- Don't talk to the user before session warm-up is complete.

### Before commit

- When pre-commit is installed, run `.venv/Scripts/pre-commit run --all-files`; otherwise at least `uvx mdformat` on changed `.md` files.
- New tool/config → update this section and `MEMORY.md`.

### Before push / PR

- Fork-only workflow: work lives in the personal fork (origin remote; run `git remote -v` for the URL). Never create PRs against upstream `trikset/trik-gamepad`.
- Branch from fork `master` (== `origin/master`); name `feat/`, `fix/`, `docs/`, `style/`, `refactor/`, or `chore/`.
- Never push to `master` directly — always a within-fork PR (`gh pr create --base master`), squash-merged after green CI (see Guardrails).
- Re-validate from repo root: `./gradlew test` and `./gradlew lint`.
- If `AGENTS.md` changed: `git diff HEAD -- AGENTS.md`, check every added/removed line against the boundary test (see Guardrails — Documenting decisions).

### Before test / command

- From repo root: `./gradlew test` (Robolectric, no device needed); a single test via `./gradlew testDebugUnitTest --tests "com.trikset.gamepad.SenderServiceTest.<method>"`.
- Instrumented tests need a running emulator/device (AEHD hypervisor required — verify with `emulator -accel-check`).

### Operational rules (command hygiene)

- **Every command runs with a reasonable timeout**, scaled to the task (gradle build ~10 min, downloads ~10 min, emulator boot ~5 min).
- **Every command is logged**: tee output to `app/build/<task>.log` (gitignored) or `.tmp/`; never run blind. On timeout, read the log and find root cause before re-running.
- **If a command takes ≥1.5× the expected time, analyze the wrong guess.** Record expected vs actual for each command.
- **A wrong guess often means an option was not set properly** — re-audit the invocation (e.g. the AVD's `hw.gpu.mode=host` config was the truth that CLI flags must not override).
- **Slow commands → research (incl. web), apply best practices, tune repeatable tooling** — fix the tooling, not the symptom; document quirks and findings in `MEMORY.md`.
- **Async tools**: capture a process handle (`Start-Process -PassThru`), verify liveness immediately, wait for the readiness signal with a timeout, then continue independent work — never stall on a poll.
- **Never pipe a long-lived child through `Select-Object`/`Tee-Object`**: Gradle spawns a daemon that inherits the parent's stdout/stderr pipe handles, so a pipeline never sees EOF and the command blocks until timeout even though Gradle finished in seconds. Redirect to a file instead (`& gradlew ... *> log` or `Start-Process -Wait -RedirectStandardOutput log`), read the file after, and use short timeouts for probes. Use `gradlew --stop` / `--no-daemon` for one-shot probe runs so no daemon lingers.
- **"Exit 0" ≠ the tool ran**: static analyzers (PMD 7 drops invalid rule names; checkstyle/spotbugs can silently no-op) may exit clean with zero files analyzed. Re-run with `--info`/`--rerun-tasks` and grep for the analysis actually loading config + analyzing sources before trusting a green result.
- **Static state leaks across Robolectric test classes**: `SenderService` keeps `keepaliveTimeout`/`mConnectTask` static; tests that touch it must reset both via reflection in `@Before`/`@After`, or the 3-variant suite flakes only on CI (SDK-23). Test UI logic without a live TCP dependency when possible (assert `PausedExecutorService.runAll()` count, not server arrival).
- **CI `script:` blocks must be plain POSIX `sh`** — no `\` line continuations or `{ ...; }` brace groups (they collapse into "Syntax error: end of file unexpected"). Validate the extracted script with `sh -n` before pushing.
- **Distinguish infra from code failures**: `Failed to resolve action download info` (GitHub Actions) and `adb ... exit code 1` during the runner's boot poll are transient infra/emulator flakes, not code defects. Check which job/step failed and whether it is boot vs tests before changing code. Keep a note of the last known-good CI run id.
- **Apply documented class traps before writing tests against a class**: MEMORY.md records hard-won hazards per class (e.g. `SenderService` static state, `DummyServer` sync-bind). When adding tests to a known-tricky class, read that class's MEMORY.md/TESTING.md entry FIRST and apply every documented trap in the first draft — re-discovering them costs CI runs (the session hit the static-state and async-bind flakes twice each).
- **Run the full 3-variant suite twice before pushing test changes**: `./gradlew test` runs debug/release/releaseDebug in parallel JVMs; static-state and timing flakes surface only under full-suite or second-run conditions, not single-test runs. New/edited tests get `test` twice locally before push.
- **Generate lint baselines with the aggregate `lint` task, not `lintDebug`**: a baseline from `lintDebug` misses issues the aggregate task reports, so CI fails on a "new" issue that is actually in-scope. Env-dependent checks (e.g. `OldTargetApi`) cannot be baselined — suppress them in `lint.xml`.

### On tool error

- Stop immediately; identify the root cause before proceeding. Fix second, skip third.
- Transient infra failures (network, CI outage) ≠ code errors: verify state, retry with backoff, then report.
- Every error leaves a trace: capture the lesson in `AGENTS.md` (rules) or `MEMORY.md` (details) before moving on.

### Before release

- Gates: green CI, 0 open PRs, 0 security alerts.
- Bump `appMinorVersion` in `app/build.gradle`; signing is local-only (the keystore never enters CI).
- Commit the release APK to `_apk/`; generate notes via the release-notes skill; review the draft, never auto-publish.

## Guardrails

- **Decision-making**: when unsure, ask — never guess. Default conservative: if an action risks code/tests/architecture, postpone and discuss. Self-verify first with read-only experiments. Present 3 numbered options (small effort / best practice / unobvious) with the recommended one, so the user can answer "yes to all".
- **PR discipline**: Conventional Commits titles (`feat`/`fix`/`refactor`/`ci`/`docs`/`test`/`chore`/`perf`), imperative mood, \<50 chars; one idea per PR; keep diffs under ~400 lines.
- **PR description**: Root cause (traced to the actual reason) / Profit (measurable) / Trade-offs (alternatives rejected) / Verification (proof not visible in the diff). Never list changed files or CI status. Add `Closes #N`.
- **Repo hygiene**: use repo-root `.tmp/` (gitignored) for all temporary files; never touch `git config`; never modify `.gitignore` without user acceptance; feature branches only.
- **Suppressions**: every `@Suppress*` / `//noinspection` / `lint.xml` relaxation carries a reasoning comment or a recorded rationale in `MEMORY.md`.
- **Tooling assumptions**: never assume tooling behaves intuitively — verify options against `--help`/docs/schema with a read-only probe. PowerShell escaping differs from bash (backticks, `-1` in `git commit -m`); route complex arguments through a `.tmp/` file rather than inlining.
- **Documenting decisions**: `AGENTS.md` stores rules/constraints only — never rationale. If a line explains *why*, it belongs in `MEMORY.md`. Removing a documented rule changes agent behavior — only delete if provably wrong; relocate rationale, never drop it.

## Commands

```sh
./gradlew assembleDebug                      # CI adds: -PpreDexEnable=false
./gradlew assembleDebugAndroidTest
./gradlew test                               # Robolectric unit tests, no device needed
./gradlew lint                               # lint.xml downgrades MissingTranslation to warning
./gradlew connectedDebugAndroidTest          # needs running emulator/device (AEHD)
./gradlew checkstyle pmd spotbugsDebug jacocoTestReport jacocoTestCoverageVerification spotlessCheck   # quality gates (also run in CI)
```

```sh
uv venv                                      # create repo-local .venv (gitignored)
uv pip install --python .venv pre-commit mdformat
.venv/Scripts/pre-commit run --all-files     # or uvx pre-commit run --all-files
uvx mdformat <file>.md
```

## App protocol (quick reference)

- `SenderService` keeps one TCP connection to the robot (default `192.168.77.1:4444`) and sends newline-terminated plain-text commands: `pad1 x y`, `pad2 x y`, `btn N down`, `wheel <angle>`, `keepalive <ms>`.
- Keepalive: default 5000 ms, minimum 1000 ms. The real timer period is `keepaliveTimeout - 300` ms. Disconnect on `mOut.checkError()` or target change.
- MJPEG video: `com.demo.mjpeg` package, default URI `http://<host>:8080/?action=stream`; the stream reconnects **on error** (`MjpegView.OnStreamErrorListener` → `MainActivity.restartVideoStream()`) — there is no forced periodic restart. Cleartext HTTP is enabled in the manifest.
- Settings keys live in `SettingsFragment` as `SK_*` constants, stored via legacy `PreferenceManager`/`android.preference` APIs.
- Deeper details and rationale: MEMORY.md "App protocol".

## Memory index

Details live in `MEMORY.md` — pull a section on demand:

| Topic | Section in MEMORY.md |
|-------|----------------------|
| Layout, keystore path, versioning | Build & layout |
| Test suite structure, DummyServer ports, emulator prerequisites | Testing |
| SenderService protocol, keepalive, MJPEG | App protocol |
| CI (GitHub Actions), emulator prerequisites | CI quirks |
| Branch/PR and release workflows | Workflows |
| Rationale for tool choices and past fixes | Design decisions |

## Current work

- Execution plan for the revival (canonical layout + quality gates + format
  sweep + toolchain upgrade to compileSdk/targetSdk 36, minSdk 21, coverage to
  85%, pure-Kotlin migration, commits on `feat/global-refresh`) lives in
  `.PLAN.md`. `.PLAN.md` is gitignored — never commit it.
- Phases 1–12 are committed and pushed to the fork (layout, cleanup, CI retire,
  gates, format sweep, deterministic tests, toolchain 36, edge-to-edge + MJPEG
  reconnect, docs refresh, GitHub Actions CI, static analysis, coverage drive).
  CI build gate is green; **CI is NOT green overall at session end** — the last
  fully-green run (both jobs) was `31103997686`. After Phase 12, several runs
  failed on code (lint baseline scope, static-state/test flakes — all fixed),
  one on a GitHub Actions infra issue (`31115958336`), and the final
  validation run `31116833261` failed **7/9 on the swiftshader focus flake —
  the retry-once did not absorb it**. Key state: coverage gate at
  **85% line / 60% branch**; PMD 7.26 + strict-lint baseline + SpotBugs
  (0 bugs); `aosp_atd` local test AVD `Atd_API36` (`-gpu host` only); CI uses
  `default` image + KVM step + `pixel_5`. Next: **fix the instrumented focus
  flake for real** (macOS/GPU runner or a focus-wait before Espresso — the
  retry-once is a band-aid that does not hold), then Kotlin migration
  (Phase 13), retrospective (14).

## Conventions

- Resources are English-only (`resourceConfigurations += ['en']`); `lint.xml` downgrades `MissingTranslation` so missing translations are expected, not an error (rationale in MEMORY.md).
- `buildFeatures.buildConfig = true` — `BuildConfig.VERSION_NAME` is used by the About section.
