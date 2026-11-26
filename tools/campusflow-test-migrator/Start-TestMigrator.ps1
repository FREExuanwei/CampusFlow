#Requires -Version 7.4
[CmdletBinding(PositionalBinding = $false)]
param([switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$jarName = 'campusflow-migrator-v6-0.2.0-mysql-final.jar'
$jar = Join-Path $PSScriptRoot $jarName
$lib = Get-Item -LiteralPath (Join-Path $PSScriptRoot 'lib') -Force
$sums = Get-Item -LiteralPath (Join-Path $PSScriptRoot 'SHA256SUMS.txt') -Force
if (-not $lib.PSIsContainer -or $lib.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint) -or
    $sums.PSIsContainer -or $sums.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) {
    throw '发布目录或 SHA256SUMS.txt 不是普通本机文件。'
}

$files = @($jarName, 'Start-TestMigrator.ps1', 'README.md', 'campusflow-test-migrator.password.example')
foreach ($entry in Get-ChildItem -LiteralPath $lib.FullName -Force) {
    if ($entry.PSIsContainer -or $entry.Name -cnotmatch '^[a-z0-9.-]+[.]jar$') {
        throw '发布目录中的 lib 包含未批准的内容。'
    }
    $files += 'lib/' + $entry.Name
}

$checked = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($line in [IO.File]::ReadAllLines($sums.FullName, [Text.Encoding]::UTF8)) {
    if ($line -cnotmatch '^(?<digest>[A-F0-9]{64})  (?<name>[^ ]+)$') {
        throw 'SHA256SUMS.txt 格式无效。'
    }
    $digest = $Matches.digest
    $name = $Matches.name
    if ($name -cnotin $files -or -not $checked.Add($name)) {
        throw 'SHA256SUMS.txt 的路径或重复项无效。'
    }
    $file = Get-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Force
    if ($file.PSIsContainer -or $file.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint) -or
        (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -cne $digest) {
        throw '发布文件的 SHA-256 核对失败。'
    }
}
if ($checked.Count -ne $files.Count) {
    throw 'SHA256SUMS.txt 未覆盖全部发布文件。'
}

$actualFiles = @(Get-ChildItem -LiteralPath $PSScriptRoot -Force | Select-Object -ExpandProperty Name)
$expectedFiles = @($files | Where-Object { -not $_.StartsWith('lib/') }) + @('lib', 'SHA256SUMS.txt')
if (Compare-Object $expectedFiles $actualFiles -CaseSensitive) {
    throw '发布目录包含未批准的内容。'
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw '请将 JAVA_HOME 设置为 JDK 25 的安装目录。'
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw 'JAVA_HOME 未指向有效的 JDK 安装目录。'
}
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = $java
$info.WorkingDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$info.UseShellExecute = $false
$info.CreateNoWindow = $false
foreach ($name in 'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH') {
    [void]$info.Environment.Remove($name)
}
foreach ($name in @($info.Environment.Keys)) {
    if ($name.StartsWith('FLYWAY_', [StringComparison]::OrdinalIgnoreCase) -or
        $name.StartsWith('SPRING_', [StringComparison]::OrdinalIgnoreCase)) {
        [void]$info.Environment.Remove($name)
    }
}
$info.ArgumentList.Add('-jar')
$info.ArgumentList.Add($jar)
if ($CheckOnly) {
    $info.ArgumentList.Add('--check')
}
$process = [Diagnostics.Process]::Start($info)
try {
    $process.WaitForExit()
    exit $process.ExitCode
} finally {
    $process.Dispose()
}
