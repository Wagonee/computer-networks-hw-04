param(
    [string] $OutDir = (Join-Path $PSScriptRoot "..\client-data"),
    [int]    $Count  = 10,
    [int]    $SizeMB = 100
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -Path $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir | Out-Null
}

$rng       = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$chunkSize = 4 * 1024 * 1024
$chunk     = New-Object byte[] $chunkSize

for ($i = 1; $i -le $Count; $i++) {
    $name = "test-{0:D2}.bin" -f $i
    $path = Join-Path $OutDir $name
    Write-Host ("creating {0} ({1} MB)" -f $path, $SizeMB)
    $totalBytes = [int64] $SizeMB * 1024 * 1024
    $written    = [int64] 0
    $fs         = [System.IO.File]::Create($path)
    try {
        while ($written -lt $totalBytes) {
            $rng.GetBytes($chunk)
            $toWrite = [int]([Math]::Min($chunkSize, $totalBytes - $written))
            $fs.Write($chunk, 0, $toWrite)
            $written += $toWrite
        }
    } finally {
        $fs.Dispose()
    }
}

$rng.Dispose()
Write-Host ("done: {0} files in {1}" -f $Count, (Resolve-Path $OutDir))
