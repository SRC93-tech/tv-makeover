<#
.SYNOPSIS
    Install the pre-built personalized DreamOS launcher APK onto an Android TV over Wi-Fi ADB.
.EXAMPLE
    .\install-apk.ps1 -TvHost 192.168.29.30
.EXAMPLE
    .\install-apk.ps1 -TvHost 192.168.29.247 -Build
#>
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$ApkPath = '',
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$Root = $PSScriptRoot

if (-not $TvHost) {
    $TvHost = Read-Host 'Enter your TV IP address on the same Wi-Fi (e.g. 192.168.29.30)'
}
if (-not $TvHost) { throw 'TV host IP is required.' }
$env:TV_HOST = $TvHost.Trim()

$LauncherDir = Join-Path $Root 'build\dreamos\launcher'
if ($Build) {
    if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName 'jbr' } |
            Where-Object { Test-Path "$_\bin\java.exe" } |
            Select-Object -First 1
        if (-not $jbr) { throw 'Set JAVA_HOME to a JDK 17+ (or install Android Studio).' }
        $env:JAVA_HOME = $jbr
    }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
    $localProps = Join-Path $LauncherDir 'local.properties'
    if (-not (Test-Path $localProps)) {
        "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii $localProps
    }
    Push-Location $LauncherDir
    try {
        .\gradlew.bat assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Gradle assembleRelease failed.' }
    } finally {
        Pop-Location
    }
}

if (-not $ApkPath) {
    $builtApk = Join-Path $LauncherDir 'app\build\outputs\apk\release\app-release.apk'
    $stagedApk = Join-Path $Root 'releases\RaghavTV-launcher-v2.4.apk'
    $ApkPath = if (Test-Path $builtApk) { $builtApk } elseif (Test-Path $stagedApk) { $stagedApk } else { '' }
}
if (-not $ApkPath -or -not (Test-Path $ApkPath)) {
    throw 'APK not found. Pass -Build to compile or -ApkPath <path-to-apk>.'
}

$ToolsDir = Join-Path $Root 'build\dreamos\tools'
$PyCandidates = @(
    (Join-Path $ToolsDir 'venv\Scripts\python.exe'),
    (Join-Path $Root 'cleanyourtv\tools\venv\Scripts\python.exe')
)
$Py = $PyCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $Py) {
    Write-Host 'Creating Python ADB helper venv...'
    python -m venv (Join-Path $ToolsDir 'venv')
    $Py = Join-Path $ToolsDir 'venv\Scripts\python.exe'
    & $Py -m pip install -q -r (Join-Path $ToolsDir 'requirements.txt')
}

$B64Push = Join-Path $ToolsDir 'b64push.py'
$TvPy = Join-Path $ToolsDir 'tv.py'

Write-Host "Probing $env:TV_HOST..."
& $Py $TvPy --probe
if ($LASTEXITCODE -ne 0) { throw "Could not reach TV at $env:TV_HOST. Ensure USB debugging is enabled and you are on the same Wi-Fi." }

Write-Host "Pushing $ApkPath to TV..."
& $Py $B64Push $ApkPath /data/local/tmp/tvhome.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed.' }

Write-Host 'Installing, compiling, setting Home, and launching...'
& $Py $TvPy "pm install -r /data/local/tmp/tvhome.apk && rm -f /data/local/tmp/tvhome.apk && cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity && cmd package compile -m speed -f com.gathin.tvhome && am start -W -S -n com.gathin.tvhome/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'APK install failed on TV.' }

Write-Host 'Done! The updated launcher is now live on your TV.'

