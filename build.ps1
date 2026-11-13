<#
  Vroomi build helper (Windows).

  Why a copy: the Android Gradle Plugin and aapt2 fail on project paths that contain non-ASCII
  characters or spaces (this repo lives under OneDrive\바탕 화면\...). The script mirrors the source
  to an ASCII folder, builds there, and copies the APK back to .\apk\ (only an APK this run built: an
  older one left in the build folder never replaces the one in the repository).

  Usage (from this folder):
    powershell -ExecutionPolicy Bypass -File .\build.ps1                 # unit tests + release APK
    powershell -ExecutionPolicy Bypass -File .\build.ps1 -Task "lint"    # any Gradle task(s)

  Toolchain (JDK 17, Android SDK, Gradle cache) is expected in C:\vroomi-tools (override with -Tools).
#>
param(
    [string]$Task = "testDebugUnitTest assembleRelease",
    [string]$Tools = "C:\vroomi-tools",
    [string]$Work = "C:\vroomi-build\Vroomi"
)
$ErrorActionPreference = "Stop"
$src = $PSScriptRoot

$env:JAVA_HOME = Join-Path $Tools "jdk17"
$env:ANDROID_HOME = Join-Path $Tools "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $Tools "gradle-home"
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path

New-Item -ItemType Directory -Force $Work | Out-Null
# Mirror sources (skip build outputs and IDE folders). /MIR keeps the copy identical.
robocopy $src $Work /MIR /XD build .gradle .idea .kotlin apk .git .superpowers .claude /XF local.properties /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed ($LASTEXITCODE)" }
Set-Content -Path (Join-Path $Work "local.properties") -Value ("sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '\\')) -Encoding ascii
New-Item -ItemType Directory -Force (Join-Path $Work "apk") | Out-Null

$started = Get-Date
Push-Location $Work
try {
    & .\gradlew.bat --no-daemon --console=plain $Task.Split(" ")
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }
} finally { Pop-Location }

New-Item -ItemType Directory -Force (Join-Path $src "apk") | Out-Null
# Copy back only what this run wrote. The build folder keeps the APKs of earlier runs (another branch, an older
# team name); copying those would silently put an old build over the release APK that is committed here.
Get-ChildItem (Join-Path $Work "apk") -Filter "*_WithDriving_release.apk" -ErrorAction SilentlyContinue |
    ForEach-Object {
        if ($_.LastWriteTime -ge $started) { Copy-Item $_.FullName (Join-Path $src "apk") -Force; Write-Host "APK -> apk\$($_.Name)" }
        else { Write-Host "APK kept, not rebuilt in this run: apk\$($_.Name)" }
    }
