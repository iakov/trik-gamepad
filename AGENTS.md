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
video over HTTP. Coverage gate: thresholds live in `app/build.gradle`
(`jacocoTestCoverageVerification`); QA discipline: TESTING.md.
Improvement roadmap: `docs/ROADMAP.md`.

## Layout

- Canonical Android layout at repo root: `settings.gradle` + `app/` module.
  All gradle commands run from the repo root (`./gradlew ...`).
- `_apk/` — committed release APKs (historical).
- `.github/workflows/` — CI: build, Robolectric unit tests, lint/detekt/
  spotbugs/jacoco gates, instrumented tests on emulator, plus a **dormant
  master-only `publish` job** (uploads a debug-signed `releaseDebug` APK — see
  ci.yml; dormant in the single-branch workflow, do not expect it to run).
- `.opencode/skills/` — opencode skills (e.g. release-notes).
- `docs/architecture.md` — module map, TCP protocol, MJPEG pipeline, test layering.
- `docs/img/` — screenshots/logos.

## Build (from repo root)

- Versions/toolchain live in `gradle/libs.versions.toml` (the catalog) +
  `app/build.gradle`/`settings.gradle`: single main flavor, `compileSdk 36` /
  `targetSdk 36` / `minSdk 23` / `maxSdk 36`, the three build types
  (`debug`/`release`/`releaseDebug`), and the `versionCode` formula are all
  there — never restate them here (see "Sources of truth").
  AGP 9's built-in Kotlin removed the `org.jetbrains.kotlin.android` plugin;
  Gradle runs under **JDK 21** (Robolectric 4.16.1 requires it for SDK 36 —
  also the CI JDK). Migration rationale: `DECISIONS.md` "AGP 9.3.1 / Gradle
  9.5.0 migration LANDED".
- `org.gradle.configuration-cache=true` in `gradle.properties` — do not disable
  it again (rationale: DECISIONS.md).
- Three build types; `./gradlew test` runs Robolectric under all three in
  parallel JVMs — unit tests must use ephemeral ports and reset
  SharedPreferences per test (they persist across methods in a JVM; the old
  SenderService static-state trap is gone — see TESTING.md).
- **Verify coverage measures the live class output.** The coverage gate reads
  `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`
  under AGP 9's built-in Kotlin — a stale `tmp/kotlin-classes` path silently
  under-measures and lets new app classes bypass the gate (hit 2026-08-09, see
  DECISIONS.md). After any toolchain migration, add a new app class and confirm
  it appears in the JaCoCo report.
- Signing: `app/build.gradle` applies a release signing config conditionally —
  only when `file('../android-keystorage.jks')` exists; debug builds fall back
  to the auto-generated debug keystore in CI. See MEMORY.md "Build & layout".
- Version: hand-set at the top of `app/build.gradle`
  (`appMajorVersion`/`appMinorVersion`). Bump `appMinorVersion` for a release;
  never set versionCode by hand.
- Dev tooling is cross-platform (Windows/Linux/macOS), managed by uv:
  `pyproject.toml` declares the dev deps; `uv sync` (re)creates `.venv`
  (gitignored) + the committed `uv.lock`. Run tools as `uv run ...`
  (venv-agnostic — resolves `.venv/bin` on POSIX, `.venv/Scripts` on Windows).

### Sources of truth (scripts — reference, never restate)

| Guardrail | Source |
|-----------|--------|
| Coverage gate thresholds | `app/build.gradle` `jacocoTestCoverageVerification` |
| Quality gate steps + tooling | `scripts/gate.py` (+ CI mirror `.github/workflows/ci.yml`) |
| Test-duplication gate | `.jscpd.json` |
| detekt rules/thresholds | `app/config/detekt/detekt.yml` |
| lint rules | `app/lint.xml` (+ baseline) |
| pre-commit hooks | `.pre-commit-config.yaml` |
| Dependency/tool versions | `gradle/libs.versions.toml` |

## Hooks

Action triggers for AI agents. When adding/removing tools or changing
configurations, update this section and the referenced config files.

### On session init

- Read this file, `MEMORY.md` header + section list, `DECISIONS.md` index,
  `DESIGN.md` section index, `TESTING.md`, `app/build.gradle`, and
  `.github/workflows/ci.yml`; pull MEMORY/DECISIONS/DESIGN sections on demand.
- Don't talk to the user before session warm-up is complete.

### Before commit

- When pre-commit is installed, run `uv run pre-commit run --all-files`; otherwise at least `uvx mdformat` on changed `.md` files.
- Commit with `git commit --no-gpg-sign`: `commit.gpgsign=true` is set locally but gpg has no interactive agent here, so a plain `git commit` hangs until timeout. Never change git config (Repo hygiene); pass the flag per commit instead. Rationale: MEMORY.md.
- New tool/config → update this section and `MEMORY.md`.
- Editing `AGENTS.md`: review `git diff HEAD -- AGENTS.md`, merge old content
  into the new rather than deleting outright; confirm each deletion is
  intentional.

### Before push — publishing gate (commit-sprint)

- Fork-only workflow: work lives in the personal fork (origin remote; run `git remote -v` for the URL). Never create PRs against upstream `trikset/trik-gamepad`.
- Branch from fork `master` (== `origin/master`); name `feat/`, `fix/`, `docs/`, `style/`, `refactor/`, or `chore/`.
- Never push to `master` directly — always a within-fork PR (`gh pr create --base master`), squash-merged after green CI (see Guardrails).
- Squash-fix mistakes before push: `git reset --soft HEAD~1 && git commit`.
- **Commit cheaply during development** — pre-commit formats only, no heavy gates per commit. All validation batches here: run the canonical gate once (`uv run python scripts/gate.py`) and re-iterate fix→gate until green before pushing. Test changes additionally: full 3-variant `test` suite twice. `git status --short` must be clean (local gates validate the working tree, not the commits).
- After pushing new commits, update the PR body (stale bodies mislead):
  `gh pr edit <N> --body-file .tmp/pr-body.md`; verify bodies with
  `gh pr view --json body` for mojibake. *Dormant during the current
  single-branch no-PR execution plan — applies once the within-fork
  PR workflow resumes.*
- **Docs pushes (`.md`-only changes): never wait for green CI** — if a docs push spoils CI, fix it before the next campaign (campaigns start only from green CI).
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
  resolved under the current toolchain (config-cache reuses; the Gradle-10
  deprecations were fixed) — re-run the scan before trusting old examples.

### After merge

- Check fork `master` CI after the squash-merge (`gh run list --branch master --limit 3`) — if it fails, fix immediately, don't move on.
- Update local `master`: `git switch master && git pull`; delete the merged
  branch.
- *Dormant during the current single-branch no-PR execution plan —
  applies once the within-fork PR workflow resumes.*

### Before test / command

- From repo root: `./gradlew test` (Robolectric, no device needed); single-test syntax: Commands.
- Instrumented tests need a running emulator/device (hypervisor: Windows AEHD / Linux KVM / macOS Hypervisor.framework — verify with `emulator -accel-check`); boot with `-gpu host` (never `swiftshader_indirect`) and pre-empt the immersive-mode confirmation (`adb shell settings put secure immersive_mode_confirmations confirmed`). Full recipe + per-platform table: TESTING.md.
- **Instrumented tap→command tests use direct `performClick()`** (a `ViewAction` calling `view.performClick()`), not Espresso touch `click()`, when the app does async work (connect/video reload) around the taps. A tap injected during such a main-thread transition is **silently dropped** — signature: the outer buttons of a row fire while the middle/second taps never send (verified a test-injection artifact, not an app bug). Touch-precision assertions belong only in dedicated pad tests. Diagnostics + rationale: TESTING.md "tap→command wiring".
- **Cold-boot degraded emulators**: after a long session, broad instrumented failures (logcat `Sending oneway calls to frozen process`, package-service down, focus flakes) mean the emulators are degraded — `adb reboot` is **not** enough; kill + relaunch with the exact launch flags and settle ~30-60 s before re-running. Details: TESTING.md "long-session emulator degradation".
- `connectedDebugAndroidTest` fails under the configuration cache with an AGP/UTP serialization error (`field __testRunnerFactory__ ... DefaultConfigurableFileCollection`) — run it with `--no-configuration-cache`.

### Operational rules (command hygiene)

- **Command hygiene**: every command runs with a reasonable timeout and is logged (tee to `app/build/<task>.log` or `.tmp/`); on timeout read the log first. If a command takes ≥1.5× the expected time, analyze the wrong guess and record expected vs actual.
- **Always measure elapsed time for every campaign**: report the wall-clock elapsed figure to the console when the campaign completes, and store ONLY the campaign's `Estimated` and `Actual` (elapsed) in the ROADMAP header table — no `Start`/`End` timestamps. A campaign whose ROADMAP entry has no elapsed figure is **incomplete** — treat missing timing like missing verification.
- **A wrong guess usually means an option was not set properly** — re-audit the invocation.
- **Slow commands → research (incl. web), tune repeatable tooling, document in MEMORY.md** — never fix the symptom.
- **Single-branch CI cache trap**: `cache-read-only: ${{ github.ref != 'refs/heads/master' }}` never writes the cache when nothing pushes to `master` — set `cache-read-only: false` and verify a follow-up run is faster (measured here: build gate 4m27s → 1m05s, ~4×). Details: MEMORY.md "CI quirks".
- **Measure the second CI run after a build change**: the first run after a `settings.gradle`/`build.gradle` change is polluted by config-cache invalidation — compare the second run on the same head, not the first.
- **Async turns**: capture a process handle (`Start-Process -PassThru`), verify liveness immediately, then a single bounded readiness poll in the same working loop — a turn is not complete until the readiness result is recorded; never end a turn on a bare liveness check.
- **"Exit 0" ≠ the tool ran** — re-run with `--info`/`--rerun-tasks` and confirm the analyzer actually analyzed sources before trusting green. Concrete trigger: a static-analysis task that shows `UP-TO-DATE` right after you added/renamed source files (analyzers can stay UP-TO-DATE when new files arrive via an untracked path) — force one `./gradlew detekt --rerun-tasks` pass before trusting the gate (MEMORY.md "Campaign 4").
- **Apply documented class traps before writing tests** (MEMORY.md/TESTING.md per-class entries).
- **Format before you gate — automate, don't remember.** `.kt` → `./gradlew spotlessApply` (ktfmt), `.md` → `uvx mdformat`; run them before the gate or `spotlessCheck` fails. Run `spotlessApply` as a **separate invocation** from the gate when `org.gradle.parallel=true` (it rewrites `.kt` while `test` compiles them — a race). Pre-commit hooks + `scripts/gate.py` automate this (see Commands).
- **Generate lint baselines with the aggregate `lint` task**, not `lintDebug`; env-dependent checks (e.g. `OldTargetApi`) go in `lint.xml`, not the baseline.
- **CI/emulator traps** (per-line `script:` blocks, infra-vs-code triage, step-timestamp races, focus-overlay): MEMORY.md "CI quirks".
- **Gaps escalate** (1st: document · 2nd: automate · 3rd+: tool config); **verify "runs automatically" claims with a command**; **measure, don't estimate**.
- **3 identical failures → stop and read the shadow/API source**, don't tweak-and-rerun. This applies to **any repeated tooling signal, not just test assertions**: the same message appearing N≥3 times across commands (e.g. `spotlessKotlinCheck FAILED`, "configuration cache cannot be reused") means a systemic cause — find and fix it, don't absorb it. After reading the source, **also instrument the failing test** (log server-received messages, view bounds, timing; read the per-test logcat under `app/build/outputs/androidTest-results/`) — the root cause may be in the test harness/injection, not the app.
- **Never read `window`/activity-scoped state in a field initializer** — `Activity.window` is only assigned during `attach()` (after the constructor), so a field initializer referencing it throws in Robolectric ("Window creation failed!") and NPEs on device. Use `by lazy` or a provider lambda; declare such fields with a default that defers the access.
- **CI cadence**: one bounded run check (~3 min) after each push; if no run appears, document it and re-check at the next push rather than blocking.
- **UI changes ship with a screenshot proof**: capture a `.tmp` screenshot on an emulator whose screencap works (`Atd_API36` host-GPU screencap returns pure black — use `Swiftshader_API36`; MEMORY "Emulator prerequisites") as feature proof and report its path after push. If told to stop before push, stop with the screenshot already saved — never push past a stop request.

### On tool error

- Stop immediately; identify the root cause before proceeding. Fix second, skip third.
- **Error triage**: after any unexpected error, ask "Was this expected? Would I have been surprised if it succeeded?" If unexpected, stop and investigate — root cause first, fix second, skip third.
- **Root-cause taxonomy**: trace past the surface error to one of — missing hook (add one), missing in docs (write it down), forgot to search (add a "check docs" step), ignored error signal (tool produced `fatal:` but execution continued — add an "on tool error" hook).
- **Check if this has happened before**: grep `AGENTS.md` and `MEMORY.md` for the error type before crafting a fix.
- Transient infra failures (network, CI outage) ≠ code errors: verify state, retry with backoff, then report.
- Every error leaves a trace: capture the lesson in `AGENTS.md` (rules) or `MEMORY.md` (details) before moving on.

### Before release

- Gates: green CI, 0 open PRs, 0 security alerts.
- Run an **upstream comparison + user-facing downside audit** (diff the app vs upstream from the user's point of view; report regressions/downgrades). Use it for PR/release descriptions written from the user's perspective.
- Bump `appMinorVersion` in `app/build.gradle`; signing is local-only (the keystore never enters CI).
- Commit the release APK to `_apk/`; generate notes via the release-notes skill; review the draft, never auto-publish.

## Guardrails

- **Decision-making**: when unsure, ask — never guess. Default conservative: if an action risks code/tests/architecture, postpone and discuss. Self-verify first with read-only experiments. Present 3 numbered options (small effort / best practice / unobvious) with the recommended one, so the user can answer "yes to all".
- **"run auto" / "go full auto mode"**: execute fully and accurately without questions. Auto mode additionally means all of: (1) work inside the stated container (e.g. single-branch no-PR) without ceremony — commit/push per the gate rules as you go; (2) apply documented traps and hooks from `AGENTS.md`/`MEMORY.md`/`TESTING.md` before acting, and probe tooling read-only before relying on it — don't stall on a command call; (3) if a problem arises, research the best solution (web/code) before deciding — if still unsure after experiments, think hard and postpone for the future rather than guess; (4) implement only proved, reasonable decisions. Postpone anything genuinely biased or uncertain.
- **PR discipline**: Conventional Commits titles (`feat`/`fix`/`refactor`/`ci`/`docs`/`test`/`chore`/`perf`), imperative mood, \<50 chars; one idea per PR; keep diffs under ~400 lines.
- **PR description**: Root cause (traced to the actual reason) / Profit (measurable) / Trade-offs (alternatives rejected) / Verification (proof not visible in the diff). Never list changed files or CI status. Add `Closes #N`.
- **Repo hygiene**: use repo-root `.tmp/` (gitignored) for all temporary files; never touch `git config`; never modify `.gitignore` without user acceptance; feature branches only.
- **Suppressions**: every `@Suppress*` / `//noinspection` / `lint.xml` relaxation carries a reasoning comment or a recorded rationale in `MEMORY.md`.
- **Tooling assumptions**: never assume tooling behaves intuitively — verify options against `--help`/docs/schema with a read-only probe; route complex arguments through a `.tmp/` file rather than inlining. (Shell-escaping differences, e.g. PowerShell vs bash: "Windows/PowerShell quirks".)
- **Re-read `.md` diffs after mdformat**: line-start `+`/`-`/`*` mid-paragraph get reflowed into lists — never start a wrapped line with a list character.
- **Documenting decisions**: `AGENTS.md` stores rules/constraints only — never rationale. A *decision* (problem → alternatives → why → out-of-scope) belongs in `DECISIONS.md`; a fact/quirk/retrospective belongs in `MEMORY.md`. Removing a documented rule changes agent behavior — only delete if provably wrong; relocate rationale, never drop it. Would removing this change agent behavior? → keep it. Is the claim provably wrong (verified against executable sources — config, workflow, code)? → only then delete/correct. Does it enforce a docs/structure contract? → keep structural-convention rules even when the wording looks generic.
- **AGENTS.md grows only via argued decisions**: a new line must be justified by a decision documented in `DECISIONS.md` (why it is, or is expected to be, useful). Retrospectives may remove statements that were never proven, to keep AGENTS.md lean.
- **Merge, don't delete**: when replacing a section, merge old content into the new rather than deleting outright; confirm each deletion is intentional. On docs-drift review, read `AGENTS.md` top-to-bottom and push detail/rationale down to `MEMORY.md`/`DECISIONS.md` — `AGENTS.md` = pointers, the rest = on-demand detail; keep context small and focused.
- **Session context is ephemeral**: persist decisions to `AGENTS.md`/`DECISIONS.md`/`MEMORY.md` BEFORE creating any PR or wrapping up — never rely on chat history to preserve decisions.
- **Docs/code sync**: config/dependency/public-interface/workflow changes update `README.md`, `AGENTS.md`, and/or `MEMORY.md`/`DECISIONS.md`; if `.github/workflows/` changed, grep docs for stale claims. `README.md` is end-user-facing only.
- **Docs store experience, not state**: keep only what saves future time and cannot be rediscovered faster than a doc can drift — counts, versions, thresholds, and hashes must be OUT of docs (glob/wrapper/`libs.versions.toml`/build scripts reveal them in seconds). Dated records stay as history; never restate live state. Docs help, not burden: a line that would not save a future step is cut.
- **Code comments are first-level documentation**: re-validate comments when the surrounding code changes — a comment that describes a no-longer-true constraint or gives misleading advice is garbage (hit 2026-08-10: the NSC whitelist comment and the `main`-child-order comment both went stale within the same campaign). No stale or misleading comments; when in doubt, delete the comment rather than leave a wrong one.
- **Machine-local workarounds never enter repo docs**: host-specific repo mirrors, init scripts, URLs and other local-host hacks live on the host in their corresponding places (e.g. the Gradle user-home), or in a gitignored `.tooling.md` if no other place exists — repo docs must not reference them.
- **Verify toolchain/dependency-manager names against executable sources** (build files, lockfiles) before writing them into any doc.
- **Tests are code**: re-use similar test support (shared `TestTcpServer`, `RobolectricTestBase`,
  pref/measure helpers, data-driven tables) instead of copy-pasting. **Dedup drives the token number, not
  table-ization**; a table row's expected value must not depend on an earlier row's state (reset the fixture per row).
  Rationale + details: `DECISIONS.md` "[2026-08-09] Test logical SLOC metric" + TESTING.md "Test quality discipline".
- **Exact-text UI matchers break silently on rewording**: before changing any user-visible string
  (titles, values, contentDescriptions), grep `androidTest` for `withText`/`withContentDescription`
  exact matchers and update them in the **same commit** (hit twice in C15: the ellipsis-prefixed
  preference titles and the glyph-suffixed magic-button descriptions each broke instrumented tests).
- **Budget the coverage pass with the code**: a commit that adds app classes should carry the tests
  that keep `jacocoTestCoverageVerification` green — waiting for the gate to fail costs repeated
  full-gate iterations (hit 3× in C15; new app code always dips the ratio).
- **Mark dormant hooks**: a rule/hook that does not apply to the current phase must say so explicitly (e.g. PR-workflow hooks are dormant during the single-branch no-PR execution plan) — otherwise it silently misdirects agents into workflow artifacts that don't exist yet.

## Commands

```sh
./gradlew assembleDebug                      # CI adds: -PpreDexEnable=false
./gradlew assembleDebugAndroidTest
./gradlew test                               # Robolectric unit tests, all 3 build types (no device)
./gradlew testDebugUnitTest --tests "com.trikset.gamepad.SenderServiceTest.<method>"   # single test
./gradlew connectedDebugAndroidTest          # needs emulator/device; add --no-configuration-cache (prereqs + flags: TESTING.md)
  uv run python scripts/gate.py                # THE canonical quality gate — steps + tooling live in
                                               # scripts/gate.py (CI mirror: ci.yml; jscpd config: .jscpd.json).
                                               # Never re-run its steps by hand.
  uv run python scripts/check_translations.py --sync              # translation key/format parity (also runs inside gate.py/CI)
  uv run python scripts/check_translations.py --back-translate    # one-off semantic review via MyMemory (not a gate)
```

Dev tooling (uv): `uv sync` (re)creates `.venv` from `pyproject.toml` +
`uv.lock`; run tools via `uv run ...` (venv-agnostic). Pre-commit:
`uv run pre-commit run --all-files` (hooks: `.pre-commit-config.yaml`);
`.md` formatting: `uvx mdformat <file>.md`.

## App protocol (quick reference)

TCP protocol, keepalive, MJPEG reconnect, settings keys: MEMORY.md "App protocol".

## Windows/PowerShell quirks

Consolidated Windows/PowerShell-specific traps (Campaign 7). Each entry may be
about the *subject* (Gradle daemons, UTF/BOM, EOL) or about the *environment*
(pwsh, win). Re-audit each on the first POSIX dev box: subject-related entries
stay universal, environment-related ones stay here — they are not universal
rules.

- **`local.properties` drive-colon escaping (Windows)**: `sdk.dir=C\:/...`
  (escape the drive-letter colon) or lint's `PropertyEscape` check fails the
  build; POSIX paths need no escaping. The file is gitignored.
- **`.md` formatting + EOL trap (Windows checkout)**: pre-format with `uvx mdformat` before pre-commit (the first hook run reformats and fails); after committing `.md`, expect a dirty working tree — the hook rewrites to LF (vs CRLF) with a **content-identical** diff (`git diff --stat` empty, `git status` dirty). Verify content-identical, then `git checkout -- <md>`; never `git add` the EOL-only state. POSIX checkouts are LF already, so the symptom does not appear there.
- **PS `Set-Content`/`Out-File` rewrite the ENTIRE file with a BOM + normalized EOLs**: a one-line regex "fix" run through `Set-Content -Encoding utf8` across 5 files turned a 5-line diff into 385 lines of EOL churn plus a UTF-8 BOM on every file (hit 2026-08-11, C15). For targeted edits use the edit tool (byte-preserving); for whole-file transforms use a byte-preserving Python script routed through `.tmp/` (the edit tool and Python preserve untouched bytes; the shell cmdlets do not). Verify `git diff --stat` stayed minimal after any PowerShell file rewrite.
- **PS 5.1 `$ErrorActionPreference='Stop'` + native stderr is a terminating error**: `& npx ... *>> $log` under EAP=Stop silently kills the script the moment the tool prints to stderr (jscpd prints "Using config from ..."). Scope `$ErrorActionPreference='Continue'` around native calls and check `$LASTEXITCODE` — for ANY new native command in a PowerShell script with EAP=Stop. (The Python gate `scripts/gate.py` is immune — `subprocess` captures stderr.)
- **`gh` run commands take the run **id**, not a PowerShell run object**, and need `--repo iakov/trik-gamepad` (the default resolves to upstream and 404s).
- **`gh run view --json ... --jq "<expr>"` with embedded quotes breaks under PowerShell** ("accepts at most 1 arg(s), received N") — the quoted `--jq` expression gets mangled in argument passing. Use plain `--json status,conclusion` or route the expression through a `.tmp/` file.
- **PowerShell escaping differs from bash** (backticks, `-1` in `git commit -m`); route complex arguments through a `.tmp/` file rather than inlining.
- **PS 5.1 `>` on a native command's stdout corrupts binary output**: `adb exec-out screencap -p > shot.png` writes the raw bytes as text (UTF-16/translated), breaking the PNG ("FromFile ... Out of memory"); `git show HEAD:file > x` is the same trap (the dump becomes UTF-16). Capture on-device (`adb shell screencap -p /sdcard/x.png`) then `adb pull`, read git blobs via the git tools, or redirect via `cmd /c "... > file"`.
- **Cold Gradle daemon spawn hangs the caller until the daemon detaches (Windows handle inheritance)**: the build prints and finishes fast, but a freshly-spawned daemon inherits the tool's output handles, so the completion signal (pipe EOF) waits until the daemon releases them after startup (`jps -l` shows the orphan `GradleDaemon`). Never pipe long-lived children (gradle/emulator) through Tee/Select — the daemon inherits the pipe handles and the pipeline never sees EOF; redirect to a file (`*> log`) instead, and the file redirect alone still does **not** fix it — use `--no-daemon`/`--stop` for tool-driven Gradle probes. POSIX daemons detach cleanly (re-audit on the first POSIX box).

## Memory index

Details and decisions live in `MEMORY.md` / `DECISIONS.md` — pull a section on
demand:

| Topic | Section |
|-------|---------|
| **UX/design decisions & conventions** | **DESIGN.md** (named sections: Defaults-as-useful, Every-setting-shows-its-value, Ellipsis-on-dialog-rows, Option-descriptions, Color-is-not-enough, Touch-targets, Magic-button-symbols, About-vs-Copy-report, System-theme, Localization, WCAG, Connection-and-video-state) |
| Layout, keystore path, versioning | MEMORY.md "Build & layout" |
| Test suite structure, TCP test servers, emulator prerequisites | MEMORY.md "Testing" + TESTING.md |
| SenderService protocol, keepalive, MJPEG | MEMORY.md "App protocol" |
| CI (GitHub Actions), emulator prerequisites | MEMORY.md "CI quirks" |
| Branch/PR and release workflows | MEMORY.md "Workflows" |
| Retrospectives, execution records, reference quirks | MEMORY.md "Design decisions & retrospectives" |
| **Decision log** (problem → alternatives → why → out-of-scope) | **DECISIONS.md** (index at top) |
| Architecture notes + quirks + Android/Kotlin/CI best-practice reference | `docs/architecture.md` |

## Current work

- Execution plan + crash-safety session state: `.PLAN.md` (gitignored — never
  commit; holds ONLY unfinished tasks; read it on session init and after every
  decision/step; completed work is trimmed from it). Why/what it is for:
  `DECISIONS.md` "Why .PLAN.md exists". Improvement roadmap: `docs/ROADMAP.md`.

## Conventions

- Resources ship `en` + `ru` + `fr` + `de` + `vi` (`resourceConfigurations`,
  `values-*/strings.xml`, `res/xml/locales_config.xml`). Every locale keeps full
  key + format-specifier parity — enforced deterministically by
  `scripts/check_translations.py --sync` inside `scripts/gate.py`, so
  translations can never drift into a commit. Typical buttons/labels reuse
  `@android:string/*` where an exact match exists (the OS localizes those).
  Details: `DESIGN.md "Localization"`.
- `buildFeatures.buildConfig = true` — `BuildConfig.VERSION_NAME` is used by the About section.
- **Every option carries a description** and value-bearing settings show their
  current value; rows that open an input dialog end with "…"; defaults are the
  most useful choice and are pre-filled (never a blank). See
  `DESIGN.md "Option descriptions"`, `"Every setting shows its current value"`,
  `"Ellipsis on dialog rows"`, `"Defaults are as useful as possible"`.
- SIMPLE ENGLISH for all globally-visible content (release notes, PR
  descriptions, commits, docs, comments); reply to GitHub issues/comments in
  the language the author used.
