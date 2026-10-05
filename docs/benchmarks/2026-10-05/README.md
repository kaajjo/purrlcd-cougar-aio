# Background resource measurements — 2026-10-05

Two sequential 120-second measurements on the same PC, sampled every 2 seconds. PurrLCD's editor was closed; LCDEditor was manually minimized to the tray before its measured run.

## Results

| Metric | PurrLCD | COUGAR LCDEditor 1.0.14 |
| --- | ---: | ---: |
| Processes | 1 | 5 |
| Average CPU usage | 0.0399% | 1.1197% |
| Peak CPU usage over a 2-second sample | 0.0983% | 1.6475% |
| Average summed working set | 24.6 MiB | 713.6 MiB |
| Summed working set range | 24.5–27.1 MiB | 678.3–742.3 MiB |
| Average private commit | 74.6 MiB | 494.2 MiB |
| Private commit range | 74.5–77.4 MiB | 455.8–522.5 MiB |

PurrLCD used about **28 times less CPU** in these runs.

Working set includes resident shared pages. Adding working sets across LCDEditor's five processes can count the same pages more than once; 713.6 MiB is not a measurement of unique physical RAM. Private commit is a separate allocation counter, not resident RAM. Do not add these two memory figures together.

The main LCDEditor process alone averaged 214.1 MiB working set. The table includes its four child processes as well.

## Conditions and method

- Windows x64; Ryzen 7 5700X with 16 logical processors; Radeon RX 9070 XT; COUGAR Poseidon Vistek Pro ARGB 360, 720 × 720 LCD.
- PurrLCD: 16:11:16–16:13:16, UTC+03:00. Already running before collection, connected to the LCD with valid CPU/GPU temperature readings and a 1-second refresh interval. Connection and outgoing frame count were checked before and after collection.
- LCDEditor 1.0.14: 16:25:34–16:27:34, UTC+03:00. Running for several minutes before collection; the user confirmed manual minimization. PurrLCD was stopped. Physical LCD updates during this run were not independently confirmed.
- The process tree was checked before measurement. All five LCDEditor processes had the same executable name. Process IDs remained unchanged throughout each run.
- PowerShell collected `TotalProcessorTime`, `WorkingSet64` and `PrivateMemorySize64` via `Get-Process`. CPU usage is the change in total process CPU time divided by elapsed wall time and 16 logical processors, multiplied by 100. Memory results are arithmetic means of 60 samples. One MiB is 1,048,576 bytes.
- These are observations of each application's current configuration during normal desktop use, not an identical-workload or long-duration benchmark. Startup costs and the open PurrLCD editor are excluded.

An earlier exploratory LCDEditor run was excluded because its background state could not be verified.

## Data

- [Summary and per-process breakdown](comparison.json)
- PurrLCD: [full samples](purrlcd.json), [CSV](purrlcd.csv)
- LCDEditor in the tray: [full samples](cougar-tray.json), [CSV](cougar-tray.csv)
- [Collection script used for both runs](measure.ps1)

To repeat, run the applications one at a time in the same background states. Confirm the LCD is updating, check the process tree, then run the appropriate command from this directory:

```powershell
.\measure.ps1 -Label purrlcd-repeat -ProcessNames PurrLCD
.\measure.ps1 -Label cougar-repeat -ProcessNames 'COUGAR LCD Editor'
```

The script defaults to 120 seconds, 2-second sampling and 16 logical processors. Set `-LogicalProcessors` to the actual count on another PC. It writes JSON and CSV beside the script; use a new label to preserve the original results. It does not launch or stop either application.
