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

Write-Host 'GATE PASSED - all steps green. Log: .tmp/gate.log'
