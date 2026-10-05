param(
    [Parameter(Mandatory=$true)][string]$Label,
    [Parameter(Mandatory=$true)][string[]]$ProcessNames,
    [int]$DurationSeconds=120,
    [int]$SampleSeconds=2,
    [int]$LogicalProcessors=16
)
$ErrorActionPreference='Stop'
function Snapshot {
    $found = @(Get-Process -Name $ProcessNames -ErrorAction SilentlyContinue)
    if ($found.Count -eq 0) { throw 'No target processes are running' }
    @($found | ForEach-Object {
        [pscustomobject]@{
            id=$_.Id; name=$_.ProcessName; cpuSeconds=$_.TotalProcessorTime.TotalSeconds
            workingSetBytes=$_.WorkingSet64; privateCommitBytes=$_.PrivateMemorySize64
        }
    })
}
$begin = Snapshot
$processIds = @($begin.id | Sort-Object)
$beginCpu = ($begin | Measure-Object cpuSeconds -Sum).Sum
$previousCpu = $beginCpu
$previousElapsed = 0.0
$started = [DateTimeOffset]::Now
$timer = [Diagnostics.Stopwatch]::StartNew()
$samples = [Collections.Generic.List[object]]::new()
Write-Output "Measuring $Label for $DurationSeconds seconds; $($processIds.Count) processes, $LogicalProcessors logical CPUs."
while ($timer.Elapsed.TotalSeconds -lt $DurationSeconds) {
    $next = [Math]::Min($DurationSeconds, ($samples.Count+1)*$SampleSeconds)
    $delay = [int][Math]::Max(1, ($next-$timer.Elapsed.TotalSeconds)*1000)
    Start-Sleep -Milliseconds $delay
    $current = Snapshot
    $elapsed = $timer.Elapsed.TotalSeconds
    if (Compare-Object $processIds @($current.id | Sort-Object)) { throw 'Process set changed during measurement; rerun after stabilization' }
    $cpu = ($current | Measure-Object cpuSeconds -Sum).Sum
    $row = [pscustomobject]@{
        elapsedSeconds=$elapsed
        cpuPercent=100*($cpu-$previousCpu)/(($elapsed-$previousElapsed)*$LogicalProcessors)
        workingSetMiB=($current | Measure-Object workingSetBytes -Sum).Sum/1MB
        privateCommitMiB=($current | Measure-Object privateCommitBytes -Sum).Sum/1MB
        processes=$current
    }
    $samples.Add($row)
    $previousCpu=$cpu
    $previousElapsed=$elapsed
    if ($samples.Count % 10 -eq 0) { Write-Output ('{0}: {1:N0}s; CPU avg {2:N4}%; working set {3:N1} MiB; private commit {4:N1} MiB' -f $Label,$elapsed,(100*($cpu-$beginCpu)/($elapsed*$LogicalProcessors)),$row.workingSetMiB,$row.privateCommitMiB) }
}
$summary=[ordered]@{
    label=$Label; startedAt=$started.ToString('o'); durationSeconds=$elapsed
    logicalProcessors=$LogicalProcessors; processIds=$processIds; processNames=$ProcessNames
    cpuPercentAverage=100*($cpu-$beginCpu)/($elapsed*$LogicalProcessors)
    cpuPercentPeakSample=($samples | Measure-Object cpuPercent -Maximum).Maximum
    workingSetMiBAverage=($samples | Measure-Object workingSetMiB -Average).Average
    workingSetMiBMin=($samples | Measure-Object workingSetMiB -Minimum).Minimum
    workingSetMiBMax=($samples | Measure-Object workingSetMiB -Maximum).Maximum
    privateCommitMiBAverage=($samples | Measure-Object privateCommitMiB -Average).Average
    privateCommitMiBMin=($samples | Measure-Object privateCommitMiB -Minimum).Minimum
    privateCommitMiBMax=($samples | Measure-Object privateCommitMiB -Maximum).Maximum
    initialProcesses=$begin; finalProcesses=$current; samples=$samples
}
$outputFile = Join-Path $PSScriptRoot ($Label+'.json')
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputFile -Encoding utf8
$samples | Select-Object elapsedSeconds,cpuPercent,workingSetMiB,privateCommitMiB | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $PSScriptRoot ($Label+'.csv'))
$summary.Remove('samples')
$summary | ConvertTo-Json -Depth 5
