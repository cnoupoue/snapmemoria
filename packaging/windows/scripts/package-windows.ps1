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
    $candidateRoots = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ChocolateyInstall)) {
        $candidateRoots += (Join-Path $env:ChocolateyInstall 'lib\ffmpeg\tools')
    }

    $candidateRoots += @(
        "C:\ProgramData\chocolatey\lib\ffmpeg\tools",
        "C:\tools"
    )

    foreach ($candidateRoot in $candidateRoots) {
        if ([string]::IsNullOrWhiteSpace($candidateRoot) -or -not (Test-Path -LiteralPath $candidateRoot -PathType Container)) {
            continue
        }

        $matches = Get-ChildItem -LiteralPath $candidateRoot -Filter 'ffmpeg.exe' -Recurse -ErrorAction SilentlyContinue | Sort-Object FullName
        foreach ($match in $matches) {
            if (Test-Path -LiteralPath $match.FullName -PathType Leaf) {
                return [string]$match.FullName
            }
        }
    }

    $command = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
    if ($command -and -not [string]::IsNullOrWhiteSpace($command.Source) -and (Test-Path -LiteralPath $command.Source -PathType Leaf)) {
        return [string]$command.Source
    }

    return $null
}

function Install-FfmpegWithChocolatey {
    if (-not (Get-Command choco.exe -ErrorAction SilentlyContinue)) {
        throw "FFmpeg download failed and Chocolatey is not available for fallback installation."
    }

    Write-Host "Installing FFmpeg via Chocolatey fallback..."
    $chocoOutput = & choco.exe install ffmpeg --no-progress -y 2>&1
    $chocoExitCode = $LASTEXITCODE
    foreach ($line in $chocoOutput) {
        if (-not [string]::IsNullOrWhiteSpace([string]$line)) {
            Write-Host $line
        }
    }

    if ($chocoExitCode -ne 0) {
        throw "Chocolatey failed to install FFmpeg."
    }

    $machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    $pathSegments = @($machinePath, $userPath, $env:Path) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $env:Path = [string]::Join(';', [string[]]$pathSegments)

    $resolvedFfmpeg = Find-InstalledFfmpeg
    if ([string]::IsNullOrWhiteSpace($resolvedFfmpeg)) {
        throw "Chocolatey completed but no FFmpeg executable could be located."
    }

    return [string]$resolvedFfmpeg
}

function Stage-FfmpegFromInstalledLocation {
    param (
        [string]$Destination
    )

    [string]$installedFfmpeg = Find-InstalledFfmpeg
    if ([string]::IsNullOrWhiteSpace($installedFfmpeg)) {
        $installedFfmpeg = Install-FfmpegWithChocolatey
    }

    if ([string]::IsNullOrWhiteSpace($installedFfmpeg)) {
        throw "Could not locate FFmpeg after Chocolatey fallback installation."
    }

    if (-not (Test-Path -LiteralPath $installedFfmpeg -PathType Leaf)) {
        throw "Resolved FFmpeg path does not exist: $installedFfmpeg"
    }

    Copy-Item -LiteralPath $installedFfmpeg -Destination $Destination -Force
    Write-Host "FFmpeg staged from installed location: $installedFfmpeg"
}

function New-WindowsIconFromPng {
    param (
        [string]$SourcePng,
        [string]$DestinationIco
    )

    if (-not (Test-Path -LiteralPath $SourcePng -PathType Leaf)) {
        throw "Missing application icon source: $SourcePng"
    }

    Add-Type -AssemblyName System.Drawing

    $temporaryPng = [System.IO.Path]::ChangeExtension([System.IO.Path]::GetTempFileName(), ".png")
    $sourceImage = $null
    $resizedImage = $null
    $graphics = $null

    try {
        $sourceImage = [System.Drawing.Image]::FromFile($SourcePng)
        $resizedImage = New-Object System.Drawing.Bitmap 256, 256, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($resizedImage)
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($sourceImage, 0, 0, 256, 256)
        $graphics.Dispose()
        $graphics = $null

        $resizedImage.Save($temporaryPng, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngBytes = [System.IO.File]::ReadAllBytes($temporaryPng)

        $destinationDirectory = Split-Path -Parent $DestinationIco
        New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null

        $iconBytes = New-Object byte[] (22 + $pngBytes.Length)
        $iconBytes[2] = 1
        $iconBytes[4] = 1
        $iconBytes[10] = 1
        $iconBytes[12] = 32
        [System.BitConverter]::GetBytes([UInt32]$pngBytes.Length).CopyTo($iconBytes, 14)
        [System.BitConverter]::GetBytes([UInt32]22).CopyTo($iconBytes, 18)
        [Array]::Copy($pngBytes, 0, $iconBytes, 22, $pngBytes.Length)
        [System.IO.File]::WriteAllBytes($DestinationIco, $iconBytes)
    } finally {
        if ($graphics) {
            $graphics.Dispose()
        }
        if ($resizedImage) {
            $resizedImage.Dispose()
        }
        if ($sourceImage) {
            $sourceImage.Dispose()
        }
        Remove-Item -LiteralPath $temporaryPng -Force -ErrorAction SilentlyContinue
    }

    Write-Host "Generated Windows application icon: $DestinationIco"
}

# Validate supported architecture early
if ($Arch -ne "x64") {
    Write-Error "Unsupported architecture: $Arch. Only 'x64' builds are currently supported for Windows packaging."
    exit 1
}

$root = Resolve-Path .
$dist = Join-Path $root 'dist'
$jpackageInput = Join-Path $dist 'jpackage-input'
$generatedIconDir = Join-Path $dist 'generated-icons'
$appIconSource = Join-Path $root 'src\main\resources\icon.png'
$windowsIcon = Join-Path $generatedIconDir 'MemoriaVault.ico'
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

# 1. Windows FFmpeg staging
if (-not (Test-Path -LiteralPath $ffmpegDest -PathType Leaf)) {
    Write-Host "FFmpeg binary missing in $ffmpegDest. Resolving Windows FFmpeg through Chocolatey..."
    New-Item -ItemType Directory -Path $ffmpegDir -Force | Out-Null
    Stage-FfmpegFromInstalledLocation -Destination $ffmpegDest
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

# 5. Generate platform-specific icon from the single application icon source
New-WindowsIconFromPng -SourcePng $appIconSource -DestinationIco $windowsIcon

# 6. Local Development Environment Instantiation Only
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

# 7. Structured Payload Summary for jpackage
Write-Host ""
Write-Host "=== READY FOR JPACKAGE EXECUTION ==="
Write-Host "Staged jpackage input directory:"
Write-Host $jpackageInput
Write-Host "Staged main JAR:"
Write-Host $jarName
Write-Host "Generated Windows icon:"
Write-Host $windowsIcon
Write-Host 'The release workflow now executes jpackage directly after this staging script.'
Write-Host 'Required Windows runtime options:'
Write-Host '-Djava.awt.headless=false'
Write-Host '-Dmemoriavault.desktop=true'
Write-Host '-Dmemoriavault.browser.auto-open=false'
Write-Host '-Dmemoriavault.ffmpeg.path=$APPDIR\ffmpeg\ffmpeg.exe'
Write-Host ""
