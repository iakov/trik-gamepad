# F-Droid submission reference

Post-mortem of the v2.45→v2.46 submission cycle to `fdroid/fdroiddata` MR !49992.
Every mistake below was made and fixed; root causes are documented to prevent recurrence.

## Error catalog (root cause → fix)

### Category A: F-Droid metadata (fdroiddata YAML)

| # | Error | Root cause | Fix verified in CI |
|---|-------|------------|-------------------|
| A1 | Missing `UpdateCheckData` — `fdroid checkupdates` fails with "Couldn't find any version information" | Dynamic `versionCode` formula in `build.gradle` (`minSdk*10000 + major*100 + minor`) is not a static literal; fdroidserver can't evaluate it | Add `UpdateCheckData: version.properties\|VERSION_CODE=(\d+)\|.\|` — fdroidserver reads VERSION_CODE from the static field in version.properties |
| A2 | `^` anchor in `UpdateCheckData` regex → regex never matches | `fdroidserver` compiles the regex with Python `re` defaults **without `re.MULTILINE`**. `^` matches only the absolute start of the file (before comments), not line starts | Remove `^` from regex |
| A3 | `WebSite:` same URL as `SourceCode:` | Redundant field; review explicitly asks to remove | Omit `WebSite:` entirely when `SourceCode:` points to the same URL |
| A4 | `Description:` in fdroiddata YAML | F-Droid auto-pulls descriptions from the upstream repo's `fastlane/metadata/android/<locale>/full_description.txt` at the tagged commit. Putting it in the YAML duplicates the content | Remove `Description:` from fdroiddata; maintain full fastlane metadata in upstream repo |
| A5 | `AllowedAPKSigningKeys:` with colons and uppercase hex | Used `keytool -list -v` output directly; but fdroidserver expects **lowercase hex, no colons** | Format: `keytool -list -v ... \| grep "SHA256:" \| tr '[:upper:]' '[:lower:]' \| tr -d ':'` |
| A6 | `Binaries:` missing | Required for reproducible builds; reviewer will block submission | Always add `Binaries:` URL with `%v` (versionName) and `%c` (versionCode) placeholders |
| A7 | `commit:` uses tag (`v2.45`) instead of full SHA hash | Copy-pasted tag name from the fastlane YML; but fdroiddata requires the immutable commit SHA | `git rev-parse v2.45` → paste the 40-char SHA |
| A8 | Field ordering wrong | `fdroid rewritemeta` was not run before submission | Always run `fdroid rewritemeta <appid>` before committing to the fork |
| A9 | `Binaries:` URL hardcodes `-API21-` in asset filename | Asset naming used `appVersionName` which includes the minSdk suffix. If minSdk changes, the URL breaks silently | Use `%c` (versionCode) in the asset filename — versionCode formula encodes minSdk naturally: `minSdk * 10000 + ...`. When minSdk bumps, versionCode jumps. |
| A10 | `Description:` in fdroiddata duplicates fastlane | Assumption: fdroiddata needs its own description | F-Droid auto-extracts from upstream fastlane; never duplicate |

### Category B: Git/release management

| # | Error | Root cause | Fix |
|---|-------|------------|-----|
| B1 | GPG signing fails with "Inappropriate ioctl for device" | `gpgconf --kill` flushed the agent passphrase cache; subsequent signing needs a TTY-based pinentry | Never kill the gpg-agent mid-session. Sign via `tmux` with `GPG_TTY=\$(tty)` where pinentry-curses can prompt |
| B2 | `git push --force-with-lease` failure | Branch state changed between fetch and push (e.g., `git rebase` in another terminal) | Use `--force-with-lease` (not `--force`); fetch before push; avoid concurrent operations on the same branch |
| B3 | Accidental staging of `.omo/`, `release-v*.md` | `git add -A` stages everything including dev artifacts | Always use explicit path lists: `git add <specific files>`. `git status --short` before commit to audit staged files |
| B4 | Pushed to fdroiddata fork without user authorization | Task scope creep: "prepare metadata" drifted into "push to GitLab fork". No hard boundary between prep and delivery | ⚠️ **NEVER push to the fdroiddata fork.** Preparation ends at `cat metadata/file.yml` showing the ready file. The user pushes themselves. Scripts must refuse to push to any remote matching `*fdroid*` |
| B4 | Tag deleted unnecessarily | Over-corrected for a minor asset naming issue | Verify documented convention matches actual need before destructive actions |
| B5 | Stale rebase state in tmux session | `git rebase --abort` from a stale pane moved the branch to an older commit | Before any `send-keys` to tmux, verify `git status` and `git branch --show-current` match expectations |

### Category C: Version/bump management

| # | Error | Root cause | Fix |
|---|-------|------------|-----|
| C1 | `version_manager.py check` shows stale built APK mismatch | `check` compares against `app/build/outputs/apk/release/output-metadata.json` which is only updated when `assembleRelease` runs. The APK from the last release is always one version behind | This is a warning, not an error. Build fresh APK before release: `./gradlew assembleRelease` |
| C2 | Missing changelog for new version (`210245.txt`) | `version_manager.py bump` updates version fields but doesn't create a changelog | Manual step; `version_manager.py check` now flags missing changelogs. Could auto-create a template in future |
| C3 | Missing changelog for v2.45 (for the tag) | v2.45 was tagged before changelog was created; can't add files to an existing tag | Create changelog BEFORE tagging |

### Category D: Build/compatibility

| # | Error | Root cause | Fix |
|---|-------|------------|-----|
| D1 | `compileSdk 33` build fails — `androidx.core:core-viewtree` requires SDK 34+ | Jetpack deps have moved past API 33 | Minimum viable compileSdk is 34. F-Droid pre-installs up to 33 but auto-installs via sdkmanager. No benefit to lowering below 36 |
| D2 | Release asset name depends on hardcoded `API${21}` in `appVersionName` | `def appVersionName = "${major}.${minor}-API${21}"` in build.gradle — the 21 is hardcoded, not read from version.properties | Dropped `-API${21}` from `appVersionName`; asset naming now uses `appVersionCode` via `%c` in Binaries URL |
| D3 | **F-Droid scanner rejects signed APK** — extra signing block `Dependency metadata` (0x504B4453) | AGP 7.1+ inserts an encrypted dependency list (Maven coordinates) encrypted with Google Play's key. F-Droid cannot verify it and flags it as a problem | Add `dependenciesInfo { includeInApk = false }` in `app/build.gradle`. No functional impact — block is only for Google Play Console SDK insight warnings |

## Pre-release gates (automated in `version_manager.py check`)

Run `uv run python scripts/version_manager.py check` before every release.
It now validates all categories above:

1. **Version drift** — version.properties, fastlane YML, fdroiddata YML, and built APK all agree
2. **`AllowedAPKSigningKeys` present** in fdroiddata YAML (A5)
3. **`Binaries` present** in fdroiddata YAML (A6)
4. **`commit:` is a full SHA** (40 hex chars), not a tag or branch name (A7)
5. **`UpdateCheckData` regex** has no `^` anchor (A2)
6. **No `Description:` / `Summary:`** in fdroiddata YAML (A4)
7. **Changelog exists** at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (C2)
8. **`dependenciesInfo.includeInApk = false`** in `app/build.gradle` (D3) — absence causes F-Droid scanner to flag the signed APK

## Suggested script improvements

### 1. `version_manager.py bump` should create a changelog template

After bumping, if `changelogs/<new_versionCode>.txt` doesn't exist, create it with a placeholder. Saves manual step, prevents C2.

### 2. Pre-tag validation in CI

```yaml
  validate-fdroiddata:
    name: Validate F-Droid metadata
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: uv run python scripts/version_manager.py check
```

### 3. `check_reproducibility.py` should validate against the tagged commit

Currently runs against the working tree. For honest verification, run against the exact commit that `git tag` will point to, via a git worktree:
```python
subprocess.run(["git", "worktree", "add", tmpdir, commit_hash], ...)
```

### 4. `git add -A` guard

Add a pre-commit hook that rejects commits containing `.omo/`, `release-v*.md`, `*.log` files (or other dev-artifact patterns).

## Release flow (hardened)

```
 1. version_manager.py bump <minor>     # bumps version.properties + syncs YMLs
 2. version_manager.py check             # validates ALL 7 gates
 3. git add <files>                       # explicit paths, never -A
 4. git commit -S                         # signed commit
 5. ./gradlew test                        # or uv run gate.py
 6. check_reproducibility.py              # F-Droid reproducibility gate
 7. Write tag text to .tmp/release-v<tag>.txt, then:
    git tag -s -F .tmp/release-v<tag>.txt v<maj>.<min> upstream/master
 8. git push upstream v<maj>.<min>        # CI builds + publishes
 9. --- DONE: release is live ---
10. # Generate fdroiddata submission from template:
11. VERSION_NAME=2.46 VERSION_CODE=210246 \
      COMMIT_SHA=$(git rev-parse v2.46^{commit}) \
      envsubst '${VERSION_NAME}${VERSION_CODE}${COMMIT_SHA}' \
        < fdroiddata/com.trikset.gamepad2.yml.template \
        > .tmp/com.trikset.gamepad2.yml
12. # Validate generated file:
13. fdroid rewritemeta < .tmp/com.trikset.gamepad2.yml  # or copy to fdroiddata clone and run there
14. # Submit: push .tmp/com.trikset.gamepad2.yml to GitLab fork (agent NEVER touches fdroiddata remotes)
```

## Release tag text template

```
TRIK Gamepad <version> — <one-line title>

What's new

<user-facing paragraph>

Key improvements

- <bullet point>
- <bullet point>

---

For developers

### Version

| Version | versionCode | minSdk | targetSdk |
|---------|-------------|--------|-----------|
| <v.m> | <code> | Android 5.0 (API 21) | 36 |

### Major changes

- <conventional-commit summaries>

Contributors: [@iakov](https://github.com/iakov)

Detailed comparison: <https://github.com/trikset/trik-gamepad/compare/v<prev>...v<current>>
```

## Common CI failures (by error code)

| Code | Symptom | Root cause | Fix |
|------|---------|------------|-----|
| A1 | `checkupdate` → "Couldn't find any version information" | Missing `UpdateCheckData` or `^` anchor | Add `UpdateCheckData`; remove `^` |
| A2 | `checkupdate` → "file not found" | Tag doesn't contain the referenced file | Check `version.properties` exists at the tag commit |
| A3 | `rewritemeta` fails | Field ordering wrong | Run `fdroid rewritemeta <appid>` |
| A4 | `WebSite` same as `SourceCode` | Redundant field | Remove `WebSite:` |
| A5 | `Description:` present in fdroiddata | Duplicate | Remove; goes in upstream fastlane |
| A7 | `commit:` uses tag instead of SHA | Copy-paste error | `git rev-parse <tag>` → paste full 40-char hash |
| B1 | GPG "Inappropriate ioctl" | Agent cache flushed | `export GPG_TTY=\$(tty)` + sign via tmux |
| C2 | Missing changelog | Bump didn't create it | Create `changelogs/<versionCode>.txt` before tag |
| FD | `checkupdate` → "current version is newer" | CI scans older tags (v2.44, v2.45) whose versionCode is < CurrentVersionCode. For initial submissions this is a false positive | Acceptable for initial MR; maintainer can override. Or restrict `UpdateCheckMode: Tags ^v2\.4[6-9]$` to skip older tags |

## Guard script suggestion

Add a `--forbid-remote` flag to `version_manager.py` (or a small wrapper) that checks `git remote -v` and exits 1 if any remote URL matches `gitlab.com/*fdroid*`:

```python
def forbid_fdroid_remote() -> None:
    import subprocess, re
    out = subprocess.run(["git", "remote", "-v"], capture_output=True, text=True).stdout
    if re.search(r"gitlab\.com\S*fdroid", out):
        sys.exit("Refusing to run: fdroiddata remote detected. "
                 "The user pushes to fdroiddata manually.")
```
| B1 | GPG "Inappropriate ioctl" | Agent cache flushed | `export GPG_TTY=\$(tty)` + sign via tmux |
| C2 | Missing changelog | Bump didn't create it | Create `changelogs/<versionCode>.txt` before tag |