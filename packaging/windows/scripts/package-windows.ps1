param(
    [string]$Version = "",
    [string]$Arch = "x64"
)

Set-StrictMode -Version Latest

$root = Resolve-Path .
$dist = Join-Path $root 'dist'
$jpackageInput = Join-Path $dist 'jpackage-input'
$ffmpegDir = Join-Path $root "packaging\windows\ffmpeg\win-$Arch"
$ffmpegDest = Join-Path $ffmpegDir 'ffmpeg.exe'

# 1. Clean extraction of version and artifactId from pom.xml
$pomContent = Get-Content (Join-Path $root 'pom.xml') -Raw

$artifactId = ""
if ($pomContent -match '<artifactId>([^<]+)</artifactId>') {
    $artifactId = $Matches[1].Trim()
}

if ([string]::IsNullOrEmpty($Version)) {
    if ($pomContent -match '<version>([^<]+)</version>') {
        $Version = $Matches[1].Trim()
    } else {
        $Version = "1.0.0"
    }
}

# 2. Dynamic download of FFmpeg if missing
if (-not (Test-Path $ffmpegDest)) {
    Write-Host "FFmpeg not found in $ffmpegDest. Initializing dynamic download..."
    New-Item -ItemType Directory -Path $ffmpegDir -Force | Out-Null

    # URL of the stable essentials build of FFmpeg for Windows
    $ffmpegUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
    $zipPath = Join-Path $ffmpegDir 'ffmpeg.zip'

    Write-Host "Downloading FFmpeg from $ffmpegUrl ..."
    Invoke-WebRequest -Uri $ffmpegUrl -OutFile $zipPath

    Write-Host "Extracting ffmpeg.exe..."
    $tempExtractDir = Join-Path $ffmpegDir 'temp_extract'
    Expand-Archive -Path $zipPath -DestinationPath $tempExtractDir -Force

    # Recursive search for the extracted ffmpeg.exe to move it to the correct location
    $extractedExe = Get-ChildItem -Path $tempExtractDir -Filter 'ffmpeg.exe' -Recurse | Select-Object -First 1
    if ($extractedExe) {
        Move-Item $extractedExe.FullName -Destination $ffmpegDest -Force
        Write-Host "FFmpeg successfully installed in $ffmpegDest"
    } else {
        throw "Could not find ffmpeg.exe in the downloaded archive."
    }

    # Clean up temporary files
    Remove-Item $zipPath -Force
    Remove-Item -Recurse -Force $tempExtractDir -ErrorAction SilentlyContinue
}

# 3. Build production JAR
Write-Host "Building production jar..."
& .\mvnw.cmd -Pproduction -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

# 4. Prepare jpackage-input directory
Write-Host "Preparing jpackage input directory: $jpackageInput"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $jpackageInput
New-Item -ItemType Directory -Path $jpackageInput | Out-Null

# Explicit initialization to satisfy PowerShell StrictMode
$jar = $null

# Robust search for the compiled production JAR file
$jarCandidates = Get-ChildItem -Path target -Filter '*.jar' -Recurse -ErrorAction SilentlyContinue |
                 Where-Object { $_.Name -notmatch 'original' -and $_.Name -notmatch 'wrapper' } |
                 Sort-Object Length -Descending

if ($jarCandidates) {
    # If candidates exist, select the largest one (the fat application JAR)
    $jar = $jarCandidates | Select-Object -First 1
}

# Strict validation to prevent runtime errors down the line
if ($null -eq $jar) {
    Write-Error "Unable to locate any compiled JAR file in target/. Please ensure the Maven build succeeded."
    exit 1
}

# Safely declare the name and format the size layout
[string]$jarName = $jar.Name
$jarSizeMB = [Math]::Round($jar.Length / 1MB, 2)
Write-Host "Found target JAR: $jarName ($jarSizeMB MB)"
Copy-Item $jar.FullName $jpackageInput

# Stage FFmpeg into the application input directory for jpackage ingestion
if (Test-Path $ffmpegDest) {
    $jpackageFfmpegDir = Join-Path $jpackageInput 'ffmpeg'
    New-Item -ItemType Directory -Path $jpackageFfmpegDir -Force | Out-Null
    Copy-Item $ffmpegDest (Join-Path $jpackageFfmpegDir 'ffmpeg.exe') -Force
    Write-Host "Staged ffmpeg to $jpackageFfmpegDir\ffmpeg.exe"
}

# 5. Automatically create/update local .env with absolute Windows paths
$envFilePath = Join-Path $root '.env'
$absoluteFfmpegPath = $ffmpegDest

Write-Host "Updating local .env file with current absolute paths..."
$ffmpegEnvLine = "MEMORIAVAULT_FFMPEG_PATH=$absoluteFfmpegPath"

if (Test-Path $envFilePath) {
    $content = Get-Content $envFilePath
    if ($content -match 'MEMORIAVAULT_FFMPEG_PATH=') {
        $content = $content -replace 'MEMORIAVAULT_FFMPEG_PATH=.*', $ffmpegEnvLine
    } else {
        $content += $ffmpegEnvLine
    }
    $content | Set-Content $envFilePath
} else {
    $defaultContent = @(
        "# Optional local overrides.",
        "# MEMORIAVAULT_* names map to memoriavault.* Spring configuration keys.",
        $ffmpegEnvLine,
        "MEMORIAVAULT_THUMBNAIL_DIRECTORY="
    )
    $defaultContent | Set-Content $envFilePath
}
Write-Host "✓ .env file updated with path: $absoluteFfmpegPath"

# 6. Final instructions for jpackage execution
Write-Host ""
Write-Host "=== READY FOR JPACKAGE ==="
Write-Host "Example command to run:"
Write-Host "jpackage --type exe --dest `"$dist\installers`" --name `"Memoria Vault`" --app-version $Version --vendor `"cnoupoue`" --input `"$jpackageInput`" --main-jar `"$jarName`" --icon `"packaging\windows\icon\MemoriaVault.ico`" --win-shortcut --win-menu --jlink-options `"--strip-debug --no-man-pages --no-header-files --compress zip-6`""
Write-Host ""
Write-Host "If you need to extract and repack embedded native libraries"
Write-Host "powershell -NoProfile -ExecutionPolicy Bypass -File packaging\windows\scripts\sign-sqlite-native-libs.ps1 -AppPath `"$jpackageInput\$jarName`"
