#Requires -Version 7.4

param([switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$target = Join-Path $PSScriptRoot 'target\mysql-final-20261122'
$migrations = Join-Path $projectRoot 'src\main\resources\db\migration'
$approvedScripts = @(
    'V1__create_user_tables.sql', 'V2__seed_base_roles.sql', 'V3__create_activities.sql',
    'V4__activity_review_and_publication.sql', 'V5__create_enrollments.sql',
    'V6__notifications_and_operation_logs.sql'
)
$sourceHashes = @{}
foreach ($name in $approvedScripts) {
    $sourceHashes[$name] = (Get-FileHash -LiteralPath (Join-Path $migrations $name)).Hash
}
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = Join-Path ([Environment]::SystemDirectory) 'cmd.exe'
$info.WorkingDirectory = $projectRoot
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$info.StandardOutputEncoding = [Text.Encoding]::UTF8
$info.StandardErrorEncoding = [Text.Encoding]::UTF8
foreach ($name in 'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS',
        'CLASSPATH','MAVEN_BASEDIR','MVNW_REPOURL','MVNW_USERNAME','MVNW_PASSWORD') {
    [void]$info.Environment.Remove($name)
}
$info.Environment['MAVEN_SKIP_RC'] = '1'
$goal = 'package'
if ($TestsOnly) { $goal = 'test' }
$arguments = @(
    '/d', '/c', '.\mvnw.cmd', '-B', '-ntp',
    '-f', 'tools/campusflow-test-migrator/pom.xml',
    $goal
)
foreach ($argument in $arguments) { $info.ArgumentList.Add($argument) }
$process = [Diagnostics.Process]::Start($info)
try {
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    Write-Output $stdout.GetAwaiter().GetResult()
    Write-Output $stderr.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw "构建失败，退出码 $($process.ExitCode)。" }
} finally { $process.Dispose() }

if ($TestsOnly) {
    return
}
$jarName = 'campusflow-migrator-v6-0.2.0-mysql-final.jar'
$jar = Join-Path $target $jarName
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    $sqlFiles = @($archive.Entries | Where-Object FullName -match '[.]sql$')
    if ($sqlFiles.Count -ne $approvedScripts.Count) {
        throw 'JAR 中迁移资源的数量不符合预期。'
    }
    foreach ($name in $approvedScripts) {
        $entry = $archive.GetEntry("db/migration/$name")
        if ($null -eq $entry) {
            throw 'JAR 缺少已审核的迁移资源。'
        }
        $stream = $entry.Open()
        try {
            $packed = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))
        } finally { $stream.Dispose() }
        if ($packed -cne $sourceHashes[$name] -or
            (Get-FileHash -LiteralPath (Join-Path $migrations $name)).Hash -cne $sourceHashes[$name]) {
            throw '打包前后迁移资源的 SHA-256 不一致。'
        }
        Write-Output "$name $packed"
    }
} finally { $archive.Dispose() }

$libraries = @(Get-ChildItem -LiteralPath (Join-Path $target 'lib') -File -Filter '*.jar')
$locks = @{}
foreach ($record in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src\main\resources\runtime-lock.properties')) {
    $parts = $record.Split('=')
    $locks.Add($parts[0], $parts[1])
}
if ($libraries.Count -ne $locks.Count) {
    throw '运行依赖数量与已审核清单不一致。'
}
foreach ($library in $libraries) {
    if (-not $locks.ContainsKey($library.Name) -or
        (Get-FileHash -LiteralPath $library.FullName).Hash -cne $locks[$library.Name]) {
        throw '运行依赖与已审核 SHA-256 不一致。'
    }
}

$release = Join-Path $projectRoot 'artifacts\campusflow-migrator-v6-mysql-final-20261122'
$releaseLib = Join-Path $release 'lib'
foreach ($path in (Split-Path -Parent $release), $release, $releaseLib) {
    if (-not (Test-Path -LiteralPath $path)) {
        $null = New-Item -ItemType Directory -Path $path
    }
    $directory = Get-Item -LiteralPath $path -Force
    if (-not $directory.PSIsContainer -or $directory.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) {
        throw '发布目录含重解析点。'
    }
}
$support = @('Start-TestMigrator.ps1', 'README.md', 'campusflow-test-migrator.password.example')
$relativeFiles = @($jarName) + $support + @($libraries.Name | ForEach-Object { "lib/$_" })
foreach ($relative in $relativeFiles + 'SHA256SUMS.txt') {
    $existing = Get-Item -LiteralPath (Join-Path $release $relative) -Force -ErrorAction SilentlyContinue
    if ($existing -and ($existing.PSIsContainer -or $existing.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint))) {
        throw '发布目标不是普通本机文件。'
    }
}
Copy-Item -LiteralPath $jar -Destination $release -Force
foreach ($library in $libraries) {
    Copy-Item -LiteralPath $library.FullName -Destination $releaseLib -Force
}
foreach ($filename in $support) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $filename) -Destination $release -Force
}
$sums = foreach ($relative in $relativeFiles | Sort-Object) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $release $relative)).Hash
    "$hash  $relative"
}
[IO.File]::WriteAllLines((Join-Path $release 'SHA256SUMS.txt'), [string[]]$sums, [Text.UTF8Encoding]::new($false))
& (Join-Path $release 'Start-TestMigrator.ps1') -CheckOnly
if ($LASTEXITCODE -ne 0) {
    throw '发布包离线预检未通过。'
}

