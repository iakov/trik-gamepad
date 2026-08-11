# TRIK Gamepad (Android)

[![CI](https://github.com/iakov/trik-gamepad/actions/workflows/ci.yml/badge.svg)](https://github.com/iakov/trik-gamepad/actions/workflows/ci.yml)

Simple Android application that mimics a gamepad and is used to control TRIK
robots.

You can get it [here](https://play.google.com/store/apps/details?id=com.trikset.gamepad)

## For users

Available in English, Russian, French, German and Vietnamese (follows the
system language).

On large-screen Android 16 devices (tablets, foldables, and desktop windows —
screens with the smaller side at least 600 dp), Android ignores the app's
landscape lock, so the gamepad can appear rotated or stretched. To keep the
intended layout, opt in to the app's default orientation behavior in the
system's aspect-ratio settings, or lock your device's rotation to landscape.

## For developers

New session or contributor? Start here:

- `AGENTS.md` — rules, guardrails, and commands for AI agents and contributors.
- `DESIGN.md` — UX/accessibility conventions (named sections; the AGENTS memory
  index points into them).
- `MEMORY.md` — project facts, architecture details, CI quirks, and
  retrospectives (pull on demand; `AGENTS.md` points to its sections).
- `DECISIONS.md` — the decision log (problem → alternatives → why → out-of-scope
  for every decision in the repo).
- `TESTING.md` — test strategy and how to run the test suites.
- `docs/architecture.md` — module map, TCP command protocol, MJPEG video
  pipeline, and test layering, plus the Android/Kotlin/CI best-practice
  reference.
- `docs/ROADMAP.md` — the committed improvement plan (next phases and
  sequencing).
- The maintained app lives in the canonical `app/` module at the repo root
  (`settings.gradle` + `app/`); all gradle commands run from the repo root.
