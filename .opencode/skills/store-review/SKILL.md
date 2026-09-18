# Store Readiness Reviewer — TRIK Gamepad

Automated reviewer that checks app readiness for any Android app store.
Invoke with: `ask store-review: <store-name>` or `review for <store>`.

## Sources of claims

Each requirement below cites its official source. When a reviewer check
triggers, the user can verify the claim at the linked URL before acting.

| Store | Source URL |
|-------|-----------|
| Google Play | https://support.google.com/googleplay/android-developer/answer/9857753 |
| Google Play (privacy) | https://support.google.com/googleplay/android-developer/answer/1078869 |
| Google Play (testing) | https://support.google.com/googleplay/android-developer/answer/140504 |
| Huawei AppGallery | https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app |
| Samsung Galaxy Store | https://developer.samsung.com/galaxy-store/requirements |
| F-Droid | https://f-droid.org/docs/Inclusion_Policy/ |
| Amazon Appstore | https://developer.amazon.com/docs/app-submission/app-submission-checklist.html |
| RuStore | https://www.rustore.ru/help/developers/ |

## Universal checks (every store)

Run before any store-specific review:

| # | Check | How to verify | Pass/fail |
|---|-------|---------------|-----------|
| U1 | **minSdk ≥ store minimum** | `grep minSdk app/src/main/AndroidManifest.xml` | Check per store table below |
| U2 | **No sensitive permissions** | `grep uses-permission app/src/main/AndroidManifest.xml` | Only INTERNET + ACCESS_NETWORK_STATE |
| U2a | **Wi-Fi feature declared** | `grep 'uses-feature.*wifi' app/src/main/AndroidManifest.xml` | `android.hardware.wifi` should be `required="true"` (app needs Wi-Fi) |
| U2b | **Location feature opt-out** | `grep 'uses-feature.*location' app/src/main/AndroidManifest.xml` | `android.hardware.location` should be `required="false"` (app does not use location) |
| U3 | **Privacy policy exists** | `test -f PRIVACY.md` | Must exist |
| U4 | **Privacy policy linked in-app** | `grep -r privacy\|PRIVACY app/src/main/kotlin/` | Link to PRIVACY.md or external URL |
| U5 | **Open-source license** | `test -f LICENSE.txt` | Apache 2.0 |
| U6 | **No tracking/analytics SDK** | `grep -ri 'firebase\|crashlytics\|amplitude\|mixpanel\|google-analytics' app/build.gradle gradle/` | Zero matches |
| U7 | **No ads SDK** | `grep -ri 'admob\|facebook.*ad\|unityads' app/build.gradle gradle/` | Zero matches |
| U8 | **APK page size** | `apkanalyzer apk summary app/build/outputs/apk/releaseDebug/app-releaseDebug.apk \| grep page-size` | Must support 16 KB for Android 15+ |
| U9 | **uv.lock is fresh** | `git diff --name-only HEAD -- uv.lock` | Non-empty if pyproject.toml changed |
| U10 | **App builds clean** | `./gradlew test lint lintDebug detekt` | Zero errors |
| U11 | **Full coverage gate** | `./gradlew jacocoTestCoverageVerification` | 85% branch coverage |
| U12 | **APK size within bounds** | `scripts/pr_gate.py app/build/outputs/apk/releaseDebug/app-releaseDebug.apk` | Passes all checks |

## Store-specific checklists

### Google Play

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| G1 | **targetSdk ≥ 34** (from Aug 2025 for new apps) | Yes | `grep targetSdk app/build.gradle` → must be ≥ 34 (currently 36) | [Play Console policy](https://support.google.com/googleplay/android-developer/answer/17134731) |
| G2 | **Privacy policy in store listing + in-app** | Yes | U3 + U4 must pass | [User Data Policy](https://support.google.com/googleplay/android-developer/answer/1078869) |
| G3 | **Content rating (IARC)** | Yes | Submit Play Console questionnaire | [IARC](https://www.globalratings.com/) |
| G4 | **Screenshots 2–8** (min 320 px) | Yes | Manual: prepare 2–8 screenshots at 1080×1920 or 1920×1080 | [Store listing](https://support.google.com/googleplay/android-developer/answer/9857753) |
| G5 | **Feature graphic 1024×500** | Yes | Manual: prepare landscape banner | [Store listing](https://support.google.com/googleplay/android-developer/answer/9857753) |
| G6 | **Data Safety section** | Yes | Fill Play Console form: no data collected → simplest declaration | [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
| G7 | **Android App Bundle (AAB)** | Recommended | `ls -la app/build/outputs/bundle/` — AAB is required for new apps since Aug 2021 | [Play Console](https://developer.android.com/google/play/requirements-tiers-new-apps) |
| G8 | **Developer verification (2026)** | Yes | Complete Android Developer Verification in Play Console | [Play Console](https://support.google.com/googleplay/android-developer/answer/17134731) |
| G7 | **14-day closed test** (new accounts) | Yes | 12+ testers, 14 days minimum engagement | [Testing](https://support.google.com/googleplay/android-developer/answer/140504) |
| G8 | **Play App Signing** | Yes | Enabled in Play Console | [Signing](https://support.google.com/googleplay/android-developer/answer/9842756) |
| G9 | **App description** (short ≤ 80, full ≤ 4000) | Yes | Write EN + RU (match PRIVACY.md languages) | [Store listing](https://support.google.com/googleplay/android-developer/answer/9857753) |

### Huawei AppGallery

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| H1 | **minSdk ≥ 21** | Yes | Already met | [Huawei Developer](https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app) |
| H2 | **Privacy policy** | Yes | U3 + U4 must pass | [Agreement](https://developer.huawei.com/consumer/en/doc/start/10103) |
| H3 | **Content rating** | Yes | Self-rate in AppGallery Connect | [Rating](https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app) |
| H4 | **Screenshots 2–5** (1080×1920) | Yes | Manual: same screenshots as Google Play | [Listing](https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app) |
| H5 | **Feature graphic 800×800** | Yes | Manual: square icon graphic | [Listing](https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app) |
| H6 | **HMS Core compatibility check** | Yes | `grep -r 'hms\|huawei\|pushkit' app/` — should be clean (no HMS dependency) | [Compatibility](https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app) |
| H7 | **Signing certificate** | Yes | Huawei-issued cert via AppGallery Connect | [Signing](https://developer.huawei.com/consumer/en/doc/distribution/app/agc-create_app) |

### Samsung Galaxy Store

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| S1 | **minSdk ≥ 23** | Yes | Already met (minSdk 21 → needs bump to 23?) | [Seller Portal](https://developer.samsung.com/galaxy-store/requirements) |
| S2 | **16 KB page size** (Android 15+, July 2026) | Yes | `apkanalyzer apk summary` → verify page-size alignment | [Samsung notice](https://developer.samsung.com/galaxy-store/) |
| S3 | **Privacy policy** | Yes | U3 + U4 must pass | [Developer Policy](https://developer.samsung.com/galaxy-store/requirements) |
| S4 | **Content rating** | Yes | Self-rate (All, 12+, 15+, 18+) | [Seller Portal](https://seller.samsungapps.com/) |
| S5 | **Screenshots 3+** (1080×1920 portrait) | Yes | Manual: same as Google Play | [Seller Portal](https://seller.samsungapps.com/) |
| S6 | **Icon 512×512** | Yes | Already exists in `app/src/main/res/mipmap-*` | [Seller Portal](https://seller.samsungapps.com/) |
| S7 | **Developer verification** (Sept 2026) | Yes | Complete Samsung developer verification | [Seller Portal](https://developer.samsung.com/galaxy-store/) |
| S8 | **No duplicate with Google Play version** | No | Samsung allows same APK | [Policy](https://developer.samsung.com/galaxy-store/requirements) |

### F-Droid

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| F1 | **FOSS license** (Apache 2.0, GPL, etc.) | Yes | `test -f LICENSE.txt` → Apache 2.0 | [Inclusion Policy §Free Software](https://f-droid.org/docs/Inclusion_Policy/#free-software-requirement) |
| F2 | **No proprietary dependencies** (no GMS/Firebase) | Yes | `grep -ri 'google.*services\|firebase\|crashlytics' app/build.gradle` → zero | [Inclusion Policy §Free Software](https://f-droid.org/docs/Inclusion_Policy/#free-software-requirement) |
| F3 | **No tracking/analytics** | Yes | U6 must pass (zero tracking libraries) | [Inclusion Policy §Free Software](https://f-droid.org/docs/Inclusion_Policy/#free-software-requirement) |
| F4 | **Active maintenance** | Yes | CI green, recent commits, version tags | [Inclusion Policy §Quality](https://f-droid.org/docs/Inclusion_Policy/#quality-control) |
| F5 | **Public source repo** | Yes | GitHub `trikset/trik-gamepad` | [Quick Start Guide §Prepare](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/#prepare-and-compliance-check) |
| F6 | **Version tags** (e.g. `v2.44` for each release) | Yes | `git tag --sort=-creatordate \| head -5` | [Quick Start Guide §Upstream metadata](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/#upstream-metadata) |
| F7 | **short_description.txt** (≤80 chars, no trailing dot) | Yes — only truly mandatory metadata file | Must exist at `fastlane/metadata/android/*/short_description.txt` | [All About Descriptions §Fastlane](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/#fastlane-structure) |
| F8 | **full_description.txt** (≤4000 chars) | Yes — same as F7 | Must exist at `fastlane/metadata/android/*/full_description.txt` | [All About Descriptions §Fastlane](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/#fastlane-structure) |
| F9 | **Anti-feature labels** (if applicable) | Yes | Declare in fdroiddata `.yml` metadata: no anti-features needed (tracking-free, no NonFreeNet — robot runs FOSS, no ads, no NonFreeDeps) | [Anti-Features](https://f-droid.org/docs/Anti-Features/) |
| F10 | **Icon** (512×512 PNG) | For Latest tab | Must exist at `fastlane/metadata/android/*/images/icon.png` | [Latest tab criteria](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/#latest-tab-criteria) |
| F11 | **Screenshot or featureGraphic** | For Latest tab | At least one: `phoneScreenshots/1.png` or `featureGraphic.png` | [Latest tab criteria](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/#latest-tab-criteria) |
| F12 | **What's New changelog** (≤500 chars, name = versionCode) | For Latest tab | `fastlane/metadata/android/*/changelogs/<versionCode>.txt` | [Latest tab criteria](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/#latest-tab-criteria) |
| F13 | **At least one field translated** | For Latest tab | ru locale has short/full descriptions translated | [Latest tab criteria](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/#latest-tab-criteria) |
| F14 | **Source reproducibility** | Recommended | `./gradlew clean assembleRelease` → compare APK hash | [Reproducibility](https://f-droid.org/docs/Reproducible_Builds/) |

**Note:** The fdroiddata build metadata `.yml` file (RepoType, Repo, Builds blocks, License, AutoUpdateMode) lives in the F-Droid GitLab repository (`fdroiddata`), not in this repo. The fastlane metadata above is what we maintain in our source repository. Three equivalent structures are accepted by F-Droid: (a) Fastlane at `fastlane/metadata/android/<locale>/`, (b) Triple-T at `<module>/src/main/play/`, (c) directly in the fdroiddata `.yml` metadata file. Fastlane is the simplest for single-module apps.

### Amazon Appstore

**⚠️ Amazon Appstore for Android was discontinued on August 20, 2025.** You can no longer submit or update apps for Android mobile devices. Only Fire TV, Fire Tablets, and Fire TV built-in (Vega OS) are supported. This checklist is for Fire OS device publishing only — skip unless targeting Fire hardware.

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| A1 | **Fire OS target** (not Android mobile) | Yes | App targets Fire TV/Tablet, not general Android | [Release Notes](https://developer.amazon.com/docs/app-submission/release-notes.html) |
| A2 | **TV-compatible lifecycle** | Yes | Media streams and socket interfaces must release audio focus on navigate-away | [Amazon Docs](https://developer.amazon.com/) |
| A3 | **Privacy policy** | Yes | U3 + U4 must pass | [Appstore Agreement](https://developer.amazon.com/docs/app-submission/presubmission-checklist.html) |
| A4 | **Content rating** | Yes | Self-rate (G, PG, PG-13, R, M) | [Developer Console](https://developer.amazon.com/) |
| A5 | **Screenshots** (Fire device, not Android phone) | Yes | Capture on Fire TV/Tablet, not phone | [Taking Screenshots](https://developer.amazon.com/docs/app-submission/taking-screenshots.html) |
| A6 | **App Bundles (AAB)** supported | Yes | Amazon accepts AAB format (since May 2022) | [App Bundles](https://developer.amazon.com/docs/app-submission/app-bundles.html) |
| A7 | **Fire OS compatibility** | Yes | Test on actual Fire device (Amazon offers Remote Test Lab) | [Testing](https://developer.amazon.com/docs/app-testing/remote-test-lab.html) |

### RuStore

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| R1 | **App functional and stable** (no crashes/errors) | Yes | `test` gate passes, no known crashes | [Requirements §1](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#1) |
| R2 | **Self-contained app** (not just WebView wrapper) | Yes | Verify: native gamepad UI, not a website wrapper | [Requirements §1](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#1) |
| R3 | **App name ≤30 chars**, identical on store and device | Yes | `grep appLabel app/src/main/res/values/strings.xml` | [Publication §info](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication#) |
| R4 | **Category selected** (e.g. Tools) | Yes | Manual: choose in RuStore Console | [Publication §category](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/new-version-app/category) |
| R5 | **Age restriction** (0+, 6+, 12+, 16+, 18+) | Yes | Manual: select in RuStore Console | [Publication §age](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/new-version-app/age-restrictions) |
| R6 | **Content/policy compliance**: no hate speech, porn, violence, illegal goods, IP infringement | Yes | Manual: review app content against requirements | [Requirements §2](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#app-contains) |
| R7 | **minSdk ≥ 21** (pulled from manifest) | Yes | Already met | [Publication §info](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication#) |
| R8 | **Privacy policy** (if handling personal data) | Conditional | App does NOT collect personal data → recommended but optional | [Requirements §3](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#3) |
| R9 | **152-ФЗ compliance** (consent for data collection) | Conditional | No personal data collected → automatically compliant | [Requirements §3](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#3) |
| R10 | **Data security declaration** in console | Yes | Declare INTERNET + ACCESS_NETWORK_STATE only | [Publication §security](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication#) |
| R11 | **No prohibited permissions** | Yes | `grep 'protectionLevel.*signature\|privileged' app/src/main/AndroidManifest.xml` → zero | [Requirements §4](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#4) |
| R12 | **Sensitive permissions declared** | Yes | No sensitive perms → declare "none" in console | [Requirements §4](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#4) |
| R13 | **App supports Russian or English** | Yes | Already met (en + ru locales) | [Requirements §2](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#app-contains) |
| R14 | **Developer contacts** (email required, VK/Website/MAX optional) | Yes | Provide `support@trikset.com` | [Publication §contacts](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication#) |
| R15 | **Developer identity via Gosuslugi** | Yes | Complete in RuStore Console before publishing | [Registration](https://www.rustore.ru/help/developers/developer-account) |
| R16 | **Short description ≤80 chars**, no emoji/special chars | Yes | Manual: write RU short description | [Requirements §6](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#6) |
| R17 | **Full description ≤4000 chars in Russian**, accurate | Yes | Manual: write RU full description | [Requirements §6.3](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#63) |
| R18 | **Icon 512×512** px, 1:1, PNG/JPG, ≤1 MB, full bg fill | Yes | Already exists; upload to console | [Requirements §6.4](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#64) |
| R19 | **Screenshots ≥3** per device type, 16:9 recommended, max 2160×3840, ≤3 MB, actual UI | Yes | Manual: capture 3+ at 1080x1920 or 1920x1080 | [Requirements §6.5](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#65) |
| R20 | **"What's new"** for version updates | Yes | Manual: write RU changelog per version | [Publication §whatsnew](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication#) |
| R21 | **APK signed**, size ≤5 GB, version increasing | Yes | Standard signing already configured | [Publication §upload](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication#upload-app-file) |
| R22 | **Version ≥ other stores** | Yes | RuStore version must not lag behind Google Play | [Requirements §1](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#1) |
| R23 | **No GMS dependency** | Recommended | `grep -ri 'google.*services\|gms' app/build.gradle` → zero matches | [Compatibility](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps) |
| R24 | **Test account** if app requires auth | Conditional | App has no auth → N/A | [Requirements §1](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps#1) |

**Source docs:** Full RuStore developer docs at `https://www.rustore.ru/help/developers/`. Requirements: `rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps`. Publication guide: `rustore.ru/help/developers/publishing-and-verifying-apps/app-publication`. These are static server-rendered pages — links are stable.

The following scripts should be wired into CI for automatic store-readiness
gating. Each maps to one or more checks above:

| Script | Purpose | Checks covered |
|--------|---------|---------------|
| `scripts/gate.py` | Full quality gate (unit tests, lint, detekt, jacoco, spotbugs, spotless) | U10, U11 |
| `scripts/pr_gate.py` | APK-level analysis (method count, permission drift, blob size, density gaps) | U8, U12, S2 |
| `scripts/check_device_identifiers.py` | Device-ID scrub in docs/commits | G6 privacy compliance |
| `scripts/check_translations.py` | Locale completeness for store descriptions | G9 app description |
| **New: `scripts/check_privacy.py`** | Verifies PRIVACY.md exists and is linked in-app | U3, U4, G2, H2, S3, A2 |
| **New: `scripts/check_permissions.py`** | Audits manifest permissions against allowed list | U2, G6 |
| **New: `scripts/check_tracking.py`** | Scans for tracking/analytics/ad SDKs | U6, U7, F1, F2 |
| **New: `scripts/check_android15_page_size.py`** | Runs `apkanalyzer` page-size check | U8, S2 |

### Pre-PR gate hook (AGENTS.md)

Add to the "Before push" section:

```yaml
- If this is an upstream-facing PR:
  1. Run `scripts/check_privacy.py` — privacy policy present and linked
  2. Run `scripts/check_permissions.py` — no permission drift
  3. Run `scripts/check_tracking.py` — no tracking SDKs
  4. Run `scripts/pr_gate.py` — APK-level analysis
  5. Verify `uv.lock` is fresh after `pyproject.toml` changes
```

## Reviewer invocation templates

### Full review (every store)

```
review for: all
```

### Single store

```
review for: google-play
review for: huawei
review for: samsung
review for: f-droid
review for: amazon
review for: rustore
```

### CI compliance only

```
check store-gates
```

### Output format

The reviewer returns a markdown table per store with columns:

| Check | Status | Mandatory | Action needed |
|-------|--------|-----------|---------------|
| minSdk ≥ 34 | ❌ FAIL | Yes | Bump to 34 in AndroidManifest.xml |
| Privacy policy linked | ✅ PASS | Yes | — |

Each row includes a `scripts/` or manual action to resolve the failure.