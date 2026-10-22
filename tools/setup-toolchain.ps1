<#
.SYNOPSIS
    Installs a portable JDK 21 and Android SDK for building Akagi. No admin rights needed.

.DESCRIPTION
    Downloads about 0.6 GB now; the first Gradle build afterwards fetches about 1-1.5 GB more
    (Gradle itself plus dependencies). Run it on Wi-Fi. Every download is checksum-verified.
    Safe to re-run: steps that are already done are skipped.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\setup-toolchain.ps1
#>
[CmdletBinding()]
param(
    [string]$ToolchainRoot = (Join-Path $env:LOCALAPPDATA 'akagi-toolchain'),
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$Platform = 'platforms/android-37.0',
    # cmdline-tools 23+ forwards sdkmanager to the Android CLI, which uses '/' paths (a ';' would
    # also be split by cmd.exe when passed through sdkmanager.bat).
    [string]$BuildTools = 'build-tools/36.0.0',
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
# Invoke-WebRequest is extremely slow on Windows PowerShell 5.1 while drawing its progress bar.
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-VerifiedDownload {
    param([string]$Url, [string]$Destination, [string]$Algorithm, [string]$ExpectedHash)
    if (-not (Test-Path $Destination)) {
        Write-Host "Downloading $Url"
        # Download to .part and resume it (-C -) so a dropped connection doesn't restart from zero.
        $partial = "$Destination.part"
        $done = $false
        for ($attempt = 1; $attempt -le 20 -and -not $done; $attempt++) {
            & curl.exe --fail --location --continue-at - --retry 10 --retry-all-errors --retry-delay 5 `
                --connect-timeout 30 --speed-limit 1024 --speed-time 60 --output $partial $Url
            $done = $LASTEXITCODE -eq 0
            if (-not $done) { Write-Host "Interrupted (curl exit $LASTEXITCODE), resuming (attempt $attempt/20)..." }
        }
        if (-not $done) { throw "Download failed: $Url (rerun the script to resume)" }
        Move-Item $partial $Destination
    }
    $actual = (Get-FileHash -Path $Destination -Algorithm $Algorithm).Hash
    if ($actual -ne $ExpectedHash) {
        Remove-Item $Destination -Force
        throw "Checksum mismatch for $Destination (expected $ExpectedHash, got $actual)"
    }
}

function Expand-Zip([string]$Zip, [string]$Destination) {
    if (Test-Path $Destination) { Remove-Item $Destination -Recurse -Force }
    New-Item -ItemType Directory -Force $Destination | Out-Null
    # bsdtar ships with Windows 10 and is far faster than Expand-Archive for large zips.
    & tar.exe -xf $Zip -C $Destination
    if ($LASTEXITCODE -ne 0) { throw "Could not extract $Zip" }
}

function Add-UserPathEntry([string]$Entry) {
    $userPath = [string][Environment]::GetEnvironmentVariable('Path', 'User')
    if (($userPath -split ';') -notcontains $Entry) {
        $userPath = (($userPath.TrimEnd(';')) + ';' + $Entry).TrimStart(';')
        [Environment]::SetEnvironmentVariable('Path', $userPath, 'User')
    }
    if (($env:Path -split ';') -notcontains $Entry) { $env:Path = "$Entry;$env:Path" }
}

Write-Host @"
Akagi toolchain setup
  Temurin JDK 21           -> $ToolchainRoot\jdk-21   (~195 MB)
  Android cmdline-tools    -> $SdkRoot\cmdline-tools\latest   (~155 MB)
  platform-tools, $Platform, $BuildTools   (~250 MB)
  First Gradle build afterwards: ~1-1.5 GB more
Total is roughly 1.8-2.3 GB. Make sure you are on Wi-Fi.
"@
if (-not $Yes) {
    $answer = Read-Host 'Continue? (y/N)'
    if ($answer -notmatch '^(y|yes)$') { Write-Host 'Cancelled.'; return }
}

New-Item -ItemType Directory -Force $ToolchainRoot, $SdkRoot | Out-Null

# --- JDK 21 ---------------------------------------------------------------------------------
$jdkHome = Join-Path $ToolchainRoot 'jdk-21'
if (Test-Path (Join-Path $jdkHome 'bin\java.exe')) {
    Write-Step 'JDK 21 already installed'
} else {
    Write-Step 'Installing Temurin JDK 21'
    $api = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = (Invoke-RestMethod -Uri $api | Select-Object -First 1).binary.package
    $zip = Join-Path $ToolchainRoot $package.name
    Get-VerifiedDownload -Url $package.link -Destination $zip -Algorithm SHA256 -ExpectedHash $package.checksum
    $staging = Join-Path $ToolchainRoot 'jdk-staging'
    Expand-Zip -Zip $zip -Destination $staging
    Move-Item -Path (Get-ChildItem $staging -Directory | Select-Object -First 1).FullName -Destination $jdkHome
    Remove-Item $staging, $zip -Recurse -Force
}

# --- Android command-line tools ---------------------------------------------------------------
$cmdlineTools = Join-Path $SdkRoot 'cmdline-tools\latest'
$sdkManager = Join-Path $cmdlineTools 'bin\sdkmanager.bat'
if (Test-Path $sdkManager) {
    Write-Step 'Android command-line tools already installed'
} else {
    Write-Step 'Installing Android command-line tools'
    $repoUrl = 'https://dl.google.com/android/repository/repository2-3.xml'
    [xml]$repo = (Invoke-WebRequest -Uri $repoUrl -UseBasicParsing).Content
    $remote = $repo.DocumentElement.remotePackage |
        Where-Object { $_.path -eq 'cmdline-tools;latest' -and $_.channelRef.ref -eq 'channel-0' } |
        Select-Object -First 1
    $archive = $remote.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1
    $checksum = $archive.complete.checksum
    if ($checksum -isnot [string]) { $checksum = $checksum.'#text' }
    $fileName = $archive.complete.url
    $zip = Join-Path $ToolchainRoot $fileName
    Get-VerifiedDownload -Url "https://dl.google.com/android/repository/$fileName" -Destination $zip -Algorithm SHA1 -ExpectedHash $checksum
    $staging = Join-Path $ToolchainRoot 'cmdline-staging'
    Expand-Zip -Zip $zip -Destination $staging
    New-Item -ItemType Directory -Force (Split-Path $cmdlineTools) | Out-Null
    Move-Item -Path (Join-Path $staging 'cmdline-tools') -Destination $cmdlineTools
    Remove-Item $staging, $zip -Recurse -Force
}

# --- Environment variables (current user) -------------------------------------------------------
Write-Step 'Setting JAVA_HOME, ANDROID_HOME and PATH for your user account'
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkHome, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $SdkRoot
Add-UserPathEntry (Join-Path $jdkHome 'bin')
Add-UserPathEntry (Join-Path $cmdlineTools 'bin')
Add-UserPathEntry (Join-Path $SdkRoot 'platform-tools')

# --- SDK packages ---------------------------------------------------------------------------------
Write-Step 'Accepting Android SDK licenses'
(("y" + [Environment]::NewLine) * 40) | & $sdkManager --licenses | Out-Null

Write-Step "Installing platform-tools, $Platform, $BuildTools"
# sdkmanager can't resume, but it skips packages that finished, so retrying loses at most one package.
for ($attempt = 1; $attempt -le 5; $attempt++) {
    & $sdkManager --install 'platform-tools' $Platform $BuildTools
    if ($LASTEXITCODE -eq 0) { break }
    if ($attempt -eq 5) { throw 'sdkmanager failed (rerun the script to retry)' }
    Write-Host "sdkmanager failed, retrying ($attempt/5)..."
}

# --- Gradle distribution and network settings ---------------------------------------------------------
$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$wrapperProps = Join-Path $PSScriptRoot '..\gradle\wrapper\gradle-wrapper.properties'
if (Test-Path $wrapperProps) {
    $props = ConvertFrom-StringData ((Get-Content $wrapperProps -Raw) -replace '\\:', ':')
    $distUrl = $props.distributionUrl
    $distName = [IO.Path]::GetFileNameWithoutExtension($distUrl)
    # The wrapper keys its cache folder on base-36 MD5 of the URL; placing the zip there lets it skip
    # its own download, which can't resume.
    $md5 = [Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($distUrl))
    $n = [Numerics.BigInteger]::new([byte[]](@([byte[]]$md5[($md5.Length - 1)..0]) + 0))
    $hash = ''
    do { $hash = '0123456789abcdefghijklmnopqrstuvwxyz'[[int]($n % 36)] + $hash; $n = $n / 36 } while ($n -gt 0)
    $distDir = Join-Path $gradleHome "wrapper\dists\$distName\$hash"
    $distZip = Join-Path $distDir "$distName.zip"
    if ((Test-Path $distZip) -or (Test-Path "$distZip.ok")) {
        Write-Step "$distName already downloaded"
    } else {
        Write-Step "Pre-fetching $distName (~140 MB)"
        New-Item -ItemType Directory -Force $distDir | Out-Null
        Get-VerifiedDownload -Url $distUrl -Destination $distZip -Algorithm SHA256 -ExpectedHash $props.distributionSha256Sum
    }
}

Write-Step "Raising Gradle's network retries in $gradleHome\gradle.properties"
$gradleProps = Join-Path $gradleHome 'gradle.properties'
$networkProps = [ordered]@{
    'systemProp.org.gradle.internal.http.connectionTimeout' = '60000'
    'systemProp.org.gradle.internal.http.socketTimeout' = '120000'
    'systemProp.org.gradle.internal.repository.max.retries' = '10'
    'systemProp.org.gradle.internal.repository.initial.backoff' = '2000'
}
$existing = if (Test-Path $gradleProps) { Get-Content $gradleProps } else { @() }
foreach ($key in $networkProps.Keys) {
    if (-not ($existing -match "^$([regex]::Escape($key))=")) {
        Add-Content -Path $gradleProps -Value "$key=$($networkProps[$key])" -Encoding ascii
    }
}

Write-Step 'Done'
Write-Host @"
Open a NEW terminal (so the environment variables apply), then from the Akagi folder run:
    .\gradlew.bat assembleDebug
The first build downloads Gradle and the project's dependencies (~1-1.5 GB).
"@
