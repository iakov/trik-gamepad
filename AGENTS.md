# AGENTS.md — trik-gamepad

<!-- encoding: utf-8 -->

Scope: Action triggers, guardrails, and commands for AI agents.
Details: `MEMORY.md`; decisions: `DECISIONS.md`; testing: `TESTING.md`;
design: `DESIGN.md`. Pull sections on demand — never duplicate rationale.

## Project

Android app (pure Kotlin, 0 `.java` files) — gamepad for TRIK robots.
TCP command protocol, MJPEG video. Coverage gate: `app/build.gradle`
(`jacocoTestCoverageVerification`). QA discipline: `TESTING.md`.

## Layout

- Canonical Android layout: `settings.gradle` + `app/` module.
- `_apk/` — historical release APKs (committed).
- `.github/workflows/` — CI (build, gates, instrumented tests, dormant publish job).
- `.opencode/skills/` — opencode skills (release-notes).
- `docs/architecture.md` — module map, protocol, pipelines, test layering.

## Build

Versions/toolchain in `gradle/libs.versions.toml` + `app/build.gradle`/`settings.gradle`.
Three build types (`debug`/`release`/`releaseDebug`). Single `versionCode`
formula. **JDK 21** required. `org.gradle.configuration-cache=true` in
`gradle.properties`. `--add-modules=jdk.compiler` is load-bearing. Kotlin
warnings are errors. Dev tooling: `uv sync` + `uv run ...`.
Scripts: `scripts/README.md`.

### Sources of truth

| Guardrail | Source |
|-----------|--------|
| Coverage gate thresholds | `app/build.gradle` `jacocoTestCoverageVerification` |
| Quality gate steps | `scripts/gate.py` (+ CI mirror `.github/workflows/ci.yml`) |
| Test-duplication gate | `.jscpd.json` |
| detekt rules/thresholds | `app/config/detekt/detekt.yml` |
| lint rules | `app/lint.xml` (+ baseline) |
| pre-commit hooks | `.pre-commit-config.yaml` |
| Dependency/tool versions | `gradle/libs.versions.toml` |

## Hooks

### On session init

- Read this file, `MEMORY.md` header + section index, `DECISIONS.md` index,
  `DESIGN.md` index, `TESTING.md`, `app/build.gradle`, `.github/workflows/ci.yml`;
  pull sections on demand.
- Run `uv run python scripts/refresh_kotlin_ls.py` (--refresh if it warns).

### Before commit

- Run `uv run pre-commit run --all-files` (or `uvx mdformat` on `.md`).
- `git commit --no-gpg-sign` (gpgsign=true locally, no interactive GPG).
- New tool/config → update this section + `MEMORY.md`.
- Editing `AGENTS.md`: review `git diff HEAD -- AGENTS.md`, merge old content
  into new rather than deleting outright; confirm each deletion intentional.

### Before push — publishing gate

- Fork-only workflow: work lives in personal fork (origin). Never PR upstream.
- Branch: `feat/`/`fix/`/`docs/`/`style/`/`refactor/`/`chore/`. No direct master push.
- Squash-fix mistakes before push (`git reset --soft HEAD~1 && git commit`).
- **Commit cheaply** (pre-commit only). Batch validation once: `uv run python scripts/gate.py` + full 3-variant test twice. `git status --short` must be clean.
- **Scan the push for device identifiers** (`git diff origin/<branch>..HEAD`).
- Docs-only pushes (`.md`): never wait for green CI.
- If AGENTS.md changed: verify every line against the boundary test (below).

### After push (retrospective)

- Run `MEMORY.md` "Campaign retrospective checklist" (also reviews the checklist).
- Analyze decisions; suggest comments for unclear code and docs for non-obvious patterns.
- Generalize repeated instances of a lesson into one rule/finding.
- Capture every rule deviation/missing rule NOW.
- Frequency-scan session logs for repeated diagnostics; root-cause top ones.

### After merge

- Check fork master CI after squash-merge.
- Update local master, delete merged branch.
- *Dormant during single-branch no-PR workflow.*

### Before test / command

- `./gradlew test` (Robolectric, no device). Single-test syntax in Commands.
- Instrumented: boot emulator (AEHD/KVM/Hypervisor.framework, `-gpu host`),
  pre-empt immersive mode (`adb shell settings put secure immersive_mode_confirmations confirmed`). Full recipe: TESTING.md.
- Tap→command tests: use `performClick()`, not Espresso `click()`, around
  async transitions. Rationale: TESTING.md "tap→command wiring".
- Cold-boot degraded emulators: kill + relaunch, not `adb reboot`. Details: TESTING.md.
- `connectedDebugAndroidTest` fails under config cache — use `--no-configuration-cache`.

### Operational rules (command hygiene)

- **Tee first, tail for status only** — every command that can fail MUST `tee`
  to a log file before any pipe truncation. On failure, read the saved log,
  do not re-run.
- **Bound long-lived commands at the process TREE** (`uv run python scripts/run_bounded.py --timeout N -- cmd ...`). `_gradle.call_gradle` and gate.py's steps are already
  bounded. Ad-hoc calls: route through run_bounded or bash-tool timeout.
- `run_bounded` is hang-proof as of 2026-08-27 (PIPE reader thread). The
  remaining caveats: nested `$var` quoting in PowerShell loops, and verifying
  liveness independently when wrapping a detached launcher. Details: MEMORY.md
  "Timeout-bound tooling", DECISIONS.md "[2026-08-27] run_bounded hang-proof redesign".
- **Always measure elapsed time** per campaign; record in ROADMAP header.
- **A wrong guess = option not set properly** — re-audit the invocation.
- **Slow commands → research, tune, document** — never fix the symptom.
- **Single-branch CI cache trap**: set `cache-read-only: false`. Details: MEMORY.md "CI quirks".
- **Measure the second CI run after a build change** (first is config-cache polluted).
- **Async turns**: `Start-Process -PassThru` + liveness check + bounded readiness
  poll in one turn. Never end on a bare liveness check.
- **Stuck tool call WITH output = invocation pattern problem first** — re-audit
  against MEMORY "Timeout-bound tooling" before probing state.
- **"Exit 0" ≠ the tool ran** — re-run with `--rerun-tasks` to force analysis.
- **Apply documented class traps** before writing tests (MEMORY/TESTING per-class entries).
- **Format before gate**: `.kt` → `./gradlew spotlessApply` (separate from gate
  when parallel=true); `.md` → `uvx mdformat`.
- **Generate lint baselines with aggregate `lint` task**, not `lintDebug`.
- **CI/emulator traps**: MEMORY.md "CI quirks" (per-line script blocks,
  infra-vs-code triage, focus-overlay).
- **Gaps escalate** (1st: document · 2nd: automate · 3rd+: tool config).
- **3 identical failures → read the source**, don't tweak-and-rerun.
- **Never read `window`/activity state in a field initializer** — `by lazy` or
  provider lambda.
- **Custom `onMeasure` that skips children → invisible views** — call
  `super.onMeasure(...)` or `measureChildren`.
- **CI cadence**: one bounded check (~3 min) after each push.
- **UI changes ship with screenshot proof** on Swiftshader_API36 (screencap
  works). Name `_ui_YYMMDD-HHMM-NN.png`. Verify by pixel-census; hash-match
  stored proofs. Never push past a stop request.

### On tool error

- Stop, identify root cause. Fix second, skip third.
- "Was this expected?" → if unexpected, investigate.
- Check if this happened before (grep AGENTS.md + MEMORY.md).
- Transient infra failures ≠ code errors. Retry with backoff, then report.
- Capture lesson in AGENTS.md (rules) or MEMORY.md (details).

### Before release

- Gates: green CI, 0 open PRs, 0 security alerts.
- Upstream comparison + user-facing downside audit.
- Bump `appMinorVersion`. `git tag -s v<major>.<minor>`. If GPG signing hangs
  ≥10 min, abort (locally only; never push unsigned tags).
- `./gradlew assembleRelease` → rename APK → release-notes skill → `gh release create --draft --notes-file release-notes.md <files>`.
- Push tag first (signed) before `gh release create`. User reviews + publishes.
- After publish: refresh README hero screenshot if HUD changed.

## Guardrails

- **Design sits above decisions** — DESIGN.md scenarios (S1–S14) are the product
  truth. A decision that violates a scenario is a design regression.
- **Decision-making**: when unsure, ask (3 options, recommend one). Default
  conservative.
- **"run auto"**: execute fully without questions; apply all documented traps
  before acting; research best solution; implement only proved decisions.
- **PR discipline**: Conventional Commits, imperative mood, \<50 chars, one idea,
  diff \<400 lines. Description: root cause / profit / trade-offs / verification.
- **Repo hygiene**: `.tmp/` for temp files; never touch `git config`; feature
  branches only.
- **Never push device identifiers** — serial, model, IMEI. Session-only via
  ANDROID_SERIAL. Sweep `git diff`/`git add` before committing. Push-prep scan
  is the final gate. Already violated twice (2026-08-15, 2026-08-20).
- **Suppressions**: every `@Suppress`/`//noinspection`/lint.xml relaxation
  carries a rationale comment or MEMORY.md entry.
- **Tooling assumptions**: verify with `--help`/docs/`--read-only` probe; route
  complex args through `.tmp/` file.
- **Re-read .md diffs after mdformat** — line-start `+`/`-`/`*` create lists;
  keep inline-code spans on a single line.
- **Documenting decisions**: AGENTS.md = rules/triggers only (never rationale).
  Rationale → DECISIONS.md; facts/quirks → MEMORY.md. Would removing this
  change agent behavior? → keep it. Provably wrong? → delete. Structural
  convention? → keep.
- **AGENTS.md grows via argued decisions** (DECISIONS.md justification).
  Retrospectives may prune unproven statements.
- **Merge, don't delete** when replacing AGENTS.md sections. On docs-drift
  review, push detail down to MEMORY/DECISIONS.
- **Store knowledge in the best-scoped doc**: AGENTS (rules) / DECISIONS
  (rationale) / MEMORY (facts/quirks) / ROADMAP (campaign record) / `.PLAN.md`
  (unfinished tasks).
- **Docs-drift audit is per-doc scope**: each doc must contain only what its
  scope requires.
- **`.PLAN.md` holds only unfinished tasks** — completed work leaves it.
- **Session context is ephemeral** — persist decisions before creating PRs.
- **Docs/code sync**: config/dependency/public-interface/workflow changes update
  README, AGENTS, MEMORY, DECISIONS. README is end-user-facing only.
- **Docs store experience, not state** — counts/versions/thresholds/hashes
  stay in executable sources (build files, lockfiles), not docs.
- **Code comments are first-level documentation** — re-validate when surrounding
  code changes. No stale/misleading comments; delete rather than leave wrong.
- **Machine-local workarounds never enter repo docs** — host in `.tooling.md`.
- **Verify toolchain names against executable sources** before writing docs.
- **Tests are code**: reuse shared test support (TestTcpServer, RobolectricTestBase,
  helpers). Dedup drives token count, not table-ization.
- **Tests must be able to fail**: no tautologies, no unreachable assertions.
  Write new behavior tests red-first (TDD); mutation-check.
- **Exact-text UI matchers break on rewording** — grep androidTest before
  changing strings; update matchers in same commit.
- **Budget coverage with the code** — a commit adding app classes carries tests
  that keep the gate green.
- **Mark dormant hooks** with explicit "dormant" note.
- **XML-first for UI, Code only for runtime tint** — static chrome in
  drawables/styles/colors (`hud_*` + `Hud.*`). Full restyle leaves unused
  resources — grep before gate.
- **AAPT dotted style names imply parent** — add `parent=""` or explicit parent.
  `Bundle.getInt(key)` returns **0, not null** on missing key.
- **Verify UI changes by pixel-census**, not eyeballing the diff.
- **`bringToFront()` overlay eats taps on later siblings** — re-bring-to-front
  needed siblings. Tap by uiautomator bounds center.
- **State-driven dimming must compose with user alpha** — use `min()`, not bare multiply.
- **One alpha authority per view** — do not combine `.alpha` set with `fillAfter`
  `AlphaAnimation`.
- **`connectedDebugAndroidTest` uninstalls the app** → capture proofs before
  suite, or `adb install -r` after.
- **Robolectric temp-dir race** = flake (parallel 3-variant JVMs) → single-class rerun.
- **Tags over ids for repeated sibling views** — `android:tag` + `findViewWithTag`.
- **Compound drawable beats container+icon pill** for lint `UseCompoundDrawables`.
- **Identical string values under two keys** → `DuplicateStrings` lint.
- **Robolectric pixel assertions must be STRUCTURAL** (visibility/text/bounds/alpha),
  never glyph pixels. Compute at mdpi (Robolectric default).
- **jscpd threshold 0.0%** — extract shared test helpers immediately.
- **Before isolated commits, verify tests compile against HEAD's design**.
- **Nested quoting through cmd/PowerShell mangles `$`, quotes, `^`** → route
  through `.tmp/` file. `uv run pip list` showing a package ≠ usable — use
  `uv run --with <pkg>`.

## Commands

```sh
./gradlew assembleDebug                      # CI adds: -PpreDexEnable=false
./gradlew assembleDebugAndroidTest
./gradlew test                               # Robolectric, all 3 build types
./gradlew testDebugUnitTest --tests "com.trikset.gamepad2.SenderServiceTest.<method>"
./gradlew connectedDebugAndroidTest          # emulator/device; --no-configuration-cache
uv run python scripts/gate.py                # canonical quality gate
uv run python scripts/pr_gate.py             # pre-upstream-PR APK gate (after assembleReleaseDebug)
uv run python scripts/check_translations.py --sync
```

Dev tooling: `uv sync`; `uv run ...`; `uv run pre-commit run --all-files`.

## Memory index

| Topic | Section |
|-------|---------|
| UX/design decisions & conventions | DESIGN.md (named sections) |
| Layout, keystore, versioning | MEMORY.md "Build & layout" |
| Test suite, TCP servers, emulator prep | MEMORY.md "Testing" + TESTING.md |
| Gamepad protocol | DESIGN.md "Gamepad protocol" |
| SenderService, keepalive, MJPEG | MEMORY.md "App protocol" |
| CI quirks | MEMORY.md "CI quirks" |
| Workflows | MEMORY.md "Workflows" |
| Retrospectives, reference quirks | MEMORY.md "Design decisions & retrospectives" |
| Decision log | DECISIONS.md (index) |
| Architecture notes | docs/architecture.md |

## Current work

`.PLAN.md` (gitignored, unfinished tasks). Roadmap: `docs/ROADMAP.md`.

## Conventions

- Resources: `en` + `ru` + `fr` + `de` + `vi` (key/format parity enforced by
  `check_translations.py --sync`). Reuse `@android:string/*` where exact match.
- `buildFeatures.buildConfig = true` — VERSION_NAME used by About.
- Every option has a description and shows current value. Dialog rows end with "…".
  Defaults are pre-filled (never blank). Details: DESIGN.md named sections.
- SIMPLE ENGLISH for all globally-visible content (releases, PRs, commits, docs, comments).
