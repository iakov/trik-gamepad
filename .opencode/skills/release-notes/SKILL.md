______________________________________________________________________

## name: release-notes description: Generate end-user-friendly release notes for a GitHub release of TRIK Gamepad. Use when creating a release or tagging a new version. Produces a simple-English user summary (Part 1) plus a developer changelog (Part 2) with a version table, major issues, and a compare link.

# Release notes generator

Generate release notes for the version being released. Work ONLY on this task.

## Scope guard (hard rule)

- Write ONLY to `release-notes.md` in the repository root.
- Do NOT modify any code, build file, CI workflow, config, or documentation file.
- Do NOT create, edit, or publish a GitHub release. The maintainer reviews and
  publishes it manually.
- Do NOT run git commands that change state (no commits, tags, pushes).

## Steps

1. Determine the version being released and the previous release version. The
   version is `appMajorVersion.appMinorVersion` from `as/build.gradle`
   (e.g. `1.41`), formatted as `v1.41`. The previous release is the last
   version committed in `_apk/` (versioned APK names like `TRIKGamepad-1.40-21.apk`)
   or the last version tag. If neither is given, find it:
   `git tag --list 'v*' --sort=-version:refname`.
1. Collect merged pull requests since the previous release:
   `gh pr list --state merged --base master --limit 100 --json number,title,mergedAt,author`
   Filter to PRs merged after the previous release. Keep newest-first order.
1. Run the PROMPT below with that PR list, then write the result to
   `release-notes.md`.

## PROMPT

You are writing the release notes for "TRIK Gamepad". This is an Android app
that turns a phone or tablet into a gamepad to control TRIK robots: touchpads
drive the robot, buttons trigger actions, a keepalive holds the connection, and
it streams live MJPEG video from the robot's camera. The main audience is school
teachers and TRIK robotics enthusiasts, many of whom are not programmers.

Write release notes in SIMPLE ENGLISH. Use short sentences, common words, and
no jargon. Structure them in two clearly separated parts:

PART 1 — End-user summary (warm, simple, non-technical):

- A short 2-4 sentence paragraph titled "What's new" describing, in plain
  everyday language, what this release brings to a teacher who just wants to
  drive their robot from their phone. Focus on real user value: reliability,
  connection stability, video quality, ease of use.
- Then a short bulleted list "Key improvements" (3-6 bullets) of the most
  user-visible improvements, each in one plain sentence. NO jargon like
  "backend", "refactor", "CI", "lint", "dependency", "lockfile".

PART 2 — Developer reference, in this order:

1. Heading "For developers".
1. A heading "### Version" with a short table of `Version` | `versionCode`
   (computed `minSdk*10000 + major*100 + minor`), `minSdk`, `targetSdk`. One row
   for the released version. If nothing notable, keep this to the released
   version row.
1. A heading "### Major changes" with a bullet list of only significant
   commits: features, fixes, large PRs, and major improvements. In
   Conventional Commits style: `- fix: short description (#123)`. Drop routine
   `chore(deps)`, `docs`, `ci`, and other internal bumps — they belong in the
   compare link, not here.
1. A "Contributors" line listing the human authors of the merged PRs. Remove
   obvious bots (dependabot, github-actions, renovate, etc.). Format as
   `Contributors: [@username](https://github.com/username), ...`. Mark new
   contributors (first PR in this repository) in bold with `(new)`:
   `**@username (new)**`.
1. The last line, exactly:
   `Detailed comparison with previous release vX.YY: <https://github.com/iakov/trik-gamepad/compare/vX.YY...vCURRENT>`
   using the actual previous version and current version.

RULES:

- Part 1 must be genuinely user-facing: talk about what the user can now DO,
  what got faster/more reliable, what new robot or Android versions are
  supported. Do NOT translate developer terms — rephrase them into plain
  words.
- Part 1 must NOT mention: PR numbers, CI, linters, dependency versions,
  lockfiles, copyright headers, Dependabot, app internals.
- Part 1 should mention (when true for the release): no account or internet
  connection needed beyond the local Wi-Fi to the robot, the robot is driven
  from the touchscreen, and the video comes straight from the robot's camera.
- Keep Part 1 concise and friendly. Part 2 is the developer log.
- Separate the two parts with a `---` divider.

INPUT — merged changes for this release (Conventional-Commits-style titles,
newest first), with author login per PR:
<insert the PR list here>

Current version: <current version>
Previous release version: <previous version>
