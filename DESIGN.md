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
| **S13** | WAP ↔ cellular transition (user-managed phone setting; robot WAP has no internet) | both control and video sockets route over the Wi-Fi AP whenever one exists |
| **S14** | Robot up, control port blocked/off, video port open | video still recovers (competition edge) |

### Derived principles (P1–P6)

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

### Empty-value semantics

An **empty value is a deliberate "disabled" state**, not a missing one:

- empty robot host → control disabled (video-only, S5);
- empty video URI → video disabled (control-only).

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
URI → the URI, host/port/keepalive → the stored value. Static "hint" summaries
only remain where the row itself is an *action* (e.g. "Reset video URI"), never
a value. The seekbar fallback is the XML default, not a fabricated 0, so a
fresh install shows the real value. An **empty** value is a deliberate
"disabled" state (see "Scenarios & use-cases — Empty-value semantics"), not a
missing one: the video-URI row then shows "No stream URI set", and a blank host
is a valid video-only configuration.

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
(50dp). Non-interactive indicators (spinner, FPS) may be smaller. Enforced by
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

The gamepad chrome ships as **"Type 1"** — a glass/arcade look (translucent dark
fake-glass fills, brand-green glow strokes, borderless bare-circle magic buttons,
one tintable pad-chrome vector). Two principles:

- **XML-first, code-only-where-runtime.** Shapes, gradients, ripples, corners,
  padding, glass fills and styles live in `hud_*` resources and `Hud.*` styles;
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
  each pad is `@dimen/hud_pad_size = 260dp` square). Pad centers land at 25%/75%
  screen width, vertically centered over the video.
- **Chrome:** a single tintable vector (`hud_pad_chrome`) with a solid inner
  ring, full crosshair lines through the center and four edge arrows, plus a
  **dashed outer ring drawn in code** (`SquareTouchPadLayout.onDraw`; vector
  drawables cannot express dash patterns). The joystick **knob** is a radial
  gradient (accent → darkened edge) with a translucent glow halo and a bright
  center dot, drawn in `onDraw`, following the touch point.
- **Glass panel:** `hud_pad_glass` (translucent fill, 2dp brand-green border, soft
  outer glow layer). The pad chrome + glyph are child `ImageView`s tagged
  `padChrome`/`padGlyph` and recolored via one `SRC_IN` filter in
  `SquareTouchPadLayout.setAccent` — same connection-state tone as the gear/pill.
- **Measurement trap (fixed C18):** `SquareTouchPadLayout.onMeasure` must measure
  its children (`super.onMeasure(squareSpec, squareSpec)` after the square
  `setMeasuredDimension`), or the chrome/glyph collapse to 0×0 and the pad renders
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

- The status pill shows the *target* while connecting: `Connecting to host:port:` (and announces it). `Disconnected` shows `Tap to connect:`; a
  blank host (video-only mode) hides the pill entirely.
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
