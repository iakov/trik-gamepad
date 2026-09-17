# Store-Review Results — TRIK Gamepad

Date: 2026-09-18 · Branch: `dev` · APK: `app/build/outputs/apk/releaseDebug/app-releaseDebug.apk` (2.42-API21, versionCode 210242)

Legend: ✅ PASS · ❌ FAIL (code-fixable) · ⚠️ NOTE (store-console / policy / suggestion)

## Universal checks (U1–U12)

| # | Check | Result | Evidence / action |
|---|-------|--------|-------------------|
| U1 | minSdk ≥ store minimum | ⚠️ | minSdk 21 (build.gradle). Meets Huawei/Amazon/RuStore (21); fails Samsung (23) and Google Play new-app (34). See S1/G1. |
| U2 | No sensitive permissions | ✅ | Manifest has only INTERNET + ACCESS_NETWORK_STATE (VIBRATE commented out). |
| U3 | Privacy policy exists | ✅ | `PRIVACY.md` at repo root (EN+RU, contact support@trikset.com). |
| U4 | Privacy policy linked in-app | ✅ FIXED | Was ❌ (no reference in `app/src/main/kotlin/`). Added `privacyPolicy` preference row in About section (`pref_app.xml`) + `initializePrivacyPolicyField()` in `SettingsFragment.kt` opening `https://github.com/trikset/trik-gamepad/blob/master/PRIVACY.md`. |
| U5 | Open-source license | ✅ | `LICENSE.txt` exists. |
| U6 | No tracking/analytics SDK | ✅ | Zero matches for firebase/crashlytics/amplitude/mixpanel/google-analytics in `app/build.gradle` + `gradle/`. |
| U7 | No ads SDK | ✅ | Zero matches for admob/facebook-ad/unityads. |
| U8 | APK page size | ✅ | `zipalign -c -P 16 -v 4` → "Verification successful" (16 KB page alignment OK). |
| U9 | uv.lock fresh | ✅ | No diff on `uv.lock`; `pyproject.toml` unchanged. |
| U10 | App builds clean | ⏳ | Gate run in Phase C (`test lint lintDebug detekt`). |
| U11 | Full coverage gate | ⏳ | Gate run in Phase C (`jacocoTestCoverageVerification`). |
| U12 | APK size within bounds | ✅ FIXED | `pr_gate.py` passed all 7 checks (4.5 MB, 58 473 dex refs, 5 densities, no large blobs). Script had a Linux bug (`_local_properties_sdk()` prepended `C:`; `apkanalyzer()` never tried the extensionless SDK binary) — fixed in `scripts/pr_gate.py`. |

## Google Play (G1–G9)

| # | Check | Result | Action |
|---|-------|--------|--------|
| G1 | minSdk ≥ 34 (new apps, Aug 2025) | ❌ | minSdk 21. **Policy decision**: bumping to 34 drops Android 5.x–13 support. Not code-fixable without product decision. |
| G2 | Privacy policy in listing + in-app | ✅ | U3 + U4 pass (after fix). |
| G3 | Content rating (IARC) | ⚠️ | Console questionnaire. |
| G4 | Screenshots 2–8 (≥320 px) | ⚠️ | Console: prepare 1080×1920 / 1920×1080. |
| G5 | Feature graphic 1024×500 | ⚠️ | Console: landscape banner. |
| G6 | Data Safety section | ⚠️ | Console: "no data collected" declaration (only INTERNET + ACCESS_NETWORK_STATE). |
| G7 | 14-day closed test | ⚠️ | Console: 12+ testers, 14 days. |
| G8 | Play App Signing | ⚠️ | Console: enable. |
| G9 | App description (short ≤80, full ≤4000) | ⚠️ | Console: EN + RU. |

## Huawei AppGallery (H1–H7)

| # | Check | Result | Action |
|---|-------|--------|--------|
| H1 | minSdk ≥ 21 | ✅ | minSdk 21. |
| H2 | Privacy policy | ✅ | U3 + U4 pass (after fix). |
| H3 | Content rating | ⚠️ | Self-rate in AppGallery Connect. |
| H4 | Screenshots 2–5 (1080×1920) | ⚠️ | Same as Google Play. |
| H5 | Feature graphic 800×800 | ⚠️ | Square icon graphic. |
| H6 | HMS Core compatibility | ✅ | No hms/huawei/pushkit in `app/src/`. |
| H7 | Signing certificate | ⚠️ | Huawei-issued cert via AppGallery Connect. |

## Samsung Galaxy Store (S1–S8)

| # | Check | Result | Action |
|---|-------|--------|--------|
| S1 | minSdk ≥ 23 | ❌ | minSdk 21. **Policy decision**: bump to 23 drops Android 5.0/5.1. No API usage requires >21 (verified: no API-22+ only calls). |
| S2 | 16 KB page size (Android 15+) | ✅ | zipalign -P 16 verified. |
| S3 | Privacy policy | ✅ | U3 + U4 pass (after fix). |
| S4 | Content rating | ⚠️ | Self-rate (All, 12+, 15+, 18+). |
| S5 | Screenshots 3+ (1080×1920) | ⚠️ | Same as Google Play. |
| S6 | Icon 512×512 | ✅ | mipmap-* present (48–192 px); 512×512 store icon uploaded separately in Seller Portal. |
| S7 | Developer verification (Sept 2026) | ⚠️ | Complete Samsung developer verification. |
| S8 | No duplicate with Google Play | ✅ | Same APK allowed. |

## F-Droid (F1–F6)

| # | Check | Result | Action |
|---|-------|--------|--------|
| F1 | No proprietary dependencies | ✅ | Zero google-services/firebase/crashlytics. |
| F2 | No tracking/analytics | ✅ | U6 passes. |
| F3 | Active maintenance | ✅ | CI green, recent commits. |
| F4 | Useful to end users | ✅ | Functional gamepad app. |
| F5 | Source reproducibility | ⚠️ | Recommended: `./gradlew clean assembleRelease` + hash compare. |
| F6 | Anti-feature labels | ⚠️ | Declare NonFreeNet (network service). |

## Amazon Appstore (A1–A7)

| # | Check | Result | Action |
|---|-------|--------|--------|
| A1 | minSdk ≥ 21 | ✅ | minSdk 21. |
| A2 | Privacy policy | ✅ | U3 + U4 pass (after fix). |
| A3 | Content rating | ⚠️ | Self-rate (G–M). |
| A4 | Screenshots 1–10 | ⚠️ | 1280×800 or 800×1280. |
| A5 | Icon 114×114 | ❌ | Not in mipmap set (48–192 px). **Suggestion**: add 114×114 density or upload separately. |
| A6 | Fire OS compatibility | ⚠️ | Test on Fire tablet (Remote Test Lab). |
| A7 | DRM for video | ✅ | MJPEG/RTSP DRM-free — no issue. |

## RuStore (R1–R24)

| # | Check | Result | Action |
|---|-------|--------|--------|
| R1 | Functional and stable | ⏳ | Gate run in Phase C. |
| R2 | Self-contained app | ✅ | Native gamepad UI, not a WebView wrapper. |
| R3 | App name ≤30 chars | ✅ | "TRIK Gamepad" (12 chars). |
| R4 | Category selected | ⚠️ | Console: choose category. |
| R5 | Age restriction | ⚠️ | Console: select 0+/6+/12+/16+/18+. |
| R6 | Content/policy compliance | ✅ | No hate speech/porn/violence/illegal goods/IP infringement. |
| R7 | minSdk ≥ 21 | ✅ | minSdk 21. |
| R8 | Privacy policy (personal data) | ✅ | No personal data collected → recommended; U3+U4 pass anyway. |
| R9 | 152-ФЗ compliance | ✅ | No personal data collected → automatically compliant. |
| R10 | Data security declaration | ✅ | INTERNET + ACCESS_NETWORK_STATE only. |
| R11 | No prohibited permissions | ✅ | Zero signature/privileged protectionLevel. |
| R12 | Sensitive permissions declared | ✅ | None → declare "none". |
| R13 | Supports Russian or English | ✅ | en + ru locales (also fr/de/vi). |
| R14 | Developer contacts | ✅ | support@trikset.com in PRIVACY.md. |
| R15 | Developer identity via Gosuslugi | ⚠️ | Console: complete before publishing. |
| R16 | Short description ≤80 chars | ⚠️ | Console: RU short description. |
| R17 | Full description ≤4000 chars RU | ⚠️ | Console: RU full description. |
| R18 | Icon 512×512 | ✅ | Exists; upload to console. |
| R19 | Screenshots ≥3 | ⚠️ | Console: 3+ at 1080×1920 / 1920×1080. |
| R20 | "What's new" per version | ⚠️ | Console: RU changelog. |
| R21 | APK signed, ≤5 GB, version increasing | ✅ | Standard signing configured. |
| R22 | Version ≥ other stores | ✅ | 2.42 current. |
| R23 | No GMS dependency | ✅ | Zero google-services/gms. |
| R24 | Test account | ✅ | No auth → N/A. |

## Code changes made (Phase B)

1. **U4 fix — in-app privacy policy link** (`SettingsFragment.kt`, `pref_app.xml`, 5× `strings.xml`):
   - `const val SK_PRIVACY_POLICY = "privacyPolicy"` + `PRIVACY_POLICY_URL` in companion.
   - `initializePrivacyPolicyField()` opens the URL via `Intent(ACTION_VIEW)`; called from `onCreatePreferences()`.
   - Preference row in About category; `privacy_policy` / `privacy_policy_summary` strings added in en/ru/fr/de/vi (parity verified by `check_translations.py --sync`: 150 keys OK).
2. **U12 fix — `scripts/pr_gate.py` Linux bugs**:
   - `_local_properties_sdk()` prepended `C:` to POSIX paths (Windows-only logic ran unconditionally) → now guarded by `os.name == "nt"`.
   - `apkanalyzer()` never tried the extensionless `apkanalyzer` binary inside the SDK dir → added candidate.

## Store-console-only items (recorded, not code-fixable)

Screenshots (G4/H4/S5/A4/R19), feature graphics (G5/H5), Data Safety form (G6), content ratings (G3/H3/S4/A3/R5), closed test (G7), Play App Signing (G8), descriptions (G9/R16/R17), developer verification (S7/R15), app category (R4), "What's new" (R20), Amazon 114×114 icon (A5), F-Droid anti-features (F6).

## Policy decisions needed (not code-fixable)

- **G1**: Google Play new-app minSdk ≥ 34 vs current 21.
- **S1**: Samsung minSdk ≥ 23 vs current 21.