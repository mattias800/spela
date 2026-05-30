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

  Title bar: the transparent title bar on Windows/Linux requires the JetBrains
  Runtime (JBR). This script runs on JBR if found (override with $env:SPELA_JBR,
  else auto-detected under %USERPROFILE%\jbr\jbr-*); otherwise it falls back to
  the machine JDK and the title bar is opaque (dev only — release builds package
  JBR). Download a JBR build from https://cache-redirector.jetbrains.com/intellij-jbr/
  and unpack it under %USERPROFILE%\jbr\.
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

# --- JDK: prefer the JetBrains Runtime so the window gets a transparent title
#     bar (same as release builds; see applyJbrTransparentTitleBar in Main.kt).
#     Override with $env:SPELA_JBR; else auto-detect %USERPROFILE%\jbr\jbr-*.
#     Falls back to the machine JDK (opaque title bar) if no JBR is present. ---
$jbr = $env:SPELA_JBR
if (-not $jbr) {
    $jbrDir = Get-ChildItem "$env:USERPROFILE\jbr\jbr-*" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jbrDir) { $jbr = $jbrDir.FullName }
}
if ($jbr -and (Test-Path (Join-Path $jbr 'bin\java.exe'))) {
    $env:JAVA_HOME = $jbr
} else {
    $env:JAVA_HOME = [System.Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    Write-Warning "JetBrains Runtime not found; using $env:JAVA_HOME (title bar will be opaque). Set `$env:SPELA_JBR or unpack a JBR under %USERPROFILE%\jbr\."
}
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
