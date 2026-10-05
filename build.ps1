[CmdletBinding()]
param(
    [string]$Clang = 'x86_64-w64-mingw32-clang++.exe',
    [string]$AdlxInclude,
    [switch]$SkipEditor
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$engineSource = Join-Path $projectRoot 'engine'
$editorSource = Join-Path $projectRoot 'editor'
$applicationRoot = Join-Path $projectRoot 'app'
$engineDestination = Join-Path $applicationRoot 'engine'
$editorDestination = Join-Path $applicationRoot 'editor'
$workRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot '.work'))
$nativeBuild = Join-Path $workRoot 'source-build'
$editorWork = Join-Path $workRoot 'editor-build'
$adlxPath = if ($AdlxInclude) { [System.IO.Path]::GetFullPath($AdlxInclude) } else { $null }

if ($adlxPath -and -not (Test-Path -LiteralPath (Join-Path $adlxPath 'ADLX.h') -PathType Leaf)) {
    throw 'AdlxInclude должен указывать на папку с ADLX.h. См. engine/sensors-vendor/fetch-adlx.ps1.'
}

$clangCommand = Get-Command $Clang -CommandType Application -ErrorAction Stop
$clangPath = $clangCommand.Source
$target = (& $clangPath -dumpmachine | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $target -notmatch '^x86_64.*(mingw|windows-gnu|w64)') {
    throw "Нужен компилятор LLVM-MinGW для Windows x64; найден target '$target'. Передайте полный путь через -Clang."
}

$running = @(Get-Process -Name PurrLCD, PurrLCDEditor -ErrorAction SilentlyContinue)
if ($running.Count -gt 0) {
    throw 'Перед сборкой закройте редактор и завершите PurrLCD через меню значка в трее.'
}

$sources = @('app.cpp', 'scene.cpp', 'protocol.cpp', 'hid_display.cpp', 'sensors.cpp', 'privilege.cpp') |
    ForEach-Object { Join-Path $engineSource $_ }
foreach ($source in $sources) {
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Не найден исходник: $source" }
}

$pawnSource = Join-Path $engineSource 'sensors-vendor\pawnio'
foreach ($required in @('AMDFamily17.bin', 'COPYING.LGPL-2.1.txt', 'NOTICE.txt', 'PawnIO.Modules-0.2.11-source.zip')) {
    if (-not (Test-Path -LiteralPath (Join-Path $pawnSource $required) -PathType Leaf)) {
        throw "Не хватает файла поставки PawnIO: $required"
    }
}

New-Item -ItemType Directory -Path $nativeBuild, $engineDestination -Force | Out-Null
$nativeExe = Join-Path $nativeBuild 'PurrLCD.exe'
$compileArguments = @(
    '-std=c++17', '-O2', '-static', '-DUNICODE', '-D_UNICODE', '-DNOMINMAX',
    '-D_WIN32_WINNT=0x0A00',
    '-Wno-unknown-attributes', '-municode', '-mwindows'
) + $sources + @(
    '-lgdiplus', '-lole32', '-loleaut32', '-luuid', '-lshell32', '-ladvapi32',
    '-lpsapi', '-lsetupapi', '-lhid', '-luser32', '-lgdi32', '-o', $nativeExe
)

if ($adlxPath) { $compileArguments += @('-DPURRLCD_WITH_ADLX=1', "-I$adlxPath") }
Write-Host 'Сборка фонового движка C++…'
& $clangPath @compileArguments
if ($LASTEXITCODE -ne 0) { throw "Сборка движка завершилась с кодом $LASTEXITCODE" }

Write-Host 'Проверка протокола без обращения к экрану…'
$testExe = Join-Path $nativeBuild 'protocol_test.exe'
$testArguments = @('-std=c++17', '-O2', '-static',
    (Join-Path $engineSource 'protocol_test.cpp'), (Join-Path $engineSource 'protocol.cpp'), '-o', $testExe)
& $clangPath @testArguments
if ($LASTEXITCODE -ne 0) { throw 'Не удалось собрать проверки протокола.' }
& $testExe
if ($LASTEXITCODE -ne 0) { throw 'Проверки протокола не прошли.' }

if (-not $SkipEditor) {
    if (-not (Get-Command java -CommandType Application -ErrorAction SilentlyContinue)) {
        throw 'Для сборки редактора установите JDK 21 и добавьте java в PATH.'
    }
    $oldGradleHome = $env:GRADLE_USER_HOME
    $env:GRADLE_USER_HOME = Join-Path $editorWork 'gradle-home'
    Push-Location $editorSource
    try {
        Write-Host 'Сборка и проверка редактора Compose…'
        & (Join-Path $editorSource 'gradlew.bat') `
            --gradle-user-home $env:GRADLE_USER_HOME `
            --project-cache-dir (Join-Path $editorWork 'project-cache') `
            test createDistributable
        if ($LASTEXITCODE -ne 0) { throw "Сборка редактора завершилась с кодом $LASTEXITCODE" }
    }
    finally {
        Pop-Location
        if ($null -eq $oldGradleHome) { Remove-Item Env:\GRADLE_USER_HOME -ErrorAction SilentlyContinue }
        else { $env:GRADLE_USER_HOME = $oldGradleHome }
    }

    $editorImage = Join-Path $editorWork 'build\compose\binaries\main\app\PurrLCDEditor'
    if (-not (Test-Path -LiteralPath (Join-Path $editorImage 'PurrLCDEditor.exe') -PathType Leaf)) {
        throw "Не найден собранный редактор: $editorImage"
    }
    # Replace only this generated application directory. Persisted app/data is separate.
    $checkedEditor = [System.IO.Path]::GetFullPath($editorDestination)
    $expectedEditor = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'app\editor'))
    if (-not [string]::Equals($checkedEditor, $expectedEditor, [StringComparison]::OrdinalIgnoreCase) -or
        -not $checkedEditor.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Путь замены редактора оказался за пределами папки проекта.'
    }
    if (Test-Path -LiteralPath $checkedEditor) { Remove-Item -LiteralPath $checkedEditor -Recurse -Force }
    Copy-Item -LiteralPath $editorImage -Destination $checkedEditor -Recurse
}

Copy-Item -LiteralPath $nativeExe -Destination (Join-Path $engineDestination 'PurrLCD.exe') -Force
$vendorDestination = Join-Path $engineDestination 'sensors-vendor'
New-Item -ItemType Directory -Path $vendorDestination -Force | Out-Null
Copy-Item -LiteralPath $pawnSource -Destination $vendorDestination -Recurse -Force
Copy-Item -LiteralPath (Join-Path $engineSource 'sensors-vendor\NOTICE.txt') -Destination $vendorDestination -Force
Copy-Item -LiteralPath (Join-Path $engineSource 'third_party\JSON-LICENSE.txt') -Destination $engineDestination -Force
Copy-Item -LiteralPath (Join-Path $engineSource 'third_party\LLVM-MINGW-LICENSE.txt') -Destination $engineDestination -Force

Write-Host "Готово: $applicationRoot"
Write-Host 'Данные и настройки в app/data сохранены. Приложение и драйверы автоматически не запускаются.'
