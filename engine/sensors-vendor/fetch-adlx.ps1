param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'

# Internal build dependency only. License terms are linked in NOTICE.txt.
$revision = 'd9f04a9bba022d6cf6333f005dd540b4ad19fb63'
$base = "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/ADLX/$revision/"
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null
$headers = @('ADLX.h', 'ADLXDefines.h', 'ADLXStructures.h', 'ADLXVersion.h',
             'ICollections.h', 'IPerformanceMonitoring.h', 'ISystem.h')
foreach ($header in $headers) {
    Invoke-WebRequest -Uri ($base + 'SDK/Include/' + $header) -OutFile (Join-Path $destinationPath $header)
}
Invoke-WebRequest -Uri ($base + 'ADLX%20SDK%20License%20Agreement.pdf') `
    -OutFile (Join-Path $destinationPath 'ADLX SDK License Agreement.pdf')
Write-Output "AMD ADLX 1.5 local SDK headers downloaded to $destinationPath"
