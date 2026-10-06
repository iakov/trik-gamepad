______________________________________________________________________

## name: release-notes description: Generate end-user-friendly release notes for a GitHub release of TRIK Gamepad. Use when creating a release or tagging a new version. Produces a release tag text file (release-<tag>-tag-text.md): line 1 = release title, rest = release body (markdown). The signed tag embeds this text; CI auto-splits it back into title + body and publishes the release with the signed APK attached. Simple-English user summary (Part 1) plus developer changelog (Part 2).

# Release notes generator

Generate the release tag text for the version being released. Work ONLY on this task.

## Scope guard (hard rule)

- Write ONLY to `release-<tag>-tag-text.md` in the repository root (e.g.
  `release-v2.44-tag-text.md`).
- Do NOT modify any code, build file, CI workflow, config, or documentation file.
- Do NOT create, edit, or publish a GitHub release, and do NOT push the tag —
  the maintainer reviews the tag text, creates the signed tag
  (`git tag -s -F release-v2.44-tag-text.md v2.44`), and pushes it. CI then
  auto-publishes from the tag.
- Do NOT run git commands that change state (no commits, tags, pushes).

## Steps

1. Determine the version being released and the previous release version. Use
   `version.properties` at the repo root (VERSION_MAJOR/VERSION_MINOR), or
   `scripts/version_manager.py` for the canonical values. The version is
   `VERSION_MAJOR.VERSION_MINOR` (e.g. `2.44`), formatted as `v2.44`. The
   previous release is the last tag from
   `git tag --list 'v*' --sort=-version:refname`.
1. Collect merged pull requests since the previous release:
   `gh pr list --state merged --base master --limit 100 --json number,title,mergedAt,author`
   Filter to PRs merged after the previous release. Keep newest-first order.
1. Run the PROMPT below with that PR list.
1. Write the result to `release-v<current>-tag-text.md` (e.g. `release-v2.44-tag-text.md`).
   Line 1 of the file is the release TITLE (one line, no trailing period);
   everything from line 2 on is the release BODY (full markdown
   Part 1 + Part 2, with the `---` divider). This exact file becomes the
   signed tag message; CI splits it back identically.

## PROMPT

You are writing the release notes for "TRIK Gamepad". This is an Android app
that turns a phone or tablet into a gamepad to control TRIK robots: touchpads
drive the robot, buttons trigger actions, a keepalive holds the connection, and
it streams live MJPEG video from the robot's camera. The main audience is school
teachers and TRIK robotics enthusiasts, many of whom are not programmers.

Write release notes in SIMPLE ENGLISH. Use short sentences, common words, and
no jargon. Output a text file whose structure matches the tag discipline:

LINE 1 — Release title: one short line naming the release, e.g.
`TRIK Gamepad 2.44 — reliable connection and faster video`. No trailing
period, no markdown heading syntax. CI uses this exact line as the GitHub
release title.

LINES 2+ — Release body, two clearly separated parts:

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
   (computed `minSdk*10000 + major*100 + minor`), `minSdk` (include full Android
   version name and API level, e.g. `Android 5.0 Lollipop (API 21)`),
   `targetSdk`. One row for the released version. If nothing notable, keep this
   to the released version row.
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
- The release BODY (lines 2+) starts with a blank line after the title line,
  then Part 1's "What's new" — do NOT repeat the release title inside the body.

INPUT — merged changes for this release (Conventional-Commits-style titles,
newest first), with author login per PR:
<insert the PR list here>

Current version: <current version>
Previous release version: <previous version>
