# DESIGN.md — trik-gamepad UX & accessibility conventions

<!-- encoding: utf-8 -->

Scope: the *why* behind user-facing UI/UX decisions, so future changes stay
consistent and are not re-litigated. This is a contract: conventions here are
enforced in reviews and (where possible) by tests/lint. AGENTS.md keeps only
short trigger lines pointing at the named sections below — read a section when
you touch that surface, never restate its rationale in AGENTS.md.

Rationale for the decisions themselves (problem → alternatives → why) lives in
`DECISIONS.md` "Campaign 15" and its execution record in `MEMORY.md`.

## Scenarios & use-cases (the contract)

The product is a **gamepad for a robot over Wi-Fi**, optionally with an FPV video
feed. The scenarios below are the contract every product decision must satisfy.
A decision that breaks a scenario is a design regression — re-design, never
silently patch around it. Decisions in `DECISIONS.md` cite scenario IDs.

### Core scenarios (the typical setups)

| ID | Scenario | Required behavior |
|----|----------|-------------------|
| **S1** | Default WAP: phone on the robot's own access point, robot in range and alive | video streams; control works |
| **S2** | Default WAP: robot moves out of range (keepalive drops), then returns; user taps to reconnect | reconnect on tap; video recovers on the reconnect edge; no hammering while the WAP is gone |
| **S3** | Classroom hotspot: one robot, video host == robot host | control state is a valid reachability hint, never a hard gate |
| **S4** | Classroom hotspot: **video from a different IP** than the control robot (separate camera / copy feature) | video is fully decoupled from control: never touch a playing stream; self-heal a dead stream regardless of control state |
| **S5** | Video-only (empty host, e.g. competition FPV, remote control prohibited) | video always self-heals; no control affordances shown |
| **S6** | Two controllers / two robots (drive robot A, watch robot B's camera) | video independent of control (same as S4) |

### Extended scenarios (confirmed additions)

| ID | Scenario | Required behavior |
|----|----------|-------------------|
| **S7** | Same-host watch-only (spectator phone; competition with host set but control off) | video still recovers — a dead stream must not stay frozen just because control never connects |
| **S8** | Hostname vs IP mismatch (`trik-01` vs `192.168.1.5`) | the app treats a string mismatch as "different target" → video recovers (safe direction) |
| **S9** | Robot reboot / services restart (video boots before/after control) | video recovers at the first reconnect edge; never stays frozen |
| **S10** | Half-open control (keepalive not yet expired) | video recovery is allowed; a failed reload is cheap and bounded |
| **S11** | User re-hosts control while an old video URL persists | old camera may recover; nothing is frozen |
| **S12** | Multiple gamepads, one robot (classroom) | fully independent per phone; no cross-coupling |
| **S13** | WAP ↔ cellular transition (user-managed phone setting; robot WAP has no internet) | both control and video sockets/connections (http raw socket and https) route over the Wi-Fi AP whenever one exists |
| **S14** | Robot up, control port blocked/off, video port open | video still recovers (competition edge) |

### Derived principles (P1–P8)

- **P1 — Video is independent of control.** A different-host video stream is
  never gated on the control connection (S4/S6/S11/S8).
- **P2 — `isPlaying` is the authority.** A playing stream is never touched;
  a dead/stalled stream (`isPlaying == false`) is the only "needs recovery"
  signal (all scenarios). Stall detection is inherent: the raw-socket HTTP
  client sets a 5 s `SO_TIMEOUT`, so a silent stall surfaces as a stream error.
- **P3 — Video-only is first-class.** Empty host = control disabled; video
  always self-heals (S5).
- **P4 — Control state is a hint, not a truth.** Same-host control state may
  influence *when* to retry, but never permanently blocks video recovery (S2/
  S3/S9).
- **P5 — Reconnect is immediate, never ticked.** A control-reconnect edge
  reloads video right away (S2/S3/S9/S13).
- **P6 — Recovery is bounded.** The retry tick is fixed (5 s); every failed
  reload closes its socket, so a dead robot costs one cheap failed TCP connect
  per tick, never a leak (the 30 s-restart disease was a socket leak, not
  hammering).
- **P7 — Video fidelity.** The decoded video frame must reach the compositor
  with zero color transformation — no ColorMatrix, no setColorFilter, no bitmap
  post-processing. If a plain `canvas.drawBitmap` changes color perception, it
  is a bug. The HUD readability scrim and all overlay layers are alpha-only;
  they darken their own pixels without modifying the decoded bitmap. (Note:
  some video processing algorithms — e.g. contrast enhancement, white-balance
  correction for cheap cameras in dark rooms — could improve the driving
  experience. This is a deferred research topic; any future implementation must
  be opt-in and configurable, never applied to the raw stream by default.)
- **P8 — Overlay design flaw: darkened video (RESOLVED).** The translucent glass fills
  (pad backgrounds, pill backgrounds) darkened the video behind them. The total
  overlapped area (~55% of screen) reduced the visible video surface. Resolved by
  removing all glass fills — pads, pills, badges, and the settings button now have
  transparent backgrounds with only accent-colored strokes as borders. The readability
  scrim (top/bottom gradient edges) is kept so controls stay legible over bright video.
  The clean video area is restored to ~100% of the screen, with only thin border
  outlines on controls.

### Empty-value semantics

An **empty value is a deliberate "disabled" state**, not a missing one:

- empty robot host → control disabled (video-only, S5);
- empty video URI → video disabled (control-only).

Two distinct "no stored URI" cases exist and are both shown truthfully: an
**unset** URI means the app streams the value derived from the robot host
(`http://<host>:8080/?action=stream` — the "Defaults are as useful as possible"
behavior), and the row shows that derived URL; an **explicitly-empty** URI
means video is disabled, and the row shows "No stream URI set". The row never
shows "No stream URI set" while the app actually streams (see "Every setting
shows its current value").

"Empty for disabled" is documented here; the Settings rows show the current
value with the existing empty-state label (the video URI shows "No stream URI
set"; a blank host is a valid video-only configuration).

______________________________________________________________________

## Section index

| Section | Applies to |
|---------|------------|
| Scenarios & use-cases | every product decision (the contract) |
| Defaults are as useful as possible | any new preference/field |
| Every setting shows its current value | preference summaries |
| Ellipsis on dialog rows | preference titles |
| Option descriptions | every toggle/list/field |
| Color is never the only signal | connection status, HUD state |
| Touch targets | interactive controls on the gamepad screen |
| Magic button symbols (advanced) | Advanced > Magic buttons |
| About system vs Copy report | About category |
| System theme | day/night, gamepad vs Settings |
| HUD themes (Type 1) | the gamepad chrome: pads, buttons, gear, pill, chip |
| Robot-target chip | top-left chip + connection-tone semantics |
| Localization | strings, locales, translations |
| WCAG | contrast + touch-target regression tests |
| Connection & video state UX | status pill, reconnect badge |
| Haptics | pads, magic buttons, settings gear |
| Video player abstraction | VideoPlayer interface, MJPEG and RTSP implementations, factory |
| Gamepad protocol (source of truth) | every protocol change, TCP and UDP |

______________________________________________________________________

## Defaults are as useful as possible

Every field's default is the **most useful choice for a typical user**, and the
UI **pre-fills defaults rather than showing a blank**. A blank input where a
sensible default exists is a bug. Examples: the magic-button glyphs pre-fill
with ▲ ■ ● ✕ ◆ when unset; the video-URI summary shows "No stream URI set"
instead of an empty row.

## Every setting shows its current value

A value-bearing setting always shows its current value in its summary:
verbosity → `Info · …`, seekbars → `12 · Smaller = more sensitive…`, the video
URI → the **effective** URI, host/port/keepalive → the stored value. Static
"hint" summaries only remain where the row itself is an *action* (e.g. "Reset
video URI"), never a value. The seekbar fallback is the XML default, not a
fabricated 0, so a fresh install shows the real value. An **empty** value is a
deliberate "disabled" state (see "Scenarios & use-cases — Empty-value
semantics"), not a missing one: the video-URI row then shows "No stream URI
set", and a blank host is a valid video-only configuration.

The video-URI row shows the **effective** value — the stored URI, else the one
derived from the robot host (`http://<host>:8080/?action=stream`) — never a
raw prefs read. A fresh install with the default host set therefore shows the
derived URL (the app *does* stream it), not a misleading "No stream URI set";
the label appears only when video is genuinely disabled (blank host and no URI,
or an explicitly-empty URI). The row and the runtime share one helper
(`SettingsFragment.effectiveVideoUri`) so they can never disagree; a host edit
refreshes the row too (the derived value follows the host).

## Ellipsis on dialog rows

A row that opens an input dialog ends with "…" (U+2026): `Robot IP address…`,
`Wheel sensitivity…`, `Delete a preset…`. Switches, immediate-action rows and
sub-screen rows stay plain. Enforcement: the strings themselves carry the
ellipsis; lint `TypographyEllipsis` guards the character.

## Option descriptions

Every option carries a description, and descriptions are **state-aware**
(`summaryOn`/`summaryOff`): flipping "Share logs without editing" changes the
explanation to match the new state. A setting with no explanation is a bug —
users must understand what flipping a switch or picking a value does.

## Color is never the only signal

State conveyed by a color change is also conveyed by text/announcement:

- the gear border recolors (green/amber/red) **and** its contentDescription
  carries the state (`Connected. Toggle system bars`, …);
- connection transitions are announced via `announceForAccessibility`
  (deduped, target-gated; see `ConnectionAnnouncer`).

Rationale: the connection state was color-only (invisible to screen readers).

## Touch targets

Interactive controls on the gamepad screen are ≥ 48dp: the tap-to-connect pill
(`minHeight 48dp`), the magic buttons (`minWidth`/`minHeight 48dp`), the gear
(`touch_target_min` 48dp). Non-interactive indicators (spinner, FPS) may be smaller. Enforced by
`TouchTargetSizeTest` + `MagicButtonPanelTest`.

## Magic button symbols (advanced)

The five per-button glyphs are edited in one "Button symbols…" dialog
(Advanced > Magic buttons, below "Number of buttons"). The dialog pre-fills the
resolved symbols (defaults ▲ ■ ● ✕ ◆ when unset), offers "Use default
symbols", and saves the whole array. Storage stays under the legacy
`magicSymbol1..5` keys, **independent of** `magicButtonCount` (the count only
gates how many buttons are shown; editing symbols never touches it). Glyphs are
display-only — the protocol stays numeric `btn N down`.

## About system vs Copy report

"About system" shows a short device spec and **copies exactly that short spec**
on tap. "Copy report" is the row that copies the **full diagnostic report**
(see Campaign 14). The two rows never copy the same payload, and the summary
preview matches what "About system" copies ("Tap to copy — <spec>").

## System theme

The app follows the system light/dark mode (`Theme.AppCompat.DayNight`). The
**gamepad HUD stays dark in both modes** — it sits over full-screen robot video
with translucent dark elements (pill, spinner, gear, button fills) that must
stay visible over bright frames — via an explicit black window background and
`forceDarkAllowed=false`. The **light** theme is for Settings, dialogs and
system chrome. Verified by pixel-sampled screenshots in light (250,250,250)
and dark (48,48,48) mode.

## HUD themes (Type 1)

The gamepad chrome ships as **"Type 1"** — a glass/arcade look (accent-colored
border outlines, brand-green stroke, borderless bare-circle magic buttons,
one tintable pad-chrome vector). Two principles:

- **XML-first, code-only-where-runtime.** Shapes, gradients, ripples, corners,
  padding and styles live in `hud_*` resources and `Hud.*` styles;
  Kotlin holds *only* the connection-state accent tint (`Drawable.setTint` /
  `setTextColor` / `GradientDrawable.setStroke`). Rationale: Android resources
  resolve at inflation and `ColorStateList` selectors key on a fixed framework
  state set, so a four-value app-defined connection state has no XML hook
  (see DECISIONS.md "[2026-08-12] Type 1 HUD theme").
- **A future theme = a parallel resource set.** Type 2/3 swaps the `hud_*`
  drawables/styles/colors and the `ConnectionIndicator` palette — no layout or
  logic changes. Settings exposes one greyed-out "HUD theme" row (current value
  "Type 1"); it lights up when more than one theme exists.

### Pads (layout + visuals)

- **Placement:** the two pads are centered in their screen halves (`controlsOverlay`
  = a full-screen `LinearLayout` of two `weight=1` gravity-centered `FrameLayout`s;
  each pad is `@dimen/hud_pad_size = 260dp` square maximum). Pad centers land at 25%/75%
  screen width, vertically centered over the video.

- **Adaptive sizing (2026-08-24):** on narrow screens the pad size shrinks to
  avoid overcrowding. `SquareTouchPadLayout.onMeasure` computes
  `padPx = min(260dp in px, screenTallestPx × 0.63)`. The XML `hud_pad_size`
  (260dp) is the maximum cap; the formula reduces it on devices where the taller
  screen dimension is < 413dp (e.g. Honor X7c at 360dp → ~227dp). The result is
  cached per view instance (activity recreate resets it). Reference device table:

  | Device | Landscape height (dp) | Pad size (dp) | Notes |
  |--------|-----------------------|---------------|-------|
  | Galaxy S10e | 393 | 247 → **260** (capped) | Reference target |
  | Galaxy S24 | 407 | 256 → **260** (capped) | Slightly taller, still capped |
  | Honor X7c | 360 | **227** | Narrowest surveyed |
  | Surveyed min | 384 | 242 → **260** (capped) | 30 top-selling phones 2023-2027 |

- **Chrome:** a single tintable vector (`hud_pad_chrome`) with a solid inner
  ring, full crosshair lines through the center and four edge arrows, plus a
  **dashed outer ring drawn in code** (`SquareTouchPadLayout.onDraw`; vector
  drawables cannot express dash patterns). The joystick **knob** is a radial
  gradient (accent → darkened edge) with a translucent glow halo and a bright
  center dot, drawn in `onDraw`, following the touch point.

- **Pad glass panel:** `hud_pad_glass` (transparent fill, accent-colored stroke border,
  rounded corners). The pad chrome is a single child `ImageView` tagged
  `padChrome` (crosshair rings, full lines and edge arrows in one tintable
  vector) recolored via one `SRC_IN` filter in
  `SquareTouchPadLayout.setAccent` — same connection-state tone as the gear/pill.

- **Measurement trap (fixed C18):** `SquareTouchPadLayout.onMeasure` must measure
  its children (`super.onMeasure(squareSpec, squareSpec)` after the square
  `setMeasuredDimension`), or the chrome collapses to 0×0 and the pad renders
  as an empty glass panel (see DECISIONS.md "[2026-08-12] Pad render + layout").

## Robot-target chip

The top-left glass chip shows the robot target and reflects **robot control
status** (not the app's UI state):

- Text: the configured host → else the video stream's host (video-only mode) →
  else a `---.---.---.---` filler (never blank; DESIGN.md "Defaults are as useful
  as possible").
- Tone = the connection-state accent (green Connected / amber Connecting / sepia
  idle-standby / red error) via the pure `ConnectionIndicator` — same signal the
  gear border, pads and pill use, so the whole HUD reads one state.
- Tap opens the **robot/target settings** screen (host/port/video/network/
  presets); the gear opens the **app settings** screen. The chip text is
  deliberately not selectable (it is an action, not copyable content); the host
  is copied from Settings.
- **Compact chrome, fixed 14sp text:** the chip keeps the pill background but is
  deliberately smaller than the status pill (28dp min-height, tight 8/4dp
  padding, 8dp margin) so it stays out of the video view. The sub-48dp touch
  target is an **accepted deviation** (recorded in DECISIONS.md "[2026-08-12]
  Pad render + layout"): the chip is a read-only status row whose primary
  interaction surface is the settings screen it opens, and a full 48dp target
  made the chip dominate the top-left of the video.

## Localization

- The app ships `en` + `ru` + `fr` + `de` + `vi` (`resourceConfigurations` +
  `values-*/strings.xml` + `res/xml/locales_config.xml` for the Android 13+
  per-app language picker).
- **Reuse framework strings first**: an exact match for a typical button/label
  uses `@android:string/*` (e.g. `copy`, `cancel`) — the OS localizes those for
  every device locale for free. Only strings with no framework equivalent
  become app resources.
- Every locale has full key parity and matching format specifiers. The
  **deterministic sync guard** `scripts/check_translations.py --sync` runs in
  the canonical gate — translations cannot drift into a commit.
- Online **back-translation** (`--back-translate`, MyMemory API) is a one-off
  review aid run when translations change, never a recurring gate.
- Russian is the first additional language and gets a **human (native-speaker)
  review**; fr/de/vi are machine-drafted + machine back-translation verified.

## WCAG

WCAG 2.x AA is enforced by regression tests, not by hand:

- `WcagContrastTest` computes relative-luminance ratios for the actual color
  resources (magic-button text/fills, status pill, placeholder, gear border)
  and asserts ≥ 4.5:1 for normal text and ≥ 3.0:1 for large text / UI
  components.
- `TouchTargetSizeTest` asserts the 48dp minimum for interactive controls.
  A color change that drops below threshold fails the suite.

## Connection & video state UX

- The status pill is **symbol-only** in the Type 1 HUD: a rotating arrow (`↺`)
  while connecting and a power glyph (`⏻`) when disconnected, tinted to the
  connection accent. The *target* (`Connecting to host:port:` / `Tap to connect:`)
  is carried in the pill's `contentDescription` (announced for screen readers;
  the robot chip already shows the host visually). A blank host (video-only
  mode) hides the pill entirely — there is nothing to connect to.
- **Video recovery is control-independent (scenarios S3/S4/S5/S6/S7/S14).** A
  dead stream reloads whenever a URL is configured, the view is not playing and
  the activity is resumed — the control connection never gates the video. This
  is the scenario contract: video from a different IP, a spectator phone, a
  competition with control off, and an idle gamepad after a robot reboot all
  recover. The control-`Connected` edge reloads immediately as a bonus (a pad
  touch reconnects control → instant video, not a ≤5 s tick wait).
- The loading spinner is shown whenever a stream URL is configured and a load is
  in flight (first load and reconnect), but a reload of a stream that **was
  playing** additionally shows a `Video reconnecting:` badge, so the user can
  tell a reconnect apart from the first load. Stall detection ("no video
  signal") was deliberately **not** added: a robot with video disabled
  legitimately keeps the spinner cycling (see DECISIONS.md).

## Two-layer HUD layout & the error pill

- **Two layers in `activity_main.xml`** (child order = z-order, no
  `bringToFront()`): the pads layer (`controlsOverlay`, full-screen, two
  `weight=1` gravity-centered halves holding 260dp pads with
  `layout_gravity="center"`) is declared FIRST, then the visuals. The visuals
  always draw and receive touches above the pads. Edge insets share one dimen
  `hud_half_glyph` (~7dp = half a caption glyph): chip top, gear left/bottom,
  buttons bottom.
- **Edge-pinned controls live in an inset-aware container** (`@+id/hudControls`,
  a full-screen RelativeLayout wrapping the chip, gear and magic buttons).
  `MainActivity` pads it per edge from the window insets (`systemBars` |
  `displayCutout` | `systemGestures` | `mandatorySystemGestures`), so the
  controls clear the status-bar/shade strip, the display cutout and the
  gesture-nav zones — a bare-edge chip/gear would sit inside those OS strips
  and the system swallows their touches on physical devices (the top-left
  chip's taps were eaten by a Samsung shade strip; see DECISIONS.md
  "Inset-aware HUD container"). The video stays full-bleed as a sibling below
  the container; the center pills stay in `main`.
- **Error feedback is a content-sized glass pill** (`connectionError`,
  `Hud.GlassPill`, wrap_content → always fits its message), shown by
  `ConnectionFeedback.error()` for real connection errors: fade-in, auto-dismiss
  (~3.5 s), positioned at the vertical midpoint between the status pill and the
  magic-button row so it never covers either. It replaced the Material Snackbar
  (and let us drop the `material` dependency); the persistent state colors stay
  on the gear border and the chip glyphs.
- **Fixed symbols are bundled glyphs, not system-font text**: the pill ⏻/↺,
  gear ⚙ and magic-button defaults render from `res/font/symbols_mono.ttf`
  (a cmap-verified DejaVuSansMono Nerd Font subset) so they render identically
  on every device; anything else a user types falls back to the system font.

## Haptics

- **Haptic feedback is deliberate and sparse** (user-corrected in C24: the old
  pad fired per-move feedback that kept coming after the finger lifted — "very
  annoying and laggy"). Never continuous: wheel/slider drags and pad moves do
  not vibrate.
- **Generic constants only.** The schema maps to the legacy trio
  `KEYBOARD_TAP` (light) / `VIRTUAL_KEY` (medium) / `LONG_PRESS` (strong —
  resolves to `EFFECT_HEAVY_CLICK`, the strongest effect the S25 advertises).
  The newer API-30 constants (`CONTEXT_CLICK`/`CONFIRM`/`REJECT`) are avoided:
  their device mapping is less predictable, and the whole app must behave the
  same on every API level and OEM HAL (no SDK branching in `Haptics.constant`).
- **Event → level:**
  - Pad thumb **down** → light `TICK` (one discrete tick per touch — the
    "every interaction is noticeable" anchor; it is NOT per-move feedback).
  - Pad **release** → medium `CLICK`.
  - Pad **move / cancel** → nothing (C24 noise; cancel is an interruption).
  - Magic buttons, settings gear, target chip → **one strong `HEAVY` pulse**
    (user-chosen "one strong for button"; the gear was the missing haptic,
    reported by the user).
  - Control **connected** → one medium `CLICK` (edge-triggered, not per
    re-emission).
  - Unexpected **disconnected** → **two strong `HEAVY` pulses with 200 ms
    between their starts** (user-chosen "2 strong is enough for disconnect").
    The gap keeps the pulses distinct instead of letting the vibrator coalesce
    them. App-pause disconnects never alert.
- All haptics **respect the system haptics setting** (`performHapticFeedback`
  without `FLAG_IGNORE_GLOBAL_SETTING`), and need **no VIBRATE permission** —
  `performHapticFeedback` runs through the system's `vibrateWithoutPermissionCheck`
  path (only the direct `Vibrator.vibrate` entry enforces VIBRATE). Using
  `Vibrator.vibrate` is rejected: it would both require the permission and
  bypass the system toggle. Asserted in Robolectric via
  `shadowOf(view).lastHapticFeedbackPerformed()` against
  `Haptics.constant(level)` (the semantic level, not a raw constant — the unit
  suite runs under SDK 23 where the older fallback would otherwise fire).

## Video player abstraction

The video pipeline uses a `VideoPlayer` interface so the retry/self-heal
controller (`VideoRetryController`) drives whichever sink is active, and future
formats are drop-in additions.

### Interface (`VideoPlayer`)

```
play(url)       — start or restart playback at the given URL
stop()          — stop playback
release()       — tear down resources
isPlaying       — true while frames are being consumed
showFps         — toggle the FPS overlay (MJPEG only; no-op for RTSP)
scaleMode       — FIT (letterbox, default) or CROP (center-fill); user preference
onPlayResult    — callback with true/false after a play() attempt settles
OnStreamError   — fires on a non-recoverable stream failure
OnFirstFrame    — fires once per playback cycle when the first frame renders
```

### Two implementations

- **`MjpegVideoPlayer`** wraps the existing `MjpegView` (HTTP MJPEG stream,
  GPU-backed HWUI render). Uses an executor for async MJPEG stream opening and
  wires the result into the view on the main thread. The `showFps` property
  delegates to `MjpegView.showFps`; `scaleMode` delegates to `MjpegView.scaleMode`
  which reads/writes `MjpegFrameRenderer.scaleMode`.
- **`MediaPlayerVideoPlayer`** uses Android's built-in `MediaPlayer` +
  `TextureView` for RTSP/H.264 streams. Handles `TextureView.SurfaceTextureListener`
  lifecycle (defers playback until the surface is available). `showFps` is a
  no-op (the RTSP pipeline does not support an FPS overlay).

### Factory (`VideoPlayerFactory`)

Routes by the URL scheme:

| URL scheme | Created player |
|------------|---------------|
| `rtsp://` | `MediaPlayerVideoPlayer` |
| `http://` or `https://` | `MjpegVideoPlayer` |
| `null` | `MjpegVideoPlayer` (fallback) |

### Media3 swap strategy

If device-specific `MediaPlayer` RTSP issues surface (buffering timeouts,
reconnect quirks, codec mismatches), swapping to Media3 (ExoPlayer) is a single
new class under the `VideoPlayer` interface — no `MainActivity` or
`VideoRetryController` changes. Media3 is **not** added to the dependencies
until proven necessary.

## Gamepad protocol (source of truth)

The control protocol is the **single truth for every byte sent to and received
from the robot**. This section, not code, is the contract: `docs/architecture.md`
"TCP command protocol" and `MEMORY.md "App protocol"` are pointers to it, and a
code-vs-design mismatch is a bug. `DummyRobotServer` (test source set) is the
**reference implementation** of both directions and is kept in sync with this
section so third-party clients can validate against it.

### Wire format

- One **newline-terminated plain-text command** per unit. Over TCP that is one
  line on the persistent stream (`\n`); over UDP it is **one command per
  datagram** (the same text, verbatim — a robot that parses the TCP stream
  parses UDP the same way).
- Commands are ASCII; app→robot traffic is UTF-8-encoded plain text (ASCII is
  a subset, so the wire stays byte-compatible).
- Every command is a small set of space-separated tokens; there is no framing,
  length prefix, or binary encoding. Parsers should split on any run of
  whitespace and tolerate a trailing separator; clients send exactly the
  canonical form below (no trailing space).

### Protocol versioning

The protocol is **v1** and carries **no version tag on the wire**: a v1 robot
and a v1 client interoperate silently, and the stream is byte-compatible with
the historical commands. Everything new is additive (see the additive rule), so
a v1 robot keeps working with a v1 client and vice versa.

**v2 (deferred, not yet designed)** must stay v1-compatible — a v2 client talks
to a v1 robot and a v1 client to a v2 robot. Candidate v2 features (for when a
robot can answer): message ids / sequence numbers (UDP, possibly TCP), robot
status replies to numbered messages (error codes, `btn` state tracking), and
**data packets / telemetry (v2-only)**. See ROADMAP.

### Command matrix (app → robot)

| Command | When | Why / notes |
|---------|------|-------------|
| `pad 1 x y` / `pad 2 x y` | pad movement beyond the sensitivity threshold | `x`,`y` are `-100..100` integers (y inverted). A pad stroke sends many of these. |
| `pad 1 up` / `pad 2 up` | `ACTION_UP` / `ACTION_CANCEL` | one per touch, marks the pad released. |
| `btn N down` | magic-button press | `N` is 1-based (`1..MAX_MAGIC_BUTTONS`, default 5). **Edge**: sent once per press, never repeated while held, and **never re-sent** by the UDP resend (a re-sent `down` would re-trigger the action). The reference firmware also accepts bare `btn N` (implies `down`) and `btn N up` (explicit release); this app sends `btn N down` only. |
| `wheel <angle>` | tilt change above the step hysteresis | `<angle>` is `-100..100`. Sent only when the angle actually changes. |
| `keepalive <ms>` | every keepalive tick | `<ms>` is the **robot-side expectation**: the robot disconnects the gamepad if no control message arrives within `<ms>` of the previous one (any message counts as proof of life — see "Keepalive semantics" below). The app announces its timeout (`keepalive 5000` by default) and ticks every `keepaliveTimeout − 300` ms (4700) — *earlier* than announced, which is safe. Any app→robot command also restarts the app-side timer. |
| `custom <message>` | never (this app) | Opaque plain-text message to the robot, exposed to user programs (the robot emits a `custom` event). **Specified here but NOT implemented in this app yet** — the Android-side support is to be designed before implementation (deferred, see ROADMAP). The reference implementation already accepts and logs it. |

### Keepalive semantics

The keepalive interval is a **two-way contract, symmetric in both directions**:
a peer that announces `keepalive <ms>` commits to sending *some* control
message at least every `<ms>`, and **any incoming control message re-arms the
peer's single-shot disconnect timer** — any traffic proves the peer is alive.
`keepalive <= 0` disables the expectation (no disconnects). If no message
arrives within `<ms>` of the previous one, the peer disconnects — the original
design, meant to detect an **unreachable gamepad**. Sending keepalive *earlier*
than announced is always safe (the timer is simply restarted), so the app
announces its timeout and ticks 300 ms early to keep that margin. (The real
robot firmware is stricter — it re-arms only on `keepalive` commands; see
"Known violations (review later)".)

### Connection lifecycle

- **TCP (default):** one persistent stream to `192.168.77.1:4444`
  (`SK_HOST_ADDRESS`/`SK_HOST_PORT`). Connect timeout 5 s, `tcpNoDelay`,
  `keepAlive`, `setSoLinger(true,0)`, traffic class `0x0F`. The app writes
  commands and (Campaign 28) keeps the **input half open**, reading optional
  robot messages on a receive thread — a dead connection is still detected
  primarily via write errors (`checkError()` → `disconnect("Send failed.")`).
- **UDP (optional, added in Campaign 27):** the same text protocol, one command
  per datagram, to the same `host:port`. UDP is connectionless, so **"Connected"
  is optimistic**: the pill shows Connected as soon as the first datagram is
  sent (no reply is required or awaited). A send `IOException` (e.g. the
  network is gone) drives the same `disconnect("Send failed.")` path as TCP.
  Over UDP, per keepalive tick the **last pad/wheel state is re-sent** so a
  dropped datagram converges within one keepalive period (buttons are edges and
  are deliberately NOT re-sent — see the matrix).
- **Transport selection** is a global `SK_TRANSPORT` preference (`tcp`/`udp`,
  default `tcp`) in the robot screen's Network category; changing it disconnects
  and the next command reconnects over the new transport. The transport is set
  up through a `CommandTransport` interface so the protocol text is independent
  of the socket.

### Robot → app (received messages)

The app runs an inbound receive loop on **both** transports and accepts
optional robot messages (over TCP the input half stays open since Campaign 28;
over UDP the loop is built in):

- **Any received control message resets the robot-liveness clock** (the robot
  is alive; the app is not required to understand it).
- `keepalive <ms>` announces the robot's **expected heartbeat interval** — the
  robot will send *some* control message at least every `<ms>`. The app stores
  it and, per keepalive tick, disconnects (`"Robot keepalive missed."`) when no
  message arrived within `ms + 2000` (a fixed gap; `ROBOT_KEEPALIVE_GAP_MS`).
- `keepalive <= 0` (or no announcement — the default `-1`) means **no
  expectation**: liveness checks are no-ops (disabled / unlimited).
- **Unknown lines are ignored** (the additive rule below).
- The app keeps sending its **own** `keepalive <ms>` as before — the robot-side
  liveness is *additive* to the app-side keepalive, never a replacement.

### Additive-change rule

Protocol changes are **additive and optional**: a robot that ignores messages
it does not understand (and never replies) works exactly as before. Nothing
new is required of an existing robot: TCP control stays write-only in
practice, UDP works optimistically, and a robot that never sends a
`keepalive` simply disables the robot-side liveness check. A client that
receives an unknown line must ignore it, not error. (Robustness tiers —
keepalive/button ACKs and sequence numbers — are a deferred dream, not part of
this contract; see ROADMAP.)

### Reference implementation

`DummyRobotServer` (app test source set, started via
`./gradlew runDummyRobotServer`) implements this section **in full**: a
log-only TCP port, a log-only UDP port, and a steady MJPEG stream, on the app
default ports 4444/8080. It is the integration target for the app's own smoke
tests and for any third-party client; when this protocol changes, update
`DummyRobotServer` in the same commit. It implements the **contract**, not the
firmware: it enforces the robot-side keepalive disconnect (see "Keepalive
semantics"), accepts `custom <message>`, and tolerates parser niceties (trailing
whitespace, bare `btn N`) only as compatibility notes here — never as behavior
it relies on.

By default the server starts in the **normal situation**: a healthy robot that
only logs. Its only `ERROR` output is the keepalive watchdog's disconnect line,
which is the **conformance signal** for a gamepad client — announce
`keepalive <ms>` and then fall silent, and the connection is dropped exactly
like the real robot. A semantic-validation oracle (ERROR logs for protocol
violations) and fault-injection scenarios (e.g. making the robot stop its own
keepalive) are deferred; see ROADMAP.

### Known violations (review later)

Real implementations deviate from the contract above. Recorded here for
review; **none currently makes this app incompatible**, and none of them should
be "fixed" in this app — robot-side behavior is note-only (never change the app
to match a violation), and desktop-client behavior is reported upstream
(Step 2 issues against `trikset/trik-desktop-gamepad`), not patched here.

**Robot firmware (trikRuntime `gamepad.cpp`):**

- re-arms the keepalive timer **only on `keepalive` commands** — pad/btn/wheel
  traffic does not count as proof of life (stricter than "any message
  re-charges"; safe for this app, which always sends keepalive).
- `keepalive 0` stops the timer, but a **negative** value arms a 0 ms timer
  (deviation from the contract's `keepalive <= 0` = disable; safe because this
  app never sends `<= 0`).
- never replies on the gamepad control channel (no robot→app keepalive from the
  real robot today).
- tolerates bare `btn N` and trailing whitespace (parser splits on any
  whitespace), and auto-clears a `btn N` "pressed" state 500 ms after the last
  press.

**Desktop C++ gamepad (upstream `trikset/trik-desktop-gamepad`):**

- `btn N` has no state token — the client never sends an explicit release
  (`btn N up`), so a button sticks down until the robot's 500 ms auto-clear.
- keyboard auto-repeat re-sends `btn N`, violating the edge rule (one `down`
  per press).
- sends `pad 1 x y ` with a trailing space (its own README documents the
  canonical form without one).
- `wheel` is documented in its README but never implemented.
- announces `keepalive 4000` but sends every 3000 ms (safe — earlier is fine).

Step 2 of the current campaign raises these as issues against the upstream
repo.

### What belongs here

This section is the truth for the *protocol*. The keepalive *timer*
(`keepaliveTimeout - 300` compensation), the `SK_*` keys, and the socket tuning
remain in `docs/architecture.md` / `MEMORY.md`. When in doubt, the protocol
lives here and the implementation detail lives below it.
