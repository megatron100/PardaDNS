<# PandaDNS — Build, Install & Launch #>
param(
    [switch]$Release,
    [switch]$Clean,
    [switch]$LaunchOnly
)

$ErrorActionPreference = "Stop"

# Environment — use JAVA_HOME if valid, else Android Studio's JBR, else a JDK 21 under ~/.jdks
if (-not ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe"))) {
    $candidates = @("C:\Program Files\Android\Android Studio\jbr") +
        @(Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object FullName)
    $env:JAVA_HOME = $candidates | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if (-not $env:JAVA_HOME) { Write-Host "No JDK found. Install JDK 21 (e.g. Temurin) to ~/.jdks" -ForegroundColor Red; exit 1 }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:PATH"

$pkg = "com.panda.dns"
$activity = "com.example.pardadns.MainActivity"
$variant = if ($Release) { "Release" } else { "Debug" }
$apkDir = "app\build\outputs\apk\$($variant.ToLower())"
$apkName = "app-$($variant.ToLower()).apk"

if (-not $LaunchOnly) {
    Write-Host "`n=== Building $variant ===" -ForegroundColor Cyan

    $tasks = @()
    if ($Clean) { $tasks += "clean" }
    $tasks += "assemble$variant"

    & .\gradlew @tasks
    if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }

    Write-Host "`n=== Installing ===" -ForegroundColor Cyan
    $devices = adb devices | Select-String "^\S+\s+device$" | ForEach-Object { ($_ -split "\s+")[0] }
    if (-not $devices) { Write-Host "No devices connected" -ForegroundColor Red; exit 1 }

    foreach ($d in $devices) {
        Write-Host "  -> $d"
        adb -s $d install -r "$apkDir\$apkName"
    }
}

Write-Host "`n=== Launching ===" -ForegroundColor Cyan
adb shell am force-stop $pkg
adb shell am start -n "$pkg/$activity"

Write-Host "`n=== Logcat (Ctrl+C to stop) ===" -ForegroundColor Cyan
adb logcat -c
adb logcat -s "PandaDNS/Service" "PandaDNS/Activity" "AndroidRuntime:E" "FirebaseCrashlytics:D"
