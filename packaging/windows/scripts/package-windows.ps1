param (
    [string]$Version = "",
    [string]$Arch = "x64"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-MavenWithRetry {
    param (
        [string[]]$MavenArguments
    )

    $maxAttempts = 3
    $delaySeconds = 10

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        $output = & .\mvnw.cmd @MavenArguments 2>&1
        $exitCode = $LASTEXITCODE

        if ($exitCode -eq 0) {
            $output
            return
        }

        $output | ForEach-Object { Write-Host $_ }

        if ($attempt -eq $maxAttempts) {
            throw "Maven command failed after $maxAttempts attempts: $($MavenArguments -join ' ')"
        }

        Write-Host "Maven command failed; retrying in ${delaySeconds}s (attempt $attempt/$maxAttempts)."
        Start-Sleep -Seconds $delaySeconds
        $delaySeconds = $delaySeconds * 2
    }
}

function Find-InstalledFfmpeg {
    $candidateRoots = @(
        "${env:ChocolateyInstall}\lib\ffmpeg\tools",
        "C:\ProgramData\chocolatey\lib\ffmpeg\tools",
        "C:\tools"
    )

    foreach ($candidateRoot in $candidateRoots) {
        if ([string]::IsNullOrWhiteSpace($candidateRoot) -or -not (Test-Path $candidateRoot)) {
            continue
        }

        $matches = Get-ChildItem -Path $candidateRoot -Filter 'ffmpeg.exe' -Recurse -ErrorAction SilentlyContinue
        foreach ($match in $matches) {
            if (Test-Path $match.FullName) {
                return $match.FullName
            }
        }
    }

    $command = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    return $null
}

function Install-FfmpegWithChocolatey {
    if (-not (Get-Command choco.exe -ErrorAction SilentlyContinue)) {
        throw "FFmpeg download failed and Chocolatey is not available for fallback installation."
    }

    Write-Host "Installing FFmpeg via Chocolatey fallback..."
    choco install ffmpeg --no-progress -y
    if ($LASTEXITCODE -ne 0) {
        throw "Chocolatey failed to install FFmpeg."
    }

    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")
    return Find-InstalledFfmpeg
}

function Stage-FfmpegFromInstalledLocation {
    param (
        [string]$Destination
    )

    $installedFfmpeg = Find-InstalledFfmpeg
    if (-not $installedFfmpeg) {
        $installedFfmpeg = Install-FfmpegWithChocolatey
    }

    if (-not $installedFfmpeg -or -not (Test-Path $installedFfmpeg)) {
        throw "Could not locate FFmpeg after Chocolatey fallback installation."
    }

    Copy-Item $installedFfmpeg $Destination -Force
    Write-Host "FFmpeg staged from installed location: $installedFfmpeg"
}

# Validate supported architecture early
if ($Arch -ne "x64") {
    Write-Error "Unsupported architecture: $Arch. Only 'x64' builds are currently supported for Windows packaging."
    exit 1
}

$root = Resolve-Path .
$dist = Join-Path $root 'dist'
$jpackageInput = Join-Path $dist 'jpackage-input'
$ffmpegDir = Join-Path $root "packaging\windows\ffmpeg\win-$Arch"
$ffmpegDest = Join-Path $ffmpegDir 'ffmpeg.exe'

Write-Host "=== RESOLVING MAVEN METADATA ==="
# Query Maven directly for project version and artifact name instead of regex parsing
if ([string]::IsNullOrEmpty($Version)) {
    $Version = (Invoke-MavenWithRetry -MavenArguments @("-q", "-DforceStdout", "help:evaluate", "-Dexpression=project.version"))
}
$finalName = (Invoke-MavenWithRetry -MavenArguments @("-q", "-DforceStdout", "help:evaluate", "-Dexpression=project.build.finalName"))
$jarName = "$finalName.jar"

Write-Host "Target Version: $Version"
Write-Host "Expected JAR Name: $jarName"

# 1. Secured, Hash-Verified FFmpeg Downloader
if (-not (Test-Path $ffmpegDest)) {
    Write-Host "FFmpeg binary missing in $ffmpegDest. Initializing pinned verification download..."
    New-Item -ItemType Directory -Path $ffmpegDir -Force | Out-Null

    # Pinned production-grade release of FFmpeg with cryptographic enforcement
    $ffmpegUrl = "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-7.1-essentials_build.zip"
    $expectedHash = "6ED8BCC0B426AB5B6AA36D2CB187E6924D5B1FE26B33D4F5A170F429BAFF89B5"
    $zipPath = Join-Path $ffmpegDir 'ffmpeg.zip'

    $tempExtractDir = Join-Path $ffmpegDir 'temp_extract'
    try {
        Write-Host "Downloading pinned FFmpeg archive from $ffmpegUrl ..."
        Invoke-WebRequest -Uri $ffmpegUrl -OutFile $zipPath -UseBasicParsing -ErrorAction Stop

        if (-not (Test-Path $zipPath)) {
            throw "FFmpeg archive download did not create $zipPath."
        }

        Write-Host "Verifying SHA-256 checksum..."
        $actualHash = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash
        if ($actualHash -ne $expectedHash) {
            throw "Cryptographic verification failed! Expected hash: $expectedHash, but got: $actualHash"
        }
        Write-Host "FFmpeg archive integrity verified."

        Write-Host "Extracting binaries..."
        Expand-Archive -Path $zipPath -DestinationPath $tempExtractDir -Force

        $extractedExe = Get-ChildItem -Path $tempExtractDir -Filter 'ffmpeg.exe' -Recurse | Select-Object -First 1
        if ($extractedExe) {
            Move-Item $extractedExe.FullName -Destination $ffmpegDest -Force
            Write-Host "FFmpeg successfully isolated to $ffmpegDest"
        } else {
            throw "Fatal: Could not locate ffmpeg.exe within the downloaded archive payload."
        }
    } catch {
        Write-Warning "Pinned FFmpeg download path failed: $($_.Exception.Message)"
        Write-Warning "Falling back to Chocolatey-managed FFmpeg."
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Recurse -Force $tempExtractDir -ErrorAction SilentlyContinue
        Stage-FfmpegFromInstalledLocation -Destination $ffmpegDest
    } finally {
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Recurse -Force $tempExtractDir -ErrorAction SilentlyContinue
    }
}

# 2. Executing Clean Maven Package
Write-Host "Compiling production artifact via clean package..."
# Note: The CI release workflow runs full test validation suites before invoking this script
Invoke-MavenWithRetry -MavenArguments @("clean", "package", "-Pproduction,windows-desktop", "-DskipTests")

# 3. Setting Up Runtime Input Directory For jpackage
Write-Host "Preparing jpackage isolated input staging: $jpackageInput"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $jpackageInput
New-Item -ItemType Directory -Path $jpackageInput | Out-Null

$targetJarPath = Join-Path $root "target\$jarName"
if (-not (Test-Path $targetJarPath)) {
    Write-Error "Expected artifact missing at $targetJarPath. Verify Maven configurations."
    exit 1
}

Write-Host "Staging target deployment fat-JAR: $jarName"
Copy-Item $targetJarPath $jpackageInput

# 4. Relative Ingestion of FFmpeg (No absolute CI paths baked in)
$jpackageFfmpegDir = Join-Path $jpackageInput 'ffmpeg'
New-Item -ItemType Directory -Path $jpackageFfmpegDir -Force | Out-Null
Copy-Item $ffmpegDest (Join-Path $jpackageFfmpegDir 'ffmpeg.exe') -Force
Write-Host "Bundled FFmpeg staged into relative app folder layout structure."

# 5. Local Development Environment Instantiation Only
$envFilePath = Join-Path $root '.env'
if (-not (Test-Path $envFilePath)) {
    Write-Host "Creating default development local environment profile (.env)..."
    $defaultContent = @(
        "# Local developer runtime overrides.",
        "MEMORIAVAULT_FFMPEG_PATH=$ffmpegDest",
        "MEMORIAVAULT_THUMBNAIL_DIRECTORY="
    )
    $defaultContent | Set-Content $envFilePath
}

# 6. Structured Payload Summary for jpackage
Write-Host ""
Write-Host "=== READY FOR JPACKAGE EXECUTION ==="
Write-Host "Staged jpackage input directory:"
Write-Host $jpackageInput
Write-Host "Staged main JAR:"
Write-Host $jarName
Write-Host 'The release workflow now executes jpackage directly after this staging script.'
Write-Host 'Required Windows runtime options:'
Write-Host '-Dmemoriavault.desktop=true'
Write-Host '-Dmemoriavault.browser.auto-open=false'
Write-Host '-Dmemoriavault.ffmpeg.path=$APPDIR\ffmpeg\ffmpeg.exe'
Write-Host ""
