# AGENTS.md — trik-gamepad

<!-- encoding: utf-8 -->

Scope: Action triggers, guardrails, and commands for AI agents.
Memory: Details, architecture, design decisions, and quirks explanations live
in `MEMORY.md` (pull sections on demand) — never duplicate rationale here.
Testing strategy lives in `TESTING.md`.

Every line must answer: "Would an agent likely miss this without help?" If not, cut it.
Removing a documented rule changes agent behavior — only delete if provably incorrect.

## Project

Android app (mixed Kotlin/Java, effectively Java-only — 0 `.kt` files) that
mimics a gamepad to control TRIK robots. It sends plain-text commands over a
TCP socket and streams MJPEG video over HTTP. Only the app in `as/` is
maintained.

## Layout

- `as/` — the only active project (Android Studio / Gradle). All gradle commands run from `as/`.
- `xamarin/` — legacy, unfinished F# Xamarin port. Not built by CI, do not edit.
- `_apk/` — committed release APKs (historical).
- `.circleci/config.yml` — CI: builds `as/`, unit tests on JVM, instrumented tests via Firebase Test Lab.

## Build (from `as/`)

- Gradle wrapper 8.11.1, AGP 8.9.0, Kotlin 1.9.23, Java 11. `compileSdk 35`, `targetSdk 34`, `minSdk 21`.
- No `settings.gradle`; `build.gradle` at the `as/` root is the whole build.
- Signing: `defaultConfig` applies a release signing config referencing `../../android-keystorage.jks`. Debug builds use it too, so **any build fails without that keystore file present**. See MEMORY.md "Build & layout" for gitignore status and how the path resolves.
- Version is hand-set at the top of `as/build.gradle` (`appMajorVersion`/`appMinorVersion`, currently 1.40). `versionCode` is computed (`minSdk*10000 + major*100 + minor`), `versionNameSuffix` is `-API<minSdk>`. Bump `appMinorVersion` for a release; never set versionCode by hand.

## Hooks

Action triggers for AI agents. When adding/removing tools or changing
configurations, update this section and the referenced config files.

### On session init

- Read this file, `MEMORY.md` header + section list, `TESTING.md`, `as/build.gradle`, and `.circleci/config.yml`; pull MEMORY sections on demand.
- Don't talk to the user before session warm-up is complete.

### Before commit

- When pre-commit is installed, run `pre-commit run --all-files`; otherwise at least `uvx mdformat` on changed `.md` files.
- New tool/config → update this section and `MEMORY.md`.

### Before push / PR

- Fork-only workflow: work lives in the personal fork (origin remote; run `git remote -v` for the URL). Never create PRs against upstream `trikset/trik-gamepad`.
- Branch from fork `master` (== `origin/master`); name `feat/`, `fix/`, `docs/`, `style/`, `refactor/`, or `chore/`.
- Never push to `master` directly — always a within-fork PR (`gh pr create --base master`), squash-merged after green CI (see Guardrails).
- Re-validate from `as/`: `./gradlew test` and `./gradlew lint`.
- If `AGENTS.md` changed: `git diff HEAD -- AGENTS.md`, check every added/removed line against the boundary test (see Guardrails — Documenting decisions).

### Before test / command

- From `as/`: `./gradlew test` (Robolectric, no device needed); a single test via `./gradlew testDebugUnitTest --tests "com.trikset.gamepad.SenderServiceTest.<method>"`.
- Instrumented tests need a running emulator/device (AEHD hypervisor required — verify with `emulator -accel-check`).

### On tool error

- Stop immediately; identify the root cause before proceeding. Fix second, skip third.
- Transient infra failures (network, CI outage) ≠ code errors: verify state, retry with backoff, then report.
- Every error leaves a trace: capture the lesson in `AGENTS.md` (rules) or `MEMORY.md` (details) before moving on.

### Before release

- Gates: green CI, 0 open PRs, 0 security alerts.
- Bump `appMinorVersion` in `as/build.gradle`; signing is local-only (the keystore never enters CI).
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
cd as
./gradlew assembleDebug                      # CI adds: -PpreDexEnable=false
./gradlew assembleDebugAndroidTest
./gradlew test                               # Robolectric unit tests, no device needed
./gradlew lint                               # lint.xml downgrades MissingTranslation to warning
./gradlew connectedDebugAndroidTest          # needs running emulator/device (AEHD)
```

```sh
uv tool install pre-commit && pre-commit install   # once; needs %USERPROFILE%\.local\bin on PATH
pre-commit run --all-files
uvx mdformat <file>.md
```

## App protocol (quick reference)

- `SenderService` keeps one TCP connection to the robot (default `192.168.77.1:4444`) and sends newline-terminated plain-text commands: `pad1 x y`, `pad2 x y`, `btn N down`, `wheel <angle>`, `keepalive <ms>`.
- Keepalive: default 5000 ms, minimum 1000 ms. The real timer period is `keepaliveTimeout - 300` ms. Disconnect on `mOut.checkError()` or target change.
- MJPEG video: `com.demo.mjpeg` package, default URI `http://<host>:8080/?action=stream`; the stream is force-restarted every 30 s. Cleartext HTTP is enabled in the manifest.
- Settings keys live in `SettingsFragment` as `SK_*` constants, stored via legacy `PreferenceManager`/`android.preference` APIs.
- Deeper details and rationale: MEMORY.md "App protocol".

## Memory index

Details live in `MEMORY.md` — pull a section on demand:

| Topic | Section in MEMORY.md |
|-------|----------------------|
| Layout, keystore path, versioning | Build & layout |
| Test suite structure, DummyServer ports | Testing |
| SenderService protocol, keepalive, MJPEG | App protocol |
| CI (CircleCI, Firebase), emulator prerequisites | CI quirks |
| Branch/PR and release workflows | Workflows |
| Rationale for tool choices and past fixes | Design decisions |

## Current work

- In-flight plan for the "global refresh" (docs culture + quality gates + format
  sweep, four commits on `feat/global-refresh`) lives in `.PLAN.md`. Code/tool
  changes are intentionally postponed — do not add quality gates without an
  explicit request.

## Conventions

- Resources are English-only (`resourceConfigurations += ['en']`); `lint.xml` downgrades `MissingTranslation` so missing translations are expected, not an error (rationale in MEMORY.md).
- `buildFeatures.buildConfig = true` — `BuildConfig.VERSION_NAME` is used by the About section.
