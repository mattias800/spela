<#
.SYNOPSIS
  Launch the Spela desktop player on Windows with the full native-build environment.

.DESCRIPTION
  Building/running the desktop app needs: the MSVC x64 dev environment (for the
  native libretro bridge), JDK, Vulkan SDK, the Android SDK (the Gradle build
  configures the :android module unconditionally), SDL2 (desktop gamepad), and
  Git Bash on PATH (the native shader step shells out to bash). It also needs the
  built native dir on PATH so the Windows loader can resolve SDL2.dll, a
  dependency of spela-libretro.dll.

  This script wires all of that up and then runs `gradlew :desktop:run`.
  Any extra arguments are forwarded to Gradle (e.g. --info, --game <id>).

.NOTES
  SDL2: set $env:SDL2_DIR to the SDL2 'cmake' dir to override auto-detection.
  Auto-detection looks for %USERPROFILE%\SDL2\SDL2-*\cmake.
#>
param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $GradleArgs)

$ErrorActionPreference = 'Stop'
$playerDir = $PSScriptRoot

# --- MSVC x64 developer environment (Build Tools needs -products *) ---
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) { throw "vswhere not found. Install Visual Studio Build Tools (C++ workload)." }
$vsPath = & $vswhere -latest -products * -property installationPath
if (-not $vsPath) { throw "No Visual Studio / Build Tools installation found." }
Import-Module (Join-Path $vsPath 'Common7\Tools\Microsoft.VisualStudio.DevShell.dll')
Enter-VsDevShell -VsInstallPath $vsPath -SkipAutomaticLocation -DevCmdArguments '-arch=x64 -host_arch=x64' | Out-Null

# --- JDK / Vulkan / Android from machine/user env ---
$env:JAVA_HOME  = [System.Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:VULKAN_SDK = [System.Environment]::GetEnvironmentVariable('VULKAN_SDK', 'Machine')
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = [System.Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
}

# --- SDL2 (override with $env:SDL2_DIR; else auto-detect under %USERPROFILE%\SDL2) ---
if (-not $env:SDL2_DIR) {
    $sdl2Root = Get-ChildItem "$env:USERPROFILE\SDL2\SDL2-*" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($sdl2Root) { $env:SDL2_DIR = Join-Path $sdl2Root.FullName 'cmake' }
}
if ($env:SDL2_DIR) {
    $env:CMAKE_PREFIX_PATH = Split-Path $env:SDL2_DIR -Parent
} else {
    Write-Warning "SDL2 not found; gamepad support will be disabled. Set `$env:SDL2_DIR to enable it."
}

# --- PATH: native build dir (for SDL2.dll at runtime) + Git Bash (for shader step) ---
$nativeDir = Join-Path $playerDir 'desktop\build\native'
$gitBin = 'C:\Program Files\Git\bin'
$env:Path = "$nativeDir;$gitBin;$env:Path"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "VULKAN_SDK=$env:VULKAN_SDK"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "SDL2_DIR=$env:SDL2_DIR"
Write-Host "--- launching :desktop:run ---"

Set-Location $playerDir
& .\gradlew.bat :desktop:run --console=plain @GradleArgs
