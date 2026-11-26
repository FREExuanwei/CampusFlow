[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Start', 'Stop', 'Reset')]
    [string]$Action,
    [switch]$BackendOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtime = Join-Path $projectRoot '.campusflow-demo'
$frontend = Join-Path $projectRoot 'frontend'
$statePath = Join-Path $runtime 'processes.json'
$credentialsPath = Join-Path $runtime 'credentials.json'
$healthUrl = 'http://127.0.0.1:18087/api/v1/demo/health'
$frontendUrl = 'http://127.0.0.1:5173'

function Assert-PlainFile([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    if ($item -and ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint))) {
        throw "Invalid local demo file: $($item.Name)."
    }
}

function Initialize-RuntimeDirectory {
    $item = Get-Item -LiteralPath $runtime -Force -ErrorAction SilentlyContinue
    if ($item) {
        if (!$item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'The .campusflow-demo directory must be a real project-local directory.'
        }
    } else {
        $item = New-Item -ItemType Directory -Path $runtime
    }
    $acl = $item.GetAccessControl()
    if ($acl.AreAccessRulesProtected) { return }
    $acl.SetAccessRuleProtection($true, $false)
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $system = [Security.Principal.SecurityIdentifier]::new([Security.Principal.WellKnownSidType]::LocalSystemSid, $null)
    $inherit = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit
    foreach ($identity in @($currentUser, $system)) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', $inherit, 'None', 'Allow')
        $acl.SetAccessRule($rule)
    }
    $item.SetAccessControl($acl)
}

function Set-LocalText([string]$Path, [string]$Text) {
    Assert-PlainFile $Path
    $tempPath = Join-Path $runtime ([Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($tempPath, $Text, [Text.UTF8Encoding]::new($false))
        if ([IO.File]::Exists($Path)) {
            [IO.File]::Replace($tempPath, $Path, [NullString]::Value)
        } else {
            [IO.File]::Move($tempPath, $Path)
        }
    } finally {
        if ([IO.File]::Exists($tempPath)) { [IO.File]::Delete($tempPath) }
    }
}

function New-LocalSecret([int]$Length) {
    $bytes = New-Object byte[] $Length
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
        [Convert]::ToBase64String($bytes)
    } finally {
        $random.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Initialize-Credentials {
    Assert-PlainFile $credentialsPath
    $writeCredentials = ![IO.File]::Exists($credentialsPath)
    if ($writeCredentials) {
        $accounts = [pscustomobject]@{
            admin = [pscustomobject]@{ username = 'demo_admin'; password = New-LocalSecret 18 }
            student = [pscustomobject]@{ username = 'demo_student'; password = New-LocalSecret 18 }
            student2 = [pscustomobject]@{ username = 'demo_student2'; password = New-LocalSecret 18 }
        }
    } else {
        try {
            if ((Get-Item -LiteralPath $credentialsPath).Length -gt 8192) { throw 'Invalid file size.' }
            $credentials = Get-Content -LiteralPath $credentialsPath -Raw -Encoding UTF8 | ConvertFrom-Json
            $accounts = $credentials.accounts
            $writeCredentials = $null -ne $credentials.PSObject.Properties['jwtSigningKey']
        } catch {
            throw 'The local demo credential file is invalid.'
        }
    }
    $accountLines = @('CampusFlow local demo accounts', '')
    foreach ($role in @('admin', 'student', 'student2')) {
        $account = $accounts.$role
        if ($account.username -cne "demo_$role" -or $account.password -cnotmatch '^[A-Za-z0-9_+\-=/\.]{12,72}$') {
            throw 'The local demo credential file is invalid.'
        }
        $accountLines += ('{0,-15} {1}' -f $account.username, $account.password)
    }
    if ($writeCredentials) {
        Set-LocalText $credentialsPath ([pscustomobject]@{ accounts = $accounts } | ConvertTo-Json -Depth 4)
    }
    Set-LocalText (Join-Path $runtime 'accounts.txt') ($accountLines -join [Environment]::NewLine)
}

function Read-ProcessState {
    Assert-PlainFile $statePath
    if ([IO.File]::Exists($statePath)) {
        return Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    return [pscustomobject]@{ backend = $null; frontend = $null }
}

function Get-OwnedProcess($Record) {
    if (!$Record) { return $null }
    $process = Get-Process -Id ([int]$Record.id) -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$Record.startedTicks -and
            $process.Path -eq $Record.executable) {
        return $process
    }
    return $null
}

function Stop-OwnedProcess($Record) {
    $process = Get-OwnedProcess $Record
    if ($process) {
        Stop-Process -InputObject $process
        if (!$process.WaitForExit(5000)) { throw "Demo process $($process.Id) has not stopped." }
    }
}

function Test-TcpListener([int]$Port) {
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    $probe.ExclusiveAddressUse = $true
    try {
        $probe.Start()
        return $false
    } catch {
        if ($_.Exception.InnerException.SocketErrorCode -in @('AddressAlreadyInUse', 'AccessDenied')) { return $true }
        throw
    } finally {
        $probe.Stop()
    }
}

function Start-LocalProcess([string]$Executable, [string[]]$Arguments, [string]$Directory, [string]$Name) {
    $stdout = Join-Path $runtime "$Name.log"
    $stderr = Join-Path $runtime "$Name-error.log"
    Assert-PlainFile $stdout
    Assert-PlainFile $stderr
    $process = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $Directory `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    if ($process.HasExited) { throw "The $Name process exited. Check its local demo logs." }
    return [pscustomobject]@{ id = $process.Id; startedTicks = $process.StartTime.ToUniversalTime().Ticks;
        executable = $process.Path }
}

function Wait-LocalEndpoint([string]$Url, $ProcessRecord, [bool]$RequireDemoMarker, [int]$Seconds) {
    Add-Type -AssemblyName System.Net.Http
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(2)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        while ($timer.Elapsed.TotalSeconds -lt $Seconds -and (Get-OwnedProcess $ProcessRecord)) {
            try {
                $body = $client.GetStringAsync($Url).GetAwaiter().GetResult()
                if (!$RequireDemoMarker) { return }
                $health = $body | ConvertFrom-Json
                if ($health.success -and $health.data.status -eq 'UP' -and $health.data.authority -eq 'H2_DEMO') { return }
            } catch {
                # Poll while the server is coming online.
            }
            Start-Sleep -Milliseconds 450
        }
    } finally {
        $client.Dispose()
    }
    throw "The demo did not become ready at $Url. Check its logs in .campusflow-demo."
}

function Start-Demo($State) {
    $foreignSettings = Get-ChildItem Env: | Where-Object Name -Match `
        '^(SPRING[_.]|SERVER[_.]|MANAGEMENT[_.]|CAMPUSFLOW[_.]|HIBERNATE[_.]|HIKARICP[_.]|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)'
    if ($foreignSettings) { throw 'Demo startup rejected inherited application or JVM settings. Use a clean command window.' }

    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (!$javac) { throw 'JDK 25 is required.' }
    $java = Join-Path (Split-Path -Parent $javac.Source) 'java.exe'
    $jdkVersion = & $javac.Source --version
    if (!(Test-Path -LiteralPath $java -PathType Leaf) -or $jdkVersion -notmatch '^javac 25(?:\.|$)') {
        throw 'JDK 25 is required.'
    }

    if (!$BackendOnly) {
        $node = Get-Command node.exe -ErrorAction SilentlyContinue
        $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (!$node -or !$npm) { throw 'Node.js and npm are required.' }
        $nodeVersion = [version]((& $node.Source --version).TrimStart('v'))
        if (!(($nodeVersion.Major -eq 20 -and $nodeVersion -ge [version]'20.19') -or $nodeVersion -ge [version]'22.12')) {
            throw 'Node.js 20.19+ or 22.12+ is required to run Vite.'
        }
        $vite = Join-Path $frontend 'node_modules/vite/bin/vite.js'
        if (!(Test-Path -LiteralPath (Join-Path $frontend 'package-lock.json') -PathType Leaf)) {
            throw 'The frontend npm lock file is missing.'
        }
        if (!(Test-Path -LiteralPath $vite -PathType Leaf)) {
            Write-Host 'Installing frontend dependencies from the project lock file...'
            Push-Location -LiteralPath $frontend
            try {
                & $npm.Source ci --ignore-scripts --no-audit --no-fund
                if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
            } finally {
                Pop-Location
            }
        }
    }

    $ownedBackend = Get-OwnedProcess $State.backend
    $ownedFrontend = Get-OwnedProcess $State.frontend
    if ((!$ownedBackend -and (Test-TcpListener 18087)) -or
            (!$BackendOnly -and !$ownedFrontend -and (Test-TcpListener 5173))) {
        throw 'A demo port is already in use by another process. No server was started.'
    }
    Initialize-Credentials

    $startedBackend = $null
    $startedFrontend = $null
    try {
        if (!$ownedBackend) {
            Write-Host 'Building the H2 demo backend...'
            $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
            $mavenCommand = if ($maven) { $maven.Source } else { Join-Path $projectRoot 'mvnw.cmd' }
            $mavenArguments = @('-Pdemo', '-Dmaven.test.skip=true', 'package')
            Push-Location -LiteralPath $projectRoot
            try {
                & $mavenCommand @mavenArguments
                if ($LASTEXITCODE -ne 0) { throw 'Demo backend build failed.' }
            } finally {
                Pop-Location
            }
            $jar = Join-Path $projectRoot 'target/demo-build/campusflow-demo.jar'
            $startedBackend = Start-LocalProcess $java @('-jar', ('"{0}"' -f $jar), '--spring.profiles.active=demo') $projectRoot 'backend'
            $State.backend = $startedBackend
            Set-LocalText $statePath ($State | ConvertTo-Json -Depth 3)
        }
        Wait-LocalEndpoint $healthUrl $State.backend $true 60
        if (!$BackendOnly) {
            if (!$ownedFrontend) {
                Write-Host 'Starting the Vite frontend...'
                $startedFrontend = Start-LocalProcess $node.Source @(
                    ('"{0}"' -f $vite), '--host', '127.0.0.1', '--port', '5173', '--strictPort'
                ) $frontend 'frontend'
                $State.frontend = $startedFrontend
                Set-LocalText $statePath ($State | ConvertTo-Json -Depth 3)
            }
            Wait-LocalEndpoint $frontendUrl $State.frontend $false 30
        }
    } catch {
        try { Stop-OwnedProcess $startedFrontend } finally { Stop-OwnedProcess $startedBackend }
        throw
    }
    if (!$BackendOnly) { Write-Host "CampusFlow browser address: $frontendUrl" }
    Write-Host "H2 demo backend health check: $healthUrl"
    Write-Host 'Open-CampusFlow-Demo-Accounts.cmd opens the local demo login details.'
}

function Reset-Demo($State) {
    if ((Get-OwnedProcess $State.backend) -or (Get-OwnedProcess $State.frontend) -or (Test-TcpListener 18087)) {
        throw 'Stop the local demo before resetting it.'
    }
    if ([IO.File]::Exists($statePath)) { Remove-Item -LiteralPath $statePath }
    Write-Host 'The stopped in-memory H2 demo has no retained business data. Starting it again recreates the sample data.'
}

$lock = $null
try {
    if ($Action -ne 'Start' -and !(Test-Path -LiteralPath $runtime)) {
        Write-Host 'There is no local CampusFlow demo state.'
        exit 0
    }
    Initialize-RuntimeDirectory
    $lockPath = Join-Path $runtime 'control.lock'
    Assert-PlainFile $lockPath
    $lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $state = Read-ProcessState
    switch ($Action) {
        'Start' { Start-Demo $state }
        'Stop' {
            Stop-OwnedProcess $state.frontend
            Stop-OwnedProcess $state.backend
            if ([IO.File]::Exists($statePath)) { Remove-Item -LiteralPath $statePath }
            Write-Host 'CampusFlow demo processes stopped. The in-memory H2 demo data has been cleared.'
        }
        'Reset' { Reset-Demo $state }
    }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
} finally {
    if ($lock) { $lock.Dispose() }
}
