# AGENTS.md — trik-gamepad

<!-- encoding: utf-8 -->

Scope: Action triggers, guardrails, and commands for AI agents.
Memory: Details, architecture, and quirks explanations live in `MEMORY.md`;
decisions (problem → alternatives → why → out-of-scope) live in
`DECISIONS.md`. Pull sections on demand — never duplicate rationale here.
Testing strategy lives in `TESTING.md`.

Every line must answer: "Would an agent likely miss this without help?" If not, cut it.
Removing a documented rule changes agent behavior — only delete if provably incorrect.

## Project

Android app (pure Kotlin, 0 `.java` files) that mimics a gamepad to control
TRIK robots. It sends plain-text commands over a TCP socket and streams MJPEG
video over HTTP. Coverage gate: 95% line / 80% branch (measured 97.3% / 81.9%).
Improvement roadmap: `docs/ROADMAP.md`.

## Layout

- Canonical Android layout at repo root: `settings.gradle` + `app/` module.
  All gradle commands run from the repo root (`./gradlew ...`).
- `_apk/` — committed release APKs (historical).
- `.github/workflows/` — CI: build, Robolectric unit tests, lint/detekt/
  spotbugs/jacoco gates, instrumented tests on emulator, plus a **dormant
  `publish` job** (master-only: on green runs it uploads a debug-signed
  `releaseDebug` APK artifact for early adopters — see ci.yml; dormant until a
  master merge lands, do not expect it to run in the single-branch workflow).
- `.opencode/skills/` — opencode skills (e.g. release-notes).
- `docs/architecture.md` — module map, TCP protocol, MJPEG pipeline, test layering.
- `docs/img/` — screenshots/logos.
- `.venv/` — repo-local uv virtualenv (pre-commit, mdformat); gitignored.

## Build (from repo root)

- Toolchain (locked in `.PLAN.md`): **AGP 9.3.1, Gradle 9.5.0, built-in Kotlin**
  (AGP 9 removed the `org.jetbrains.kotlin.android` plugin — Kotlin compilation
  is built in; `kotlinOptions {}` is gone, `jvmTarget` defaults to
  `compileOptions.targetCompatibility`), Java 11 source/target — Gradle runs
  under **JDK 21** (Robolectric 4.16.1 requires it for SDK 36 tests). `compileSdk 36`,
  `targetSdk 36`, `minSdk 23`, `maxSdk 36` — single main flavor, no product
  flavors. AGP 9's new DSL is on (no `android.newDsl=false` opt-out).
- `org.gradle.configuration-cache=true` — **re-enabled under AGP 9**: the AGP
  `https.proxyHost` sys-prop read that defeated it on AGP 8 was fixed, so the
  cache is now actually reused. Do not disable it again.
- Three build types (`debug`/`release`/`releaseDebug`); `./gradlew test` runs
  Robolectric under all three in parallel JVMs — unit tests must use ephemeral
  ports and reset SharedPreferences per test (they persist across methods in a
  JVM; the old SenderService static-state trap was removed in ROADMAP Phase 3 —
  see TESTING.md).
- **Verify coverage measures the live class output.** The 95 line / 80 branch
  gate reads `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`
  under AGP 9's built-in Kotlin — a stale `tmp/kotlin-classes` path silently
  under-measures and lets new app classes bypass the gate (hit 2026-08-09, see
  DECISIONS.md). After any toolchain migration, add a new app class and confirm
  it appears in the JaCoCo report.
- `settings.gradle` at repo root: `rootProject.name = 'trik-gamepad'`, `include ':app'`.
- `local.properties` (gitignored): `sdk.dir` must escape the drive-colon (`C\:/...`) or lint's `PropertyEscape` check fails the build.
- Signing: `app/build.gradle` applies a release signing config conditionally — only when `file('../android-keystorage.jks')` exists. Debug builds fall back to the auto-generated debug keystore in CI. See MEMORY.md "Build & layout" for gitignore status and how the path resolves.
- Version is hand-set at the top of `app/build.gradle` (`appMajorVersion`/`appMinorVersion`, currently 1.41). `versionCode` is computed (`minSdk*10000 + major*100 + minor`), `versionNameSuffix` is `-API<minSdk>`. Bump `appMinorVersion` for a release; never set versionCode by hand.

## Hooks

Action triggers for AI agents. When adding/removing tools or changing
configurations, update this section and the referenced config files.

### On session init

- Read this file, `MEMORY.md` header + section list, `DECISIONS.md` index, `TESTING.md`, `app/build.gradle`, and `.github/workflows/ci.yml`; pull MEMORY/DECISIONS sections on demand.
- Don't talk to the user before session warm-up is complete.

### Before commit

- When pre-commit is installed, run `.venv/Scripts/pre-commit run --all-files`; otherwise at least `uvx mdformat` on changed `.md` files.
- Commit with `git commit --no-gpg-sign`: `commit.gpgsign=true` is set locally but gpg has no interactive agent here, so a plain `git commit` hangs until timeout. Never change git config (Repo hygiene); pass the flag per commit instead. Rationale: MEMORY.md.
- New tool/config → update this section and `MEMORY.md`.
- Editing `AGENTS.md`: review `git diff HEAD -- AGENTS.md`, merge old content
  into the new rather than deleting outright; confirm each deletion is
  intentional.

### Before push / PR

- Fork-only workflow: work lives in the personal fork (origin remote; run `git remote -v` for the URL). Never create PRs against upstream `trikset/trik-gamepad`.
- Branch from fork `master` (== `origin/master`); name `feat/`, `fix/`, `docs/`, `style/`, `refactor/`, or `chore/`.
- Never push to `master` directly — always a within-fork PR (`gh pr create --base master`), squash-merged after green CI (see Guardrails).
- Squash-fix mistakes before push: `git reset --soft HEAD~1 && git commit`.
- After pushing new commits, update the PR body (stale bodies mislead):
  `gh pr edit <N> --body-file .tmp/pr-body.md`; verify bodies with
  `gh pr view --json body` for mojibake. *Dormant during the current
  single-branch no-PR execution plan (`.PLAN.md`) — applies once the within-fork
  PR workflow resumes.*
- Re-validate from repo root: `./gradlew test` and `./gradlew lint`.
- If `AGENTS.md` changed: `git diff HEAD -- AGENTS.md`, check every added/removed line against the boundary test (see Guardrails — Documenting decisions).

### After push (retrospective)

- Analyze decisions; suggest comments for unclear code and docs for non-obvious
  patterns.
- Capture every rule deviation/missing rule NOW — end with `AGENTS.md`/
  `MEMORY.md` updated or an explicit decision not to.
- **Frequency-scan the session logs, not just failures**: `grep -c` the session
  command logs for repeated diagnostics (e.g. "Deprecated Gradle features",
  "spotlessKotlinCheck FAILED", "configuration cache cannot be reused"), sort by
  count, and root-cause the top ones. Recurring messages in *every* run mean a
  systemic cause, not noise — this is how the config-cache invalidation and the
  Gradle deprecation warnings were missed (both in ~every log). Both are now
  resolved under the AGP 9.3.1 / Gradle 9.5.0 toolchain (config-cache reuses;
  the Gradle-10 deprecations were fixed) — re-run the scan before trusting old
  examples.

### After merge

- Check fork `master` CI after the squash-merge (`gh run list --branch master --limit 3`) — if it fails, fix immediately, don't move on.
- Update local `master`: `git switch master && git pull`; delete the merged
  branch.
- *Dormant during the current single-branch no-PR execution plan (`.PLAN.md`) —
  applies once the within-fork PR workflow resumes.*

### Before test / command

- From repo root: `./gradlew test` (Robolectric, no device needed); a single test via `./gradlew testDebugUnitTest --tests "com.trikset.gamepad.SenderServiceTest.<method>"`.
- Instrumented tests need a running emulator/device (AEHD hypervisor required — verify with `emulator -accel-check`); boot with `-gpu host` (never `swiftshader_indirect`) and pre-empt the immersive-mode confirmation (`adb shell settings put secure immersive_mode_confirmations confirmed`). Full recipe: TESTING.md.

### Operational rules (command hygiene)

- **Command hygiene**: every command runs with a reasonable timeout and is logged (tee to `app/build/<task>.log` or `.tmp/`); on timeout read the log first. If a command takes ≥1.5× the expected time, analyze the wrong guess and record expected vs actual.
- **A wrong guess usually means an option was not set properly** — re-audit the invocation.
- **Slow commands → research (incl. web), tune repeatable tooling, document in MEMORY.md** — never fix the symptom.
- **Single-branch CI cache trap**: `gradle/actions/setup-gradle` `cache-read-only: ${{ github.ref != 'refs/heads/master' }}` means the cache is **never written** when the workflow never pushes to `master` (single-branch no-PR) — every CI run is cold. Set `cache-read-only: false` and verify a follow-up run is faster. Measured here: build gate 4m27s → 1m05s (~4×).
- **Measure the second CI run after a build change**: the first run after a `settings.gradle`/`build.gradle` change is polluted by config-cache invalidation — compare the second run on the same head, not the first.
- **Async turns**: capture a process handle (`Start-Process -PassThru`), verify liveness immediately, then a single bounded readiness poll in the same working loop — a turn is not complete until the readiness result is recorded; never end a turn on a bare liveness check.
- **Never pipe long-lived children (gradle/emulator) through Tee/Select** — the daemon inherits the pipe handles and the pipeline never sees EOF; redirect to a file (`*> log`) and use `--no-daemon`/`--stop` for probes.
- **"Exit 0" ≠ the tool ran** — re-run with `--info`/`--rerun-tasks` and confirm the analyzer loaded its config and analyzed sources before trusting green. Concrete trigger: a static-analysis task that shows `UP-TO-DATE` right after you added/renamed source files (e.g. detekt can stay UP-TO-DATE when new files arrive via an untracked path) — force one `./gradlew detekt --rerun-tasks` pass before trusting the gate.
- **Apply documented class traps before writing tests** (MEMORY.md/TESTING.md per-class entries); **run the full 3-variant `test` suite twice** before pushing test changes.
- **Format before you gate — automate, don't remember.** Every touched file type has a formatter: `.kt` → `./gradlew spotlessApply` (ktfmt), `.md` → `uvx mdformat`. Run them **before** the gate (`spotlessCheck` will otherwise fail the first gate run and cost a wasted ~2-min rerun — hit 4× in one session). Run `spotlessApply` as a **separate invocation** from the gate when `org.gradle.parallel=true` — in one invocation it rewrites `.kt` while `test` compiles the same files (a race). Both are now automated: the pre-commit `spotless-apply` local hook runs `gradlew.bat spotlessApply` on `.kt` changes (Windows-probed; `cmd /c` is required for `language: system`), the mdformat pre-commit hook covers `.md`, and `scripts/gate.ps1` is the canonical gate (two invocations — see Commands).
- **Generate lint baselines with the aggregate `lint` task**, not `lintDebug`; env-dependent checks (e.g. `OldTargetApi`) go in `lint.xml`, not the baseline.
- **CI `script:` blocks run per-line.** `reactivecircus/android-emulator-runner`
  splits `script:` into individual lines and runs each as its own `sh -c`
  (comments dropped). Multi-line `if/fi` blocks, `\` continuations, and
  `while` loops never work — any conditional must be a single line
  (`cmd || { ...; }`). Validate every line with `sh -n` before pushing.
- **Distinguish infra from code failures** (adb boot flake, GHA action-download) — triage by job/step and boot-vs-tests; keep a note of the last known-good CI run id.
- **When a CI "fix" doesn't hold, read the step timestamps, not just the failure**: if a setup/prerequisite step ran before its dependency was ready (e.g. `settings put` before the settings provider was up), the race is the bug — make the step wait for and verify its prerequisite.
- **A focus failure after a targetSdk bump is usually an OS overlay, not app code** — check `dumpsys window` `mCurrentFocus` for system windows before editing the app.
- **Gaps escalate** (1st: document · 2nd: automate · 3rd+: tool config); **verify "runs automatically" claims with a command**; **measure, don't estimate**.
- **3 identical failures → stop and read the shadow/API source**, don't tweak-and-rerun. This applies to **any repeated tooling signal, not just test assertions**: the same message appearing N≥3 times across commands (e.g. `spotlessKotlinCheck FAILED`, "configuration cache cannot be reused") means a systemic cause — find and fix it, don't absorb it.
- **Never read `window`/activity-scoped state in a field initializer** — `Activity.window` is only assigned during `attach()` (after the constructor), so a field initializer referencing it throws in Robolectric ("Window creation failed!") and NPEs on device. Use `by lazy` or a provider lambda; declare such fields with a default that defers the access.
- **Pre-format `.md` with `uvx mdformat`** before pre-commit (the first hook run reformats and fails).
- **`gh` run commands take the run **id**, not a PowerShell object**, and need `--repo iakov/trik-gamepad` (the default resolves to upstream and 404s).
- **Push gates**: the full local gate list is run and logged, and **`git status --short` must be clean** before every push (local gates validate the working tree, not the commits).
- **CI cadence**: one bounded run check (~3 min) after each push; if no run appears, document it and re-check at the next push rather than blocking.

### On tool error

- Stop immediately; identify the root cause before proceeding. Fix second, skip third.
- **Error triage**: after any unexpected error, ask "Was this expected? Would I have been surprised if it succeeded?" If unexpected, stop and investigate — root cause first, fix second, skip third.
- **Root-cause taxonomy**: trace past the surface error to one of — missing hook (add one), missing in docs (write it down), forgot to search (add a "check docs" step), ignored error signal (tool produced `fatal:` but execution continued — add an "on tool error" hook).
- **Check if this has happened before**: grep `AGENTS.md` and `MEMORY.md` for the error type before crafting a fix.
- Transient infra failures (network, CI outage) ≠ code errors: verify state, retry with backoff, then report.
- Every error leaves a trace: capture the lesson in `AGENTS.md` (rules) or `MEMORY.md` (details) before moving on.

### Before release

- Gates: green CI, 0 open PRs, 0 security alerts.
- Bump `appMinorVersion` in `app/build.gradle`; signing is local-only (the keystore never enters CI).
- Commit the release APK to `_apk/`; generate notes via the release-notes skill; review the draft, never auto-publish.

## Guardrails

- **Decision-making**: when unsure, ask — never guess. Default conservative: if an action risks code/tests/architecture, postpone and discuss. Self-verify first with read-only experiments. Present 3 numbered options (small effort / best practice / unobvious) with the recommended one, so the user can answer "yes to all".
- **"run auto" / "go full auto mode"**: execute fully and accurately without questions. Auto mode additionally means all of: (1) work inside the stated container (e.g. single-branch no-PR) without ceremony — commit/push per the gate rules as you go; (2) apply documented traps and hooks from `AGENTS.md`/`MEMORY.md`/`TESTING.md` before acting, and probe tooling read-only before relying on it — don't stall on a command call; (3) if a problem arises, research the best solution (web/code) before deciding — if still unsure after experiments, think hard and postpone for the future rather than guess; (4) implement only proved, reasonable decisions. Postpone anything genuinely biased or uncertain.
- **PR discipline**: Conventional Commits titles (`feat`/`fix`/`refactor`/`ci`/`docs`/`test`/`chore`/`perf`), imperative mood, \<50 chars; one idea per PR; keep diffs under ~400 lines.
- **PR description**: Root cause (traced to the actual reason) / Profit (measurable) / Trade-offs (alternatives rejected) / Verification (proof not visible in the diff). Never list changed files or CI status. Add `Closes #N`.
- **Repo hygiene**: use repo-root `.tmp/` (gitignored) for all temporary files; never touch `git config`; never modify `.gitignore` without user acceptance; feature branches only.
- **Suppressions**: every `@Suppress*` / `//noinspection` / `lint.xml` relaxation carries a reasoning comment or a recorded rationale in `MEMORY.md`.
- **Tooling assumptions**: never assume tooling behaves intuitively — verify options against `--help`/docs/schema with a read-only probe. PowerShell escaping differs from bash (backticks, `-1` in `git commit -m`); route complex arguments through a `.tmp/` file rather than inlining.
- **Re-read `.md` diffs after mdformat**: line-start `+`/`-`/`*` mid-paragraph get reflowed into lists — never start a wrapped line with a list character.
- **Documenting decisions**: `AGENTS.md` stores rules/constraints only — never rationale. A *decision* (problem → alternatives → why → out-of-scope) belongs in `DECISIONS.md`; a fact/quirk/retrospective belongs in `MEMORY.md`. Removing a documented rule changes agent behavior — only delete if provably wrong; relocate rationale, never drop it.
- **Safe-updates mirror** (when removing content): would removing this change agent behavior? → keep it. Is the claim provably wrong? → only then delete/correct, verified against executable sources (config, workflow, code). Does it enforce a docs/structure contract? → keep structural-convention rules even when the wording looks generic.
- **Merge, don't delete**: when replacing a section, merge old content into the new rather than deleting outright; confirm each deletion is intentional.
- **Generalize, then extract**: on docs-drift review, read `AGENTS.md` top-to-bottom for generalization and push detail/rationale down to `MEMORY.md`.
- **Progressive disclosure**: `AGENTS.md` = pointers, `MEMORY.md`/`DECISIONS.md` = on-demand detail; keep context small and focused.
- **Session context is ephemeral**: persist decisions to `AGENTS.md`/`DECISIONS.md`/`MEMORY.md` BEFORE creating any PR or wrapping up — never rely on chat history to preserve decisions.
- **Docs/code sync**: config/dependency/public-interface/workflow changes update `README.md`, `AGENTS.md`, and/or `MEMORY.md`/`DECISIONS.md`; if `.github/workflows/` changed, grep docs for stale claims. `README.md` is end-user-facing only.
- **Verify toolchain/dependency-manager names against executable sources** (build files, lockfiles) before writing them into any doc.
- **Mark dormant hooks**: a rule/hook that does not apply to the current phase must say so explicitly (e.g. PR-workflow hooks are dormant during the single-branch no-PR `.PLAN.md` execution plan) — otherwise it silently misdirects agents into workflow artifacts that don't exist yet.

## Commands

```sh
./gradlew assembleDebug                      # CI adds: -PpreDexEnable=false
./gradlew assembleDebugAndroidTest
./gradlew test                               # Robolectric unit tests, no device needed
./gradlew lint                               # lint.xml downgrades MissingTranslation to warning
./gradlew connectedDebugAndroidTest          # needs running emulator/device (AEHD)
./scripts/gate.ps1                           # canonical local gate: spotlessApply THEN the
                                             # full quality suite, each --no-daemon, tee'd to .tmp/gate.log
./gradlew detekt spotbugsDebug jacocoTestReport jacocoTestCoverageVerification spotlessCheck   # quality gates (also run in CI; checkstyle/pmd retired after the pure-Kotlin migration)
```

```sh
uv venv                                      # create repo-local .venv (gitignored)
uv pip install --python .venv pre-commit mdformat
.venv/Scripts/pre-commit run --all-files     # or uvx pre-commit run --all-files
uvx mdformat <file>.md
```

## App protocol (quick reference)

TCP protocol, keepalive, MJPEG reconnect, settings keys: MEMORY.md "App protocol".

## Memory index

Details and decisions live in `MEMORY.md` / `DECISIONS.md` — pull a section on
demand:

| Topic | Section |
|-------|---------|
| Layout, keystore path, versioning | MEMORY.md "Build & layout" |
| Test suite structure, DummyServer ports, emulator prerequisites | MEMORY.md "Testing" + TESTING.md |
| SenderService protocol, keepalive, MJPEG | MEMORY.md "App protocol" |
| CI (GitHub Actions), emulator prerequisites | MEMORY.md "CI quirks" |
| Branch/PR and release workflows | MEMORY.md "Workflows" |
| Retrospectives, execution records, reference quirks | MEMORY.md "Design decisions & retrospectives" |
| **Decision log** (problem → alternatives → why → out-of-scope) | **DECISIONS.md** (index at top) |
| Architecture notes + quirks + Android/Kotlin/CI best-practice reference | `docs/architecture.md` |

## Current work

- Execution plan + crash-safety session state: `.PLAN.md` (gitignored — never commit). Improvement roadmap: `docs/ROADMAP.md`.

## Conventions

- Resources are English-only (`resourceConfigurations += ['en']`); `lint.xml` downgrades `MissingTranslation` so missing translations are expected, not an error (rationale in DECISIONS.md).
- `buildFeatures.buildConfig = true` — `BuildConfig.VERSION_NAME` is used by the About section.
- SIMPLE ENGLISH for all globally-visible content (release notes, PR
  descriptions, commits, docs, comments); reply to GitHub issues/comments in
  the language the author used.
