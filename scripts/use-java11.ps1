param(
    [string]$JavaHome
)

$ErrorActionPreference = "Stop"

function Resolve-JavaHome {
    param([string]$InputHome)

    if ($InputHome -and $InputHome.Trim()) {
        return $InputHome.Trim()
    }
    if ($env:NUTERA_JAVA_HOME -and $env:NUTERA_JAVA_HOME.Trim()) {
        return $env:NUTERA_JAVA_HOME.Trim()
    }
    if ($env:JAVA_HOME -and $env:JAVA_HOME.Trim()) {
        return $env:JAVA_HOME.Trim()
    }

    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-11*",
        "C:\Program Files\Microsoft\jdk-11*",
        "C:\Program Files\Zulu\zulu-11*",
        "C:\Program Files\Java\jdk-11*"
    )
    foreach ($pattern in $candidates) {
        $match = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($match) {
            return $match.FullName
        }
    }
    return $null
}

function Should-DropPathEntry {
    param([string]$Entry)
    if (-not $Entry) { return $true }
    $e = $Entry.ToLower().Replace("/", "\")
    return $e.Contains("oracle\java\javapath") `
        -or $e.Contains("\javapath") `
        -or $e.Contains("jre1.8") `
        -or $e.Contains("jdk1.8") `
        -or $e.Contains("\java\8") `
        -or $e.Contains("\java\jre8")
}

$resolvedHome = Resolve-JavaHome -InputHome $JavaHome
if (-not $resolvedHome) {
    throw "Java 11 home was not found. Provide -JavaHome or set NUTERA_JAVA_HOME/JAVA_HOME."
}

$javaExe = Join-Path $resolvedHome "bin\java.exe"
if (-not (Test-Path $javaExe)) {
    throw "Invalid Java home: $resolvedHome (missing $javaExe)"
}

$env:NUTERA_JAVA_HOME = $resolvedHome
$env:JAVA_HOME = $resolvedHome

$segments = @()
$seen = @{}

$javaBin = Join-Path $resolvedHome "bin"
$segments += $javaBin
$seen[$javaBin.ToLower()] = $true

foreach ($part in ($env:Path -split ";")) {
    $p = $part.Trim()
    if (-not $p) { continue }
    if (Should-DropPathEntry -Entry $p) { continue }
    $key = $p.ToLower()
    if ($seen.ContainsKey($key)) { continue }
    $segments += $p
    $seen[$key] = $true
}

$env:Path = ($segments -join ";")

Write-Host "NUTERA_JAVA_HOME=$env:NUTERA_JAVA_HOME"
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Using Java executable: $javaExe"
Write-Host "java -version:"
& $javaExe -version
