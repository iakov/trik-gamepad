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
| RuStore | https://help.rustore.ru/rustore/for_developers (JS-rendered, verify manually) |

## Universal checks (every store)

Run before any store-specific review:

| # | Check | How to verify | Pass/fail |
|---|-------|---------------|-----------|
| U1 | **minSdk ≥ store minimum** | `grep minSdk app/src/main/AndroidManifest.xml` | Check per store table below |
| U2 | **No sensitive permissions** | `grep uses-permission app/src/main/AndroidManifest.xml` | Only INTERNET + ACCESS_NETWORK_STATE |
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
| G1 | **minSdk ≥ 34** (from Aug 2025) | Yes | `grep minSdk app/src/main/AndroidManifest.xml` → must be ≥ 34 | Play Console |
| G2 | **Privacy policy in store listing + in-app** | Yes | U3 + U4 must pass | [User Data Policy](https://support.google.com/googleplay/android-developer/answer/1078869) |
| G3 | **Content rating (IARC)** | Yes | Submit Play Console questionnaire | [IARC](https://www.globalratings.com/) |
| G4 | **Screenshots 2–8** (min 320 px) | Yes | Manual: prepare 2–8 screenshots at 1080×1920 or 1920×1080 | [Store listing](https://support.google.com/googleplay/android-developer/answer/9857753) |
| G5 | **Feature graphic 1024×500** | Yes | Manual: prepare landscape banner | [Store listing](https://support.google.com/googleplay/android-developer/answer/9857753) |
| G6 | **Data Safety section** | Yes | Fill Play Console form: no data collected → simplest declaration | [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
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
| F1 | **No proprietary dependencies** | Yes | `grep -ri 'google.*services\|firebase\|crashlytics' app/build.gradle` → zero | [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) |
| F2 | **No tracking/analytics** | Yes | U6 must pass | [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) |
| F3 | **Active maintenance** | Yes | CI green, recent commits | [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) |
| F4 | **Useful to end users** | Yes | Manual: app is functional gamepad | [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) |
| F5 | **Source reproducibility** | Recommended | `./gradlew clean assembleRelease` → compare APK hash | [Reproducibility](https://f-droid.org/docs/Reproducible_Builds/) |
| F6 | **Anti-feature labels** | Yes | Declare: NonFreeNet (connects to network), maybe NoSourceSince | [Anti-Features](https://f-droid.org/docs/Anti-Features/) |

### Amazon Appstore

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| A1 | **minSdk ≥ 21** | Yes | Already met | [Developer docs](https://developer.amazon.com/docs/app-submission/app-submission-checklist.html) |
| A2 | **Privacy policy** | Yes | U3 + U4 must pass | [Agreement](https://developer.amazon.com/docs/app-submission/app-submission-checklist.html) |
| A3 | **Content rating** | Yes | Self-rate (G, PG, PG-13, R, M) | [Developer Console](https://developer.amazon.com/) |
| A4 | **Screenshots 1–10** (1280×800 or 800×1280) | Yes | Manual | [Submission](https://developer.amazon.com/docs/app-submission/app-submission-checklist.html) |
| A5 | **Icon 114×114** | Yes | Extra density needed (not in mipmap) | [Submission](https://developer.amazon.com/docs/app-submission/app-submission-checklist.html) |
| A6 | **Fire OS compatibility** | Yes | Test on Fire tablet (Amazon offers Remote Test Lab) | [Testing](https://developer.amazon.com/docs/app-testing/remote-test-lab.html) |
| A7 | **DRM for video** | No | MJPEG/RTSP streams are DRM-free — no issue | [Policy](https://developer.amazon.com/docs/app-submission/app-submission-checklist.html) |

### RuStore

| # | Requirement | Mandatory? | Check | Source |
|---|-------------|-----------|-------|--------|
| R1 | **Privacy policy in Russian** | Yes | U3 + U4 must pass; policy must have a Russian-language version | [Developer docs](https://help.rustore.ru/rustore/for_developers) |
| R2 | **minSdk ≥ 21** | Yes | Already met | [Developer docs](https://help.rustore.ru/rustore/for_developers) |
| R3 | **Content rating** (0+, 6+, 12+, 16+, 18+) | Yes | Self-rate in RuStore Console | [Rating](https://help.rustore.ru/rustore/for_developers) |
| R4 | **Screenshots 2–5** | Yes | Manual: same screenshots as Google Play | [Publication](https://help.rustore.ru/rustore/for_developers) |
| R5 | **App description in Russian** | Yes | Write Russian description (already exists in `values-ru/strings.xml`) | [Publication](https://help.rustore.ru/rustore/for_developers) |
| R6 | **Developer identity verification** | Yes | Confirm via Gosuslugi (Russian government portal) | [Registration](https://help.rustore.ru/rustore/for_developers) |
| R7 | **152-ФЗ compliance** (personal data law) | Yes | App does not collect personal data → automatically compliant | [152-ФЗ](https://help.rustore.ru/rustore/for_developers) |
| R8 | **No VPN/tor/anonymizer functionality** | Yes | Verify: app only connects to local robots | [Moderation policy](https://help.rustore.ru/rustore/for_developers) |
| R9 | **APK signing** | Yes | Standard Android signing | [Publication](https://help.rustore.ru/rustore/for_developers) |
| R10 | **No Google Play Services dependency** | Recommended | `grep -ri 'google.*services\|gms' app/build.gradle` — should be clean (GMS not available on RuStore devices) | [Compatibility](https://help.rustore.ru/rustore/for_developers) |

**Note:** RuStore documentation is a client-side rendered SPA. The source URL above is the developer docs hub. Specific requirement pages should be verified manually by opening in a browser. RuStore moderation may also request additional documentation under Russian Federation laws.

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