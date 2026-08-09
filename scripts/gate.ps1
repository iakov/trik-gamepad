# gate.ps1 - canonical local quality gate for trik-gamepad.
#
# TWO invocations, deliberately (AGENTS.md "Format before you gate"):
#   1. spotlessApply first - rewrites .kt files (ktfmt).
#   2. the full gate - so spotlessCheck never fails on formatting and the
#      formatter never races the test task's Kotlin compilation when
#      org.gradle.parallel=true (one invocation lets spotlessApply rewrite .kt
#      while `test` compiles the same files).
#
# Usage (from repo root):  ./scripts/gate.ps1
# Logs every command to .tmp/gate.log.
#
# Exit code is non-zero if any step fails.

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root '.tmp'
$log = Join-Path $logDir 'gate.log'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
Remove-Item $log -ErrorAction SilentlyContinue

function Invoke-Step([string]$label, [string[]]$args) {
    Write-Host "==> $label"
    & (Join-Path $root 'gradlew.bat') @args --no-daemon *>> $log
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $label (see $log)"
        exit $LASTEXITCODE
    }
}

Invoke-Step 'spotlessApply' @('spotlessApply')
Invoke-Step 'test' @('test')
Invoke-Step 'lint' @('lint')
Invoke-Step 'detekt' @('detekt')
Invoke-Step 'spotbugsDebug' @('spotbugsDebug')
Invoke-Step 'jacocoTestReport' @('jacocoTestReport')
Invoke-Step 'jacocoTestCoverageVerification' @('jacocoTestCoverageVerification')
Invoke-Step 'spotlessCheck' @('spotlessCheck')

# Campaign 6 D1: hard test-duplication gate (jscpd) + logical-SLOC trend (lizard).
# The duplication gate fails on any NEW cloned block >= 50 tokens (import tokens
# excluded via .jscpd.json) -- "re-use what is similar". The token total is a
# trend against the A0 baseline (12,659), not a gate (data-driven test tables
# legitimately carry literal tokens). See DECISIONS.md "[2026-08-09] Test
# logical SLOC metric".
Write-Host '==> jscpd (test duplication gate)'
# npx/node writes to stderr ("Using config from ..."); under PS 5.1 that becomes
# a terminating error with $ErrorActionPreference='Stop' (and *>> would not
# capture it), so scope 'Continue' around the native call.
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& npx jscpd app/src/test app/src/androidTest --config .jscpd.json *>> $log
$jscpdExit = $LASTEXITCODE
$ErrorActionPreference = $oldEap
if ($jscpdExit -ne 0) {
    Write-Host "FAILED: jscpd test duplication gate (see $log)"
    exit $jscpdExit
}

Write-Host '==> lizard (test token trend)'
$lizard = Join-Path $root '.venv\Scripts\lizard.exe'
if (-not (Test-Path $lizard)) {
    $warn = "WARN: lizard not installed (uv pip install --python .venv lizard) - token trend skipped"
    Write-Host $warn
    Add-Content -Path $log -Value $warn
} else {
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $lizardLines = & $lizard -l kotlin app/src/test app/src/androidTest --csv 2>&1
    $lizardExit = $LASTEXITCODE
    $ErrorActionPreference = $oldEap
    if ($lizardExit -ne 0) {
        Write-Host "FAILED: lizard (see $log)"
        exit $lizardExit
    }
    $total = 0
    foreach ($ln in $lizardLines) {
        if (-not [string]::IsNullOrWhiteSpace($ln)) {
            $p = $ln -split ','
            $total += [int]$p[2]
        }
    }
    $trend = "test logical SLOC (summed lizard token_count): $total (A0 baseline 12659)"
    Write-Host $trend
    Add-Content -Path $log -Value $trend
}

Write-Host 'GATE PASSED - all steps green. Log: .tmp/gate.log'
